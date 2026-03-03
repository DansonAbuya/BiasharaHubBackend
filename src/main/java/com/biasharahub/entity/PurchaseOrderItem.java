package com.biasharahub.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "purchase_order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrderItem {

    @Id
    @Column(name = "item_id")
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID itemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "unit_of_measure", length = 32)
    private String unitOfMeasure; // e.g. kg, piece, box

    @Column(name = "requested_quantity", nullable = false)
    private Integer requestedQuantity;

    @Column(name = "expected_unit_cost", precision = 15, scale = 2)
    private BigDecimal expectedUnitCost; // optional; supplier quotes actual cost on dispatch

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}

