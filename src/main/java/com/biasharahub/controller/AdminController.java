package com.biasharahub.controller;

import com.biasharahub.dto.request.AddUserRequest;
import com.biasharahub.dto.response.UserDto;
import com.biasharahub.security.AuthenticatedUser;
import com.biasharahub.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Platform admin endpoints. Only super_admin can add owners.
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final UserService userService;

    public AdminController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/owners")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> addOwner(@AuthenticationPrincipal AuthenticatedUser admin,
                                      @Valid @RequestBody AddUserRequest request) {
        try {
            UserDto owner = userService.addOwner(request);
            return ResponseEntity.ok(owner);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
