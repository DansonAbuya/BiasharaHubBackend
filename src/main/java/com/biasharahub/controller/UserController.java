package com.biasharahub.controller;

import com.biasharahub.dto.request.AddStaffRequest;
import com.biasharahub.dto.response.UserDto;
import com.biasharahub.security.AuthenticatedUser;
import com.biasharahub.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * User management. Only owners can add staff and list staff.
 * Staff receive a temporary password by email and must enable 2FA after first login.
 */
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/staff")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<?> addStaff(@AuthenticationPrincipal AuthenticatedUser user,
                                      @Valid @RequestBody AddStaffRequest request) {
        try {
            UserDto staff = userService.addStaff(user, request);
            return ResponseEntity.ok(staff);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/staff")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<List<UserDto>> listStaff(@AuthenticationPrincipal AuthenticatedUser user) {
        try {
            return ResponseEntity.ok(userService.listStaff(user));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(403).build();
        }
    }
}
