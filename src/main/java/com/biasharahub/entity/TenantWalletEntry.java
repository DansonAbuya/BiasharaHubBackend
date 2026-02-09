package com.biasharahub.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Ledger entry for a tenant's wallet (public schema).
 * Tracks credits (incoming customer payments) and debits (payouts, fees).
 */
@Entity
@Table(name = "tenant_wallet_entries", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantWalletEntry {

    @Id
    @Column(name = "entry_id")
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID entryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    /**
     * Entry type: CREDIT (money into wallet), DEBIT (money out), COMMISSION (platform fee).
     */
    @Column(name = "entry_type", nullable = false, length = 32)
    private String entryType;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    /**
     * Optional commission amount associated with this entry (for reporting).
     */
    @Column(name = "commission_amount", precision = 15, scale = 2)
    private BigDecimal commissionAmount;

    @Column(name = "reference_id")
    private String referenceId; // e.g. orderId, paymentId, payoutId

    @Column(name = "description")
    private String description;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}

