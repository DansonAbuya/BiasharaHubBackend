package com.biasharahub.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Escrow for virtual service bookings: funds held until customer confirms service or disputes.
 * On confirm → release to provider wallet; on dispute → refund to customer (M-Pesa B2C).
 */
@Entity
@Table(name = "service_booking_escrow")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceBookingEscrow {

    @Id
    @Column(name = "escrow_id")
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID escrowId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = false, unique = true)
    private ServiceAppointment appointment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_payment_id", nullable = false)
    private ServiceBookingPayment bookingPayment;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "HELD";

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
