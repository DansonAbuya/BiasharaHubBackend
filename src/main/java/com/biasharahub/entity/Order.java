package com.biasharahub.entity;

import com.biasharahub.config.EncryptedStringAttributeConverter;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @Column(name = "order_id")
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "order_number", nullable = false, unique = true)
    private String orderNumber;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "order_status", nullable = false)
    @Builder.Default
    private String orderStatus = "pending";

    /**
     * Preferred delivery mode selected at checkout.
     * Mirrors shipment deliveryMode once shipment is created.
     * SELLER_SELF, COURIER, RIDER_MARKETPLACE, CUSTOMER_PICKUP
     */
    @Column(name = "delivery_mode", nullable = false)
    @Builder.Default
    private String deliveryMode = "SELLER_SELF";

    /**
     * Shipping fee charged to the customer (in KES).
     * Included in totalAmount.
     */
    @Column(name = "shipping_fee", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal shippingFee = BigDecimal.ZERO;

    @Convert(converter = EncryptedStringAttributeConverter.class)
    @Column(name = "shipping_address", columnDefinition = "TEXT")
    private String shippingAddress;

    @Column(name = "ordered_at")
    private Instant orderedAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "payout_released_at")
    private Instant payoutReleasedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Payment> payments = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Shipment> shipments = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (orderedAt == null) orderedAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
