package com.biasharahub.controller;

import com.biasharahub.dto.request.CreatePurchaseOrderRequest;
import com.biasharahub.dto.response.PurchaseOrderDto;
import com.biasharahub.security.AuthenticatedUser;
import com.biasharahub.service.PurchaseOrderService;
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
@RequestMapping("/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    /**
     * Seller center: list purchase orders for the business (owner/staff).
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public ResponseEntity<List<PurchaseOrderDto>> listForBusiness(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(purchaseOrderService.listForBusiness(user));
    }

    /**
     * Seller center: create a purchase order for a supplier. Prices are optional; supplier quotes actual prices on dispatch.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public ResponseEntity<?> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreatePurchaseOrderRequest request) {
        try {
            return ResponseEntity.ok(purchaseOrderService.create(user, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Seller center: get a single purchase order with items.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public ResponseEntity<?> get(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id) {
        try {
            return ResponseEntity.ok(purchaseOrderService.getForBusiness(user, id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Supplier portal: list purchase orders for the logged-in supplier (what the seller requested).
     */
    @GetMapping("/my")
    @PreAuthorize("hasRole('SUPPLIER')")
    public ResponseEntity<?> listForSupplier(@AuthenticationPrincipal AuthenticatedUser user) {
        try {
            return ResponseEntity.ok(purchaseOrderService.listForSupplier(user));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}

