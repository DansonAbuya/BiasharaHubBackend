package com.biasharahub.controller;

import com.biasharahub.dto.request.AddStaffRequest;
import com.biasharahub.dto.response.UserDto;
import com.biasharahub.entity.User;
import com.biasharahub.repository.UserRepository;
import com.biasharahub.security.AuthenticatedUser;
import com.biasharahub.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * User management. Only owners can add staff and list staff.
 * Staff and owners can list customers (e.g. to place an order on their behalf).
 */
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;

    public UserController(UserService userService, UserRepository userRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
    }

    /** Current user (id, name, email, role) for frontend to show role-specific UI (e.g. "Order for customer"). */
    @GetMapping("/me")
    public ResponseEntity<UserDto> getCurrentUser(@AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null) return ResponseEntity.status(401).build();
        return userRepository.findById(user.userId())
                .map(u -> ResponseEntity.ok(UserDto.builder()
                        .id(u.getUserId())
                        .name(u.getName())
                        .email(u.getEmail())
                        .role(u.getRole())
                        .businessId(u.getBusinessId() != null ? u.getBusinessId().toString() : null)
                        .businessName(u.getBusinessName())
                        .verificationStatus(u.getVerificationStatus())
                        .sellerTier(u.getSellerTier())
                        .applyingForTier(u.getApplyingForTier())
                        .build()))
                .orElse(ResponseEntity.status(401).build());
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

    /** List customers (for staff/owner when placing an order on behalf of a customer). */
    @GetMapping("/customers")
    @PreAuthorize("hasRole('OWNER') or hasRole('STAFF')")
    public ResponseEntity<List<UserDto>> listCustomers() {
        List<User> customers = userRepository.findByRoleIgnoreCaseOrderByNameAsc("customer");
        List<UserDto> dtos = customers.stream()
                .map(u -> UserDto.builder()
                        .id(u.getUserId())
                        .name(u.getName())
                        .email(u.getEmail())
                        .role(u.getRole())
                        .businessId(u.getBusinessId() != null ? u.getBusinessId().toString() : null)
                        .businessName(u.getBusinessName())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }
}
