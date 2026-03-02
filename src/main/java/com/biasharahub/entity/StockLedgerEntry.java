package com.biasharahub.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable audit trail for stock changes (tenant-scoped).
 * Captures who changed stock, why (type), and links to supplier deliveries or orders when relevant.
 */
@Entity
@Table(name = "stock_ledger_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockLedgerEntry {

    @Id
    @Column(name = "entry_id")
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID entryId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "change_qty", nullable = false)
    private Integer changeQty;

    @Column(name = "previous_qty")
    private Integer previousQty;

    @Column(name = "new_qty")
    private Integer newQty;

    @Column(name = "entry_type", nullable = false, length = 32)
    private String entryType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_id")
    private SupplierDelivery delivery;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by_user_id")
    private User performedBy;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}

