package com.biasharahub.controller;

import com.biasharahub.dto.request.ChangePasswordRequest;
import com.biasharahub.dto.request.ForgotPasswordRequest;
import com.biasharahub.dto.request.LoginRequest;
import com.biasharahub.dto.request.RefreshTokenRequest;
import com.biasharahub.dto.request.RegisterRequest;
import com.biasharahub.dto.request.ResetPasswordRequest;
import com.biasharahub.dto.request.VerifyCodeRequest;
import com.biasharahub.dto.response.LoginResponse;
import com.biasharahub.security.AuthenticatedUser;
import com.biasharahub.service.AuthService;
import com.biasharahub.service.OAuth2TwoFactorService;
import com.biasharahub.service.Pending2FAStore;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final OAuth2TwoFactorService oauth2TwoFactorService;
    private final Pending2FAStore pending2FAStore;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            return ResponseEntity.ok(authService.registerCustomer(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        Optional<LoginResponse> result = authService.login(request);
        if (result.isPresent()) {
            return ResponseEntity.ok(result.get());
        }
        return ResponseEntity.status(401).body(Map.of("error", "Invalid email or password"));
    }

    /**
     * Stateless logout endpoint.
     * For JWT-based auth we don't keep server-side sessions; the frontend is responsible
     * for removing access/refresh tokens from storage. This endpoint exists so the
     * frontend can always get a quick 200/204 response and update its UI.
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify-code")
    public ResponseEntity<?> verifyCode(@Valid @RequestBody VerifyCodeRequest request) {
        Optional<LoginResponse> result = authService.verifyCode(request);
        if (result.isPresent()) {
            return ResponseEntity.ok(result.get());
        }
        return ResponseEntity.status(401).body(Map.of("error", "Invalid or expired code"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        Optional<LoginResponse> result = authService.refresh(request);
        if (result.isPresent()) {
            return ResponseEntity.ok(result.get());
        }
        return ResponseEntity.status(401).body(Map.of("error", "Invalid or expired refresh token"));
    }

    @PostMapping("/2fa/enable")
    public ResponseEntity<?> enableTwoFactor(@AuthenticationPrincipal AuthenticatedUser user) {
        try {
            authService.enableTwoFactor(user.userId(), user.role());
            return ResponseEntity.ok(Map.of("message", "Two-factor authentication enabled"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/2fa/disable")
    public ResponseEntity<?> disableTwoFactor(@AuthenticationPrincipal AuthenticatedUser user) {
        try {
            authService.disableTwoFactor(user.userId(), user.role());
            return ResponseEntity.ok(Map.of("message", "Two-factor authentication disabled"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@AuthenticationPrincipal AuthenticatedUser user,
                                           @Valid @RequestBody ChangePasswordRequest request) {
        if (authService.changePassword(user.userId(), request)) {
            return ResponseEntity.ok(Map.of("message", "Password updated"));
        }
        return ResponseEntity.status(400).body(Map.of("error", "Current password is incorrect"));
    }

    /**
     * Forgot password: request a reset link by email. Available to all users (any role).
     * Always returns success to avoid email enumeration.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.requestPasswordReset(request.getEmail().trim().toLowerCase());
        return ResponseEntity.ok(Map.of("message", "If an account exists with that email, you will receive a password reset link."));
    }

    /**
     * Reset password using the token from the email link. Available to all users.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        if (authService.resetPassword(request.getToken().trim(), request.getNewPassword())) {
            return ResponseEntity.ok(Map.of("message", "Password has been reset. You can now sign in."));
        }
        return ResponseEntity.status(400).body(Map.of("error", "Invalid or expired reset link. Please request a new one."));
    }

    /**
     * Start OAuth2 2FA: validate state token and redirect user to Google sign-in.
     * Returns 503 if Google OAuth2 is not configured (use email 2FA instead).
     */
    @GetMapping("/2fa/oauth/authorize")
    public ResponseEntity<?> oauth2Authorize(@RequestParam String stateToken) {
        if (pending2FAStore.peek(stateToken).isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        Optional<String> urlOpt = oauth2TwoFactorService.buildAuthorizationUrl(stateToken);
        if (urlOpt.isEmpty()) {
            return ResponseEntity.status(503).build();
        }
        return ResponseEntity.status(302).location(URI.create(urlOpt.get())).build();
    }

    /**
     * OAuth2 callback: exchange code for token, verify user, issue our JWTs, redirect to frontend with tokens in hash.
     * No auth required.
     */
    @GetMapping("/oauth2/callback")
    public ResponseEntity<?> oauth2Callback(@RequestParam String code, @RequestParam String state) {
        Optional<LoginResponse> result = oauth2TwoFactorService.handleCallback(code, state);
        String frontendUri = oauth2TwoFactorService.getFrontendRedirectUri();
        if (result.isEmpty()) {
            String redirect = UriComponentsBuilder.fromUriString(frontendUri).queryParam("error", "invalid").build().toUriString();
            return ResponseEntity.status(302).location(URI.create(redirect)).build();
        }
        LoginResponse login = result.get();
        String redirect = frontendUri + "#access_token=" + URLEncoder.encode(login.getToken(), StandardCharsets.UTF_8)
                + "&refresh_token=" + URLEncoder.encode(login.getRefreshToken(), StandardCharsets.UTF_8);
        return ResponseEntity.status(302).location(URI.create(redirect)).build();
    }
}
