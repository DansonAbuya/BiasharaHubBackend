package com.biasharahub.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Trust & Safety: dispute ticket for an order. Customer or system creates; seller responds; admin resolves.
 * Strike applied when resolved in customer's favor (late_shipping=1, wrong_item=2, fraud=3).
 */
@Entity
@Table(name = "disputes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dispute {

    @Id
    @Column(name = "dispute_id")
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID disputeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_user_id", nullable = false)
    private User reporterUser;

    /** late_shipping, wrong_item, fraud, other */
    @Column(name = "dispute_type", nullable = false, length = 32)
    private String disputeType;

    /** open, seller_responded, under_review, resolved */
    @Column(nullable = false, length = 32)
    @Builder.Default
    private String status = "open";

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "delivery_proof_url", length = 1024)
    private String deliveryProofUrl;

    @Column(name = "seller_response", columnDefinition = "TEXT")
    private String sellerResponse;

    @Column(name = "seller_responded_at")
    private Instant sellerRespondedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by_user_id")
    private User resolvedByUser;

    /** customer_favor, seller_favor, refund, partial */
    @Column(length = 32)
    private String resolution;

    /** When resolution = customer_favor: late_shipping (1 strike), wrong_item (2), fraud (3) */
    @Column(name = "strike_reason", length = 32)
    private String strikeReason;

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
