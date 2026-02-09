package com.biasharahub.entity;

import com.biasharahub.config.EncryptedStringAttributeConverter;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Payout to a tenant from their wallet (public schema).
 * Supports M-Pesa payouts and bank transfers; sensitive destination details are encrypted.
 */
@Entity
@Table(name = "tenant_payouts", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantPayout {

    @Id
    @Column(name = "payout_id")
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID payoutId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    /**
     * Payout method: MPESA or BANK_TRANSFER.
     */
    @Column(name = "method", nullable = false, length = 32)
    private String method;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    /**
     * Encrypted destination details:
     *  - For MPESA: phone number (MSISDN)
     *  - For BANK_TRANSFER: bank name + account/IBAN
     */
    @Convert(converter = EncryptedStringAttributeConverter.class)
    @Column(name = "destination_details", nullable = false, length = 1024)
    private String destinationDetails;

    /**
     * Payout status: PENDING, PROCESSING, COMPLETED, FAILED.
     */
    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}

