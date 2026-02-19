package com.biasharahub.controller;

import com.biasharahub.dto.request.PaymentInitiateRequest;
import com.biasharahub.dto.response.PaymentInitiateResponse;
import com.biasharahub.entity.Order;
import com.biasharahub.entity.Payment;
import com.biasharahub.repository.OrderRepository;
import com.biasharahub.repository.PaymentRepository;
import com.biasharahub.repository.UserRepository;
import com.biasharahub.security.AuthenticatedUser;
import com.biasharahub.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Payment endpoints for order processing. Integrates with M-PESA STK Push and updates wallet ledger.
 */
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class PaymentController {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final OrderEventPublisher orderEventPublisher;
    private final MpesaClient mpesaClient;
    private final TenantWalletService tenantWalletService;
    private final WhatsAppNotificationService whatsAppNotificationService;
    private final InAppNotificationService inAppNotificationService;

    /** Initiate payment for an order via M-PESA STK Push. */
    @PostMapping("/{orderId}/payments/initiate")
    @Transactional
    public ResponseEntity<PaymentInitiateResponse> initiatePayment(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID orderId,
            @Valid @RequestBody PaymentInitiateRequest request) {
        if (user == null) {
            return ResponseEntity.status(401).<PaymentInitiateResponse>build();
        }

        // Find order belonging to current user
        java.util.Optional<Order> optionalOrder = orderRepository.findById(orderId)
                .filter(order -> order.getUser().getUserId().equals(user.userId()));
        if (optionalOrder.isEmpty()) {
            return ResponseEntity.status(404).<PaymentInitiateResponse>build();
        }

        Order order = optionalOrder.get();
        if (!"pending".equals(order.getOrderStatus())) {
            return ResponseEntity.badRequest().<PaymentInitiateResponse>build();
        }

        Payment payment = paymentRepository.findByOrderAndPaymentStatus(order, "pending")
                .orElse(null);
        if (payment == null) {
            return ResponseEntity.status(404).<PaymentInitiateResponse>build();
        }
        if ("Cash".equalsIgnoreCase(payment.getPaymentMethod())) {
            return ResponseEntity.badRequest()
                    .body(PaymentInitiateResponse.builder()
                            .message("This order is pay-by-cash. The seller will confirm payment when you pay in cash.")
                            .build());
        }

        String accountRef = order.getOrderNumber();
        String desc = "BiasharaHub order payment";
        String checkoutRequestId = mpesaClient.initiateStkPush(request.getPhoneNumber(),
                order.getTotalAmount(), accountRef, desc);
        payment.setTransactionId(checkoutRequestId);
        paymentRepository.save(payment);
        PaymentInitiateResponse body = PaymentInitiateResponse.builder()
                .paymentId(payment.getPaymentId())
                .checkoutRequestId(checkoutRequestId)
                .status("pending")
                .message("M-PESA STK push initiated. Complete the payment on your phone to confirm.")
                .build();

        // Notify WhatsApp chatbot (if enabled) that a payment request was sent
        try {
            whatsAppNotificationService.notifyPaymentRequested(order, payment);
        } catch (Exception e) {
            // Do not fail the payment initiation just because notifications failed
        }

        // In-app notification for payment request
        try {
            inAppNotificationService.notifyPaymentRequested(order, payment);
        } catch (Exception e) {
            // ignore failures
        }

        return ResponseEntity.ok(body);
    }

    /**
     * Manual confirm is only for staff/owner/admin reconciliation. Payment status must normally
     * be set by M-Pesa callback (STK callback); customers cannot confirm their own payment.
     */
    @PatchMapping("/{orderId}/payments/{paymentId}/confirm")
    @Transactional
    public ResponseEntity<?> confirmPayment(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID orderId,
            @PathVariable UUID paymentId) {
        if (user == null) return ResponseEntity.status(401).build();
        String role = user.role() != null ? user.role().toLowerCase() : "";
        boolean canConfirm = "owner".equals(role) || "staff".equals(role)
                || "super_admin".equals(role) || "assistant_admin".equals(role);
        if (!canConfirm) {
            return ResponseEntity.status(403).body(
                    java.util.Map.of("error", "Payment is confirmed only by M-Pesa. Complete payment on your phone and status will update automatically."));
        }
        return paymentRepository.findById(paymentId)
                .filter(p -> p.getOrder().getOrderId().equals(orderId))
                .map(payment -> {
                    payment.setPaymentStatus("completed");
                    paymentRepository.save(payment);
                    Order order = payment.getOrder();
                    if (order != null && "pending".equalsIgnoreCase(order.getOrderStatus())) {
                        order.setOrderStatus("confirmed");
                        orderRepository.save(order);
                    }
                    tenantWalletService.recordIncomingPaymentForCurrentTenant(
                            payment.getAmount(), orderId.toString(), paymentId.toString());
                    orderEventPublisher.paymentCompleted(orderId, paymentId);
                    return ResponseEntity.ok(java.util.Map.of("status", "completed", "paymentId", paymentId));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Update payment method for an order (M-Pesa, Cash, or Bank).
     * Customers may update their own pending order's payment method (e.g. switch between Cash and M-Pesa).
     * Staff/owner/admin may update for any order.
     */
    @PatchMapping("/{orderId}/payments/{paymentId}/method")
    @Transactional
    public ResponseEntity<?> updatePaymentMethod(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID orderId,
            @PathVariable UUID paymentId,
            @RequestBody java.util.Map<String, String> body) {
        if (user == null) return ResponseEntity.status(401).build();
        String role = user.role() != null ? user.role().toLowerCase() : "";
        boolean staffOrAdmin = "owner".equals(role) || "staff".equals(role)
                || "super_admin".equals(role) || "assistant_admin".equals(role);
        String method = body != null ? body.get("paymentMethod") : null;
        if (method == null || method.isBlank()) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", "paymentMethod is required (M-Pesa, Cash, or Bank)"));
        }
        String m = method.trim().toLowerCase();
        String normalized = "bank".equals(m) ? "Bank" : "cash".equals(m) ? "Cash" : "M-Pesa";
        return paymentRepository.findById(paymentId)
                .filter(p -> p.getOrder().getOrderId().equals(orderId))
                .map(payment -> {
                    Order order = payment.getOrder();
                    boolean isCustomer = "customer".equals(role);
                    if (isCustomer && !staffOrAdmin) {
                        if (!order.getUser().getUserId().equals(user.userId())) {
                            return ResponseEntity.status(403).body(
                                    java.util.Map.of("error", "You can only update payment method for your own order."));
                        }
                        if (!"pending".equalsIgnoreCase(order.getOrderStatus())) {
                            return ResponseEntity.status(400).body(
                                    java.util.Map.of("error", "Order is no longer pending."));
                        }
                        if (!"pending".equalsIgnoreCase(payment.getPaymentStatus())) {
                            return ResponseEntity.status(400).body(
                                    java.util.Map.of("error", "Payment is no longer pending."));
                        }
                    } else if (!staffOrAdmin) {
                        return ResponseEntity.status(403).body(java.util.Map.of("error", "Not allowed to update payment method."));
                    }
                    payment.setPaymentMethod(normalized);
                    paymentRepository.save(payment);
                    return ResponseEntity.ok(java.util.Map.of("status", "updated", "paymentMethod", normalized));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
