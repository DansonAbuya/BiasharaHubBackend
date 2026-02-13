package com.biasharahub.controller;

import com.biasharahub.entity.User;
import com.biasharahub.repository.UserRepository;
import com.biasharahub.security.AuthenticatedUser;
import com.biasharahub.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M-Pesa and bank reconciliation: list pending payments, match by receipt number.
 */
@RestController
@RequestMapping("/reconciliation")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER', 'STAFF', 'SUPER_ADMIN', 'ASSISTANT_ADMIN')")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;
    private final UserRepository userRepository;

    @GetMapping("/pending-payments")
    public ResponseEntity<List<Map<String, Object>>> listPendingPayments(
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null) return ResponseEntity.status(401).build();
        User u = userRepository.findById(user.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        UUID businessId = u.getBusinessId();
        if (businessId == null) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(reconciliationService.listPendingPayments(user, businessId));
    }

    /**
     * Match M-Pesa receipt to a pending payment and confirm.
     * Body: { "paymentId": "uuid", "receiptNumber": "ABC123XYZ" }
     */
    @PostMapping("/match-receipt")
    public ResponseEntity<?> matchByReceipt(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody Map<String, String> body) {
        if (user == null) return ResponseEntity.status(401).build();
        String receiptNumber = body != null ? body.get("receiptNumber") : null;
        String paymentIdStr = body != null ? body.get("paymentId") : null;
        if (receiptNumber == null || receiptNumber.isBlank() || paymentIdStr == null || paymentIdStr.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "receiptNumber and paymentId are required"));
        }
        UUID paymentId;
        try {
            paymentId = UUID.fromString(paymentIdStr);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid paymentId"));
        }
        var payment = reconciliationService.matchByReceipt(user, receiptNumber.trim(), paymentId);
        if (payment == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Payment not found or already confirmed"));
        }
        return ResponseEntity.ok(Map.of("status", "completed", "paymentId", payment.getPaymentId()));
    }
}
