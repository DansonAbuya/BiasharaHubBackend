package com.biasharahub.controller;

import com.biasharahub.dto.request.AddSupplierDeliveryItemRequest;
import com.biasharahub.dto.request.ConfirmReceiptRequest;
import com.biasharahub.dto.request.CreateSupplierDeliveryRequest;
import com.biasharahub.dto.request.ConvertDeliveryItemRequest;
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
public class SupplierDeliveryController {

    private final SupplierDeliveryService supplierDeliveryService;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF', 'SUPER_ADMIN', 'ASSISTANT_ADMIN')")
    public ResponseEntity<?> list(@AuthenticationPrincipal AuthenticatedUser user) {
        try {
            return ResponseEntity.ok(supplierDeliveryService.listMyBusinessDeliveries(user));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Failed to list deliveries"));
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF', 'SUPER_ADMIN', 'ASSISTANT_ADMIN')")
    public ResponseEntity<?> get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        try {
            return ResponseEntity.ok(supplierDeliveryService.get(user, id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF', 'SUPER_ADMIN', 'ASSISTANT_ADMIN')")
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
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF', 'SUPER_ADMIN', 'ASSISTANT_ADMIN')")
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

    /**
     * Convert part of a received delivery item into separate sale units / product.
     */
    @PostMapping("/{deliveryId}/items/{itemId}/convert")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public ResponseEntity<?> convertItem(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID deliveryId,
            @PathVariable UUID itemId,
            @Valid @RequestBody ConvertDeliveryItemRequest request) {
        try {
            return ResponseEntity.ok(supplierDeliveryService.convertItem(user, deliveryId, itemId, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Confirm supplier delivery receipt and move quantities directly to stock.
     * Optional body allows overriding received quantities per item.
     */
    @PatchMapping("/{id}/confirm-receipt")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public ResponseEntity<?> confirmReceipt(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody(required = false) ConfirmReceiptRequest request) {
        try {
            return ResponseEntity.ok(supplierDeliveryService.confirmReceipt(user, id, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Supplier: submit a dispatch to the seller.
     */
    @PostMapping("/dispatch")
    @PreAuthorize("hasRole('SUPPLIER')")
    public ResponseEntity<?> submitDispatch(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody com.biasharahub.dto.request.SubmitDispatchRequest request) {
        try {
            return ResponseEntity.ok(supplierDeliveryService.submitDispatch(user, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Supplier: list their own dispatches to this business (for reconciliation).
     */
    @GetMapping("/my-dispatches")
    @PreAuthorize("hasRole('SUPPLIER')")
    public ResponseEntity<?> listMyDispatches(@AuthenticationPrincipal AuthenticatedUser user) {
        try {
            return ResponseEntity.ok(supplierDeliveryService.listMyDispatchesAsSupplier(user));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}

