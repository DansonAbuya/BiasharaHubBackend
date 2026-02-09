package com.biasharahub.entity;

import com.biasharahub.config.EncryptedStringAttributeConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @Column(name = "user_id")
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID userId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Convert(converter = EncryptedStringAttributeConverter.class)
    @Column(name = "name", columnDefinition = "TEXT")
    private String name;

    @Column(nullable = false)
    @Builder.Default
    private String role = "customer";

    @Column(name = "two_factor_enabled")
    @Builder.Default
    private Boolean twoFactorEnabled = false;

    @Column(name = "business_id")
    private UUID businessId;

    @Column(name = "business_name")
    private String businessName;

    /**
     * Seller tier: tier1 (basic), tier2 (verified), tier3 (premium/trusted).
     * Only meaningful for owners; staff inherit the owner's tier via businessId.
     */
    @Column(name = "seller_tier", length = 32)
    @Builder.Default
    private String sellerTier = "tier1";

    /** Owner verification: pending, verified, rejected */
    @Column(name = "verification_status", length = 32)
    @Builder.Default
    private String verificationStatus = "pending";

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "verified_by_user_id")
    private UUID verifiedByUserId;

    @Column(name = "verification_notes", columnDefinition = "TEXT")
    private String verificationNotes;

    /** Optional: tier the owner is applying for (tier1/tier2/tier3). Set at onboarding; used to show which documents they must submit. */
    @Column(name = "applying_for_tier", length = 32)
    private String applyingForTier;

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
