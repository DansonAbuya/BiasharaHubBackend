package com.biasharahub.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Line item within a supplier delivery.
 */
@Entity
@Table(name = "supplier_delivery_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplierDeliveryItem {

    @Id
    @Column(name = "item_id")
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID itemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_id", nullable = false)
    private SupplierDelivery delivery;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(nullable = false)
    private Integer quantity;

    /** Seller-confirmed quantity actually received. Null = assume same as quantity. */
    @Column(name = "received_quantity")
    private Integer receivedQuantity;

    @Column(name = "unit_cost", precision = 15, scale = 2)
    private BigDecimal unitCost;

    /** Unit of measure for quantity (e.g. kg, g, L, piece). Visible to seller for pricing and subdivision. */
    @Column(name = "unit_of_measure", length = 32)
    private String unitOfMeasure;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}

