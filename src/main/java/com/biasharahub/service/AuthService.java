package com.biasharahub.service;

import com.biasharahub.dto.request.LoginRequest;
import com.biasharahub.dto.request.RegisterRequest;
import com.biasharahub.dto.request.VerifyCodeRequest;
import com.biasharahub.dto.response.LoginResponse;
import com.biasharahub.dto.response.UserDto;
import com.biasharahub.entity.User;
import com.biasharahub.entity.VerificationCode;
import com.biasharahub.repository.UserRepository;
import com.biasharahub.repository.VerificationCodeRepository;
import com.biasharahub.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final VerificationCodeRepository verificationCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

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
                .twoFactorEnabled(false)
                .build();
        user = userRepository.save(user);
        String token = jwtService.generateToken(user.getUserId(), user.getEmail(), user.getRole());
        return LoginResponse.builder()
                .token(token)
                .user(toUserDto(user))
                .requiresTwoFactor(false)
                .build();
    }

    @Transactional(readOnly = true)
    public Optional<LoginResponse> login(LoginRequest request) {
        return userRepository.findByEmail(request.getEmail().toLowerCase())
                .filter(user -> passwordEncoder.matches(request.getPassword(), user.getPasswordHash()))
                .map(user -> {
                    if (Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
                        return LoginResponse.builder()
                                .requiresTwoFactor(true)
                                .user(toUserDto(user))
                                .build();
                    }
                    String token = jwtService.generateToken(user.getUserId(), user.getEmail(), user.getRole());
                    return LoginResponse.builder()
                            .token(token)
                            .user(toUserDto(user))
                            .requiresTwoFactor(false)
                            .build();
                });
    }

    @Transactional
    public Optional<LoginResponse> verifyCode(VerifyCodeRequest request) {
        return userRepository.findByEmail(request.getEmail().toLowerCase())
                .flatMap(user -> verificationCodeRepository
                        .findByUserAndVerificationCodeAndExpiresAtAfter(
                                user, request.getCode(), Instant.now())
                        .map(code -> {
                            verificationCodeRepository.delete(code);
                            String token = jwtService.generateToken(user.getUserId(), user.getEmail(), user.getRole());
                            return LoginResponse.builder()
                                    .token(token)
                                    .user(toUserDto(user))
                                    .requiresTwoFactor(false)
                                    .build();
                        }));
    }

    private UserDto toUserDto(User user) {
        return UserDto.builder()
                .id(user.getUserId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }
}
