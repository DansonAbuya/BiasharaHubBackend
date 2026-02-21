package com.biasharahub.controller;

import com.biasharahub.dto.request.AddAssistantAdminRequest;
import com.biasharahub.dto.request.AddBusinessOwnerRequest;
import com.biasharahub.dto.request.AddOwnerRequest;
import com.biasharahub.dto.request.AddServiceProviderRequest;
import com.biasharahub.dto.response.UserDto;
import com.biasharahub.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Platform admin endpoints. super_admin and assistant_admin can add owners (onboard businesses).
 * Only super_admin can add assistant admins.
 * Owners receive a temporary password by email and can enable/disable 2FA.
 * Assistant admins have 2FA always on and cannot disable it.
 *
 * When adding an owner: send X-Tenant-ID (tenant UUID) so the owner is created in that tenant's schema
 * and the seller's payout details (payoutMethod, payoutDestination) are stored on that tenant for auto-payout on order delivery.
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final UserService userService;

    @Value("${app.oauth2.backend-base-url:http://localhost:5050/api}")
    private String apiBaseUrl;

    public AdminController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Returns URLs for API documentation (Swagger UI and OpenAPI spec).
     * Admin screen can use these to open Swagger in a new tab or fetch the spec to render docs.
     * Only SUPER_ADMIN and ASSISTANT_ADMIN can access Swagger and this endpoint.
     */
    @GetMapping("/api-docs-info")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ASSISTANT_ADMIN')")
    public ResponseEntity<Map<String, String>> getApiDocsInfo() {
        String base = apiBaseUrl.endsWith("/") ? apiBaseUrl.substring(0, apiBaseUrl.length() - 1) : apiBaseUrl;
        return ResponseEntity.ok(Map.of(
                "swaggerUiUrl", base + "/swagger-ui.html",
                "openApiSpecUrl", base + "/v3/api-docs"
        ));
    }

    @PostMapping("/owners")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ASSISTANT_ADMIN')")
    public ResponseEntity<?> addOwner(@Valid @RequestBody AddOwnerRequest request) {
        try {
            UserDto owner = userService.addOwner(request);
            return ResponseEntity.ok(owner);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/service-providers")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ASSISTANT_ADMIN')")
    public ResponseEntity<?> addServiceProvider(@Valid @RequestBody AddServiceProviderRequest request) {
        try {
            UserDto owner = userService.addServiceProvider(request);
            return ResponseEntity.ok(owner);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Onboard a business owner who can sell products, offer services, or both.
     * They receive a temporary password by email with instructions for their selected business types.
     */
    @PostMapping("/business-owners")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ASSISTANT_ADMIN')")
    public ResponseEntity<?> addBusinessOwner(@Valid @RequestBody AddBusinessOwnerRequest request) {
        try {
            UserDto owner = userService.addBusinessOwner(request);
            return ResponseEntity.ok(owner);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/assistant-admins")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> addAssistantAdmin(@Valid @RequestBody AddAssistantAdminRequest request) {
        try {
            UserDto assistantAdmin = userService.addAssistantAdmin(request);
            return ResponseEntity.ok(assistantAdmin);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
