package com.biasharahub.controller;

import com.biasharahub.dto.request.AddSupplierDeliveryItemRequest;
import com.biasharahub.dto.request.CreateSupplierDeliveryRequest;
import com.biasharahub.dto.response.SupplierDeliveryDto;
import com.biasharahub.security.AuthenticatedUser;
import com.biasharahub.service.SupplierDeliveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/supplier-deliveries")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER', 'STAFF', 'SUPER_ADMIN', 'ASSISTANT_ADMIN')")
public class SupplierDeliveryController {

    private final SupplierDeliveryService supplierDeliveryService;

    @GetMapping
    public ResponseEntity<List<SupplierDeliveryDto>> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(supplierDeliveryService.listMyBusinessDeliveries(user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        try {
            return ResponseEntity.ok(supplierDeliveryService.get(user, id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateSupplierDeliveryRequest request) {
        try {
            return ResponseEntity.ok(supplierDeliveryService.create(user, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/items")
    public ResponseEntity<?> addItem(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody AddSupplierDeliveryItemRequest request) {
        try {
            return ResponseEntity.ok(supplierDeliveryService.addItem(user, id, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/start-processing")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<?> startProcessing(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        try {
            return ResponseEntity.ok(supplierDeliveryService.startProcessing(user, id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/move-to-stock")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<?> moveToStock(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        try {
            return ResponseEntity.ok(supplierDeliveryService.moveToStock(user, id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}

