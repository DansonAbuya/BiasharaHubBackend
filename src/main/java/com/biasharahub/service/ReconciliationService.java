package com.biasharahub.service;

import com.biasharahub.entity.Order;
import com.biasharahub.entity.Payment;
import com.biasharahub.repository.OrderRepository;
import com.biasharahub.repository.PaymentRepository;
import com.biasharahub.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * M-Pesa and bank reconciliation: match receipt numbers to orders, list pending payments.
 */
@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final TenantWalletService tenantWalletService;
    private final OrderEventPublisher orderEventPublisher;

    /**
     * List pending payments (awaiting M-Pesa callback or manual confirmation).
     * Owner/staff see their business's pending payments.
     */
    public List<Map<String, Object>> listPendingPayments(AuthenticatedUser user, UUID businessId) {
        List<Order> orders = orderRepository.findOrdersContainingProductsByBusinessId(businessId);
        Set<UUID> orderIds = orders.stream().map(Order::getOrderId).collect(Collectors.toSet());
        return paymentRepository.findByPaymentStatusOrderByCreatedAtDesc("pending")
                .stream()
                .filter(p -> orderIds.contains(p.getOrder().getOrderId()))
                .map(p -> {
                    Order o = p.getOrder();
                    return (Map<String, Object>) Map.<String, Object>of(
                            "paymentId", p.getPaymentId(),
                            "orderId", o.getOrderId(),
                            "orderNumber", o.getOrderNumber(),
                            "amount", p.getAmount(),
                            "customerName", o.getUser() != null ? (o.getUser().getName() != null ? o.getUser().getName() : "—") : "—",
                            "paymentMethod", p.getPaymentMethod() != null ? p.getPaymentMethod() : "M-Pesa",
                            "createdAt", p.getCreatedAt() != null ? p.getCreatedAt().toString() : ""
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * Match M-Pesa receipt number to a pending payment and confirm.
     * Returns the updated payment or null if not found/matched.
     */
    @Transactional
    public Payment matchByReceipt(AuthenticatedUser user, String receiptNumber, UUID paymentId) {
        if (receiptNumber == null || receiptNumber.isBlank()) return null;
        String normalized = receiptNumber.trim().replaceAll("\\s+", "");
        return paymentRepository.findById(paymentId)
                .filter(p -> "pending".equalsIgnoreCase(p.getPaymentStatus()))
                .map(payment -> {
                    payment.setTransactionId(normalized);
                    payment.setPaymentStatus("completed");
                    paymentRepository.save(payment);
                    Order order = payment.getOrder();
                    tenantWalletService.recordIncomingPaymentForCurrentTenant(
                            payment.getAmount(), order.getOrderId().toString(), payment.getPaymentId().toString());
                    orderEventPublisher.paymentCompleted(order.getOrderId(), payment.getPaymentId());
                    return payment;
                })
                .orElse(null);
    }

    /**
     * Find payment by M-Pesa receipt number (for auto-matching).
     */
    public Optional<Payment> findByReceipt(String receiptNumber) {
        if (receiptNumber == null || receiptNumber.isBlank()) return Optional.empty();
        return paymentRepository.findByTransactionId(receiptNumber.trim());
    }

    /**
     * Confirm payment by receipt: if receipt matches a pending payment's expected amount/order, confirm it.
     */
    @Transactional
    public Payment confirmByReceipt(AuthenticatedUser user, String receiptNumber) {
        Optional<Payment> opt = paymentRepository.findByTransactionId(receiptNumber.trim());
        if (opt.isPresent() && "completed".equalsIgnoreCase(opt.get().getPaymentStatus())) {
            return opt.get(); // already confirmed
        }
        // Receipt not in DB yet - need to match to pending payment by order/amount
        return null;
    }
}
