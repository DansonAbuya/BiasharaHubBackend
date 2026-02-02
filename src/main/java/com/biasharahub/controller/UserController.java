package com.biasharahub.controller;

import com.biasharahub.dto.request.AddUserRequest;
import com.biasharahub.dto.response.UserDto;
import com.biasharahub.security.AuthenticatedUser;
import com.biasharahub.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * User management. Owners can add staff.
 */
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/staff")
    public ResponseEntity<?> addStaff(@AuthenticationPrincipal AuthenticatedUser user,
                                      @Valid @RequestBody AddUserRequest request) {
        if (user == null) return ResponseEntity.status(401).build();
        try {
            UserDto staff = userService.addStaff(user, request);
            return ResponseEntity.ok(staff);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/staff")
    public ResponseEntity<List<UserDto>> listStaff(@AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null) return ResponseEntity.status(401).build();
        try {
            return ResponseEntity.ok(userService.listStaff(user));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(403).build();
        }
    }
}
