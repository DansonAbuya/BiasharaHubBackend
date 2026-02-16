package com.biasharahub.repository;

import com.biasharahub.entity.Order;
import com.biasharahub.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByOrder(Order order);

    java.util.Optional<Payment> findByOrderAndPaymentStatus(Order order, String status);

    /** Find payment by M-Pesa receipt or transaction ID (for reconciliation). */
    java.util.Optional<Payment> findByTransactionId(String transactionId);

    java.util.List<Payment> findByPaymentStatusOrderByCreatedAtDesc(String status);
}
