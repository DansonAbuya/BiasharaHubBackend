package com.biasharahub.entity;

import com.biasharahub.config.EncryptedStringAttributeConverter;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @Column(name = "payment_id")
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID paymentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Convert(converter = EncryptedStringAttributeConverter.class)
    @Column(name = "transaction_id", columnDefinition = "TEXT")
    private String transactionId;

    @Column(name = "payment_status", nullable = false)
    @Builder.Default
    private String paymentStatus = "pending";

    @Column(name = "payment_method")
    @Builder.Default
    private String paymentMethod = "M-Pesa";

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
