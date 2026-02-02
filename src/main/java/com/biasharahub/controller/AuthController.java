package com.biasharahub.controller;

import com.biasharahub.dto.request.LoginRequest;
import com.biasharahub.dto.request.RegisterRequest;
import com.biasharahub.dto.request.VerifyCodeRequest;
import com.biasharahub.dto.response.LoginResponse;
import com.biasharahub.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            return ResponseEntity.ok(authService.registerCustomer(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(401).build());
    }

    @PostMapping("/verify-code")
    public ResponseEntity<LoginResponse> verifyCode(@Valid @RequestBody VerifyCodeRequest request) {
        return authService.verifyCode(request)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(401).build());
    }
}
