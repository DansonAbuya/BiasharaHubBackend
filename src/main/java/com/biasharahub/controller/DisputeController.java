package com.biasharahub.controller;

import com.biasharahub.dto.request.CreateDisputeRequest;
import com.biasharahub.dto.response.DisputeDto;
import com.biasharahub.entity.Dispute;
import com.biasharahub.entity.Order;
import com.biasharahub.repository.OrderRepository;
import com.biasharahub.security.AuthenticatedUser;
import com.biasharahub.service.DisputeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Trust & Safety: dispute workflow. Customer creates; seller responds; admin resolves (with optional strike).
 */
@RestController
@RequestMapping("/disputes")
@RequiredArgsConstructor
public class DisputeController {

    private final DisputeService disputeService;
    private final OrderRepository orderRepository;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> createDispute(@Valid @RequestBody CreateDisputeRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null) return ResponseEntity.status(401).build();
        Order order = orderRepository.findById(request.getOrderId()).orElse(null);
        if (order == null) return ResponseEntity.notFound().build();
        if (!order.getUser().getUserId().equals(user.userId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Only the customer who placed the order may open a dispute"));
        }
        try {
            Dispute dispute = disputeService.createDispute(
                    request.getOrderId(),
                    user.userId(),
                    request.getDisputeType(),
                    request.getDescription(),
                    request.getDeliveryProofUrl());
            return ResponseEntity.status(HttpStatus.CREATED).body(disputeService.toDto(dispute));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/order/{orderId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<DisputeDto>> listDisputesForOrder(@PathVariable UUID orderId, @AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null) return ResponseEntity.status(401).build();
        List<DisputeDto> list = disputeService.findByOrder(orderId).stream()
                .map(disputeService::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @PostMapping("/{id}/respond")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
    public ResponseEntity<?> sellerRespond(@PathVariable UUID id, @RequestBody Map<String, String> body, @AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null) return ResponseEntity.status(401).build();
        String response = body != null ? body.get("response") : null;
        if (response == null || response.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "response is required"));
        }
        try {
            Dispute dispute = disputeService.sellerRespond(id, user.userId(), response.trim());
            return ResponseEntity.ok(disputeService.toDto(dispute));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ASSISTANT_ADMIN')")
    public ResponseEntity<?> resolveDispute(@PathVariable UUID id, @RequestBody Map<String, String> body, @AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null) return ResponseEntity.status(401).build();
        String resolution = body != null ? body.get("resolution") : null;
        if (resolution == null || resolution.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "resolution is required (customer_favor, seller_favor, refund, partial)"));
        }
        String strikeReason = body != null ? body.get("strikeReason") : null;
        try {
            Dispute dispute = disputeService.resolveDispute(id, resolution.trim(), strikeReason, user.userId());
            return ResponseEntity.ok(disputeService.toDto(dispute));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ASSISTANT_ADMIN')")
    public ResponseEntity<List<DisputeDto>> listDisputes(@RequestParam(defaultValue = "open") String status) {
        List<DisputeDto> list = disputeService.findAllByStatus(status).stream()
                .map(disputeService::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getDispute(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null) return ResponseEntity.status(401).build();
        return disputeService.findById(id)
                .map(d -> ResponseEntity.ok(disputeService.toDto(d)))
                .orElse(ResponseEntity.notFound().build());
    }
}
