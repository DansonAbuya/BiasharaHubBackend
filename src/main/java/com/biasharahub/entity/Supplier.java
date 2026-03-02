package com.biasharahub.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Supplier for a business (tenant-scoped).
 */
@Entity
@Table(name = "suppliers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Supplier {

    @Id
    @Column(name = "supplier_id")
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID supplierId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 50)
    private String phone;

    @Column(length = 255)
    private String email;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}

