package com.biasharahub.service;

import com.biasharahub.dto.request.ChangePasswordRequest;
import com.biasharahub.dto.request.LoginRequest;
import com.biasharahub.dto.request.RefreshTokenRequest;
import com.biasharahub.dto.request.RegisterRequest;
import com.biasharahub.dto.request.VerifyCodeRequest;
import com.biasharahub.dto.response.LoginResponse;
import com.biasharahub.dto.response.UserDto;
import com.biasharahub.entity.PasswordResetToken;
import com.biasharahub.entity.User;
import com.biasharahub.entity.VerificationCode;
import com.biasharahub.repository.PasswordResetTokenRepository;
import com.biasharahub.repository.UserRepository;
import com.biasharahub.repository.VerificationCodeRepository;
import com.biasharahub.security.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final VerificationCodeRepository verificationCodeRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final MailService mailService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Pending2FAStore pending2FAStore;
    private final OAuth2TwoFactorService oauth2TwoFactorService;
    private final VerificationCodeService verificationCodeService;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    /**
     * Customer self-registration.
     * Sends ONE email containing both welcome text and a verification code.
     * After the first successful verification, future logins use email+password only (no further codes).
     */
    @Transactional
    public LoginResponse registerCustomer(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail().toLowerCase())) {
            throw new IllegalArgumentException("Email already registered");
        }
        User user = User.builder()
                .email(request.getEmail().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .role("customer")
                .twoFactorEnabled(true)  // used as "needs initial verification" flag for customers
                .build();
        user = userRepository.save(user);
        verificationCodeService.createAndSendWelcomeVerification(user);
        return LoginResponse.builder()
                .requiresTwoFactor(true)
                .user(toUserDto(user))
                .build();
    }

    @Transactional
    public Optional<LoginResponse> login(LoginRequest request) {
        return userRepository.findByEmail(request.getEmail().toLowerCase())
                .filter(user -> passwordEncoder.matches(request.getPassword(), user.getPasswordHash()))
                .map(user -> {
                    if (Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
                        verificationCodeService.createAndSend2FACode(user);
                        return LoginResponse.builder()
                                .requiresTwoFactor(true)
                                .user(toUserDto(user))
                                .build();
                    }
                    String token = jwtService.generateToken(user.getUserId(), user.getEmail(), user.getRole());
                    String refreshToken = jwtService.generateRefreshToken(user.getUserId(), user.getEmail(), user.getRole());
                    return LoginResponse.builder()
                            .token(token)
                            .refreshToken(refreshToken)
                            .user(toUserDto(user))
                            .requiresTwoFactor(false)
                            .build();
                });
    }

    @Transactional
    public Optional<LoginResponse> verifyCode(VerifyCodeRequest request) {
        String codeStr = request.getCodeNormalized();
        if (codeStr == null || codeStr.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByEmail(request.getEmail().toLowerCase())
                .flatMap(user -> verificationCodeRepository
                        .findByUserAndVerificationCodeAndExpiresAtAfter(
                                user, codeStr, Instant.now())
                        .map(code -> {
                            // One-time verification for customers and staff: after first successful verify, no more codes
                            if ("customer".equalsIgnoreCase(user.getRole()) || "staff".equalsIgnoreCase(user.getRole()) || "assistant_admin".equalsIgnoreCase(user.getRole())) {
                                user.setTwoFactorEnabled(false);
                                userRepository.save(user);
                            }
                            verificationCodeRepository.delete(code);
                            String token = jwtService.generateToken(user.getUserId(), user.getEmail(), user.getRole());
                            String refreshToken = jwtService.generateRefreshToken(user.getUserId(), user.getEmail(), user.getRole());
                            return LoginResponse.builder()
                                    .token(token)
                                    .refreshToken(refreshToken)
                                    .user(toUserDto(user))
                                    .requiresTwoFactor(false)
                                    .build();
                        }));
    }

    @Transactional(readOnly = true)
    public Optional<LoginResponse> refresh(RefreshTokenRequest request) {
        try {
            Claims claims = jwtService.parseToken(request.getRefreshToken());
            if (!jwtService.isRefreshToken(claims)) {
                return Optional.empty();
            }
            UUID userId = UUID.fromString(claims.getSubject());
            String email = claims.get("email", String.class);
            String role = claims.get("role", String.class);
            return userRepository.findById(userId)
                    .map(user -> {
                        String token = jwtService.generateToken(user.getUserId(), user.getEmail(), user.getRole());
                        String refreshToken = jwtService.generateRefreshToken(user.getUserId(), user.getEmail(), user.getRole());
                        return LoginResponse.builder()
                                .token(token)
                                .refreshToken(refreshToken)
                                .user(toUserDto(user))
                                .requiresTwoFactor(false)
                                .build();
                    });
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private UserDto toUserDto(User user) {
        return UserDto.builder()
                .id(user.getUserId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .businessId(user.getBusinessId() != null ? user.getBusinessId().toString() : null)
                .businessName(user.getBusinessName())
                .verificationStatus(user.getVerificationStatus())
                .sellerTier(user.getSellerTier())
                .applyingForTier(user.getApplyingForTier())
                .build();
    }

    /**
     * Enable 2FA for the current user. Customers, staff, and assistant_admin already have 2FA always on
     * and cannot toggle it; only owner and super_admin can enable/disable 2FA.
     */
    @Transactional
    public void enableTwoFactor(UUID userId, String role) {
        if (is2FAMandatory(role)) {
            throw new IllegalArgumentException("2FA is always on for your role and cannot be changed");
        }
        userRepository.findById(userId).ifPresent(user -> {
            user.setTwoFactorEnabled(true);
            userRepository.save(user);
        });
    }

    /**
     * Disable 2FA for the current user. Only owner and super_admin may disable 2FA.
     * Customers, staff, and assistant_admin have 2FA always on and cannot disable it.
     */
    @Transactional
    public void disableTwoFactor(UUID userId, String role) {
        if (is2FAMandatory(role)) {
            throw new IllegalArgumentException("2FA cannot be disabled for your role");
        }
        userRepository.findById(userId).ifPresent(user -> {
            user.setTwoFactorEnabled(false);
            verificationCodeRepository.deleteByUser(user);
            userRepository.save(user);
        });
    }

    private static boolean is2FAMandatory(String role) {
        if (role == null) return true;
        String r = role.toLowerCase();
        return "customer".equals(r) || "staff".equals(r) || "assistant_admin".equals(r);
    }

    /**
     * Change password for the current user (e.g. owner/staff changing temporary password).
     */
    @Transactional
    public boolean changePassword(UUID userId, ChangePasswordRequest request) {
        return userRepository.findById(userId)
                .filter(user -> passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash()))
                .map(user -> {
                    user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
                    userRepository.save(user);
                    return true;
                })
                .orElse(false);
    }

    /**
     * Request password reset for any user (all roles). Sends email with reset link. Always returns success to avoid email enumeration.
     */
    @Transactional
    public void requestPasswordReset(String email) {
        userRepository.findByEmail(email.toLowerCase()).ifPresent(user -> {
            passwordResetTokenRepository.deleteByUser(user);
            String token = UUID.randomUUID().toString().replace("-", "");
            Instant expiresAt = Instant.now().plusSeconds(3600); // 1 hour
            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .user(user)
                    .tokenHash(sha256Hex(token))
                    .expiresAt(expiresAt)
                    .build();
            passwordResetTokenRepository.save(resetToken);
            String resetLink = frontendUrl + "/reset-password?token=" + token;
            mailService.sendPasswordReset(user.getEmail(), user.getName() != null ? user.getName() : "User", resetLink);
        });
    }

    /**
     * Reset password using token from email. Available to all users.
     */
    @Transactional
    public boolean resetPassword(String token, String newPassword) {
        String tokenHash = sha256Hex(token);
        Optional<PasswordResetToken> resetTokenOpt = passwordResetTokenRepository.findByTokenHashAndExpiresAtAfter(tokenHash, Instant.now())
                .or(() -> passwordResetTokenRepository.findByTokenAndExpiresAtAfter(token, Instant.now()));
        return resetTokenOpt
                .map(resetToken -> {
                    User user = resetToken.getUser();
                    user.setPasswordHash(passwordEncoder.encode(newPassword));
                    userRepository.save(user);
                    passwordResetTokenRepository.delete(resetToken);
                    return true;
                })
                .orElse(false);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
