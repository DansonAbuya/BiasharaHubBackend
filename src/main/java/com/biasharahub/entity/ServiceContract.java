package com.biasharahub.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Contract for service bookings when payment_timing is AS_PER_CONTRACT.
 * Terms and payment schedule; signed by both customer and provider; payments tracked per contract.
 */
@Entity
@Table(name = "service_contracts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceContract {

    @Id
    @Column(name = "contract_id")
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID contractId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceOffering service;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id")
    private ServiceAppointment appointment;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String terms;

    /** JSON: e.g. [{"milestone":"On signing","amount":1000},{"milestone":"On completion","amount":2000}] */
    @Column(name = "payment_schedule", columnDefinition = "TEXT")
    private String paymentSchedule;

    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private String status = "DRAFT";

    @Column(name = "signed_by_customer_at")
    private Instant signedByCustomerAt;

    @Column(name = "signed_by_provider_at")
    private Instant signedByProviderAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
