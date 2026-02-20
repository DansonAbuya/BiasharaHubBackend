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

    /**
     * Optional phone number for WhatsApp/SMS (E.164 or national format).
     * Used for order/payment/shipment notifications when WhatsApp is enabled.
     */
    @Column(name = "phone", length = 50)
    private String phone;

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
     * Marketing pricing plan for this seller: e.g. basic, growth, enterprise.
     * Admin-controlled; used to enable features and billing models.
     */
    @Column(name = "pricing_plan", length = 50)
    @Builder.Default
    private String pricingPlan = "starter";

    /**
     * Whether this seller has white-label branding enabled (custom brand instead of platform default).
     */
    @Column(name = "branding_enabled")
    @Builder.Default
    private Boolean brandingEnabled = false;

    /** Optional white-label brand name for this seller's storefront. */
    @Column(name = "branding_name")
    private String brandingName;

    /** Optional logo URL (e.g. R2 URL) for white-label storefront. */
    @Column(name = "branding_logo_url", columnDefinition = "TEXT")
    private String brandingLogoUrl;

    /** Optional primary theme color (CSS color string, e.g. #0f172a or oklch()). */
    @Column(name = "branding_primary_color", length = 32)
    private String brandingPrimaryColor;

    /** Optional secondary/accent color. */
    @Column(name = "branding_secondary_color", length = 32)
    private String brandingSecondaryColor;

    // --- Growth plan feature flags (only active when pricing_plan = 'growth') ---

    @Column(name = "growth_inventory_automation")
    @Builder.Default
    private Boolean growthInventoryAutomation = false;

    @Column(name = "growth_whatsapp_enabled")
    @Builder.Default
    private Boolean growthWhatsappEnabled = false;

    @Column(name = "growth_analytics_enabled")
    @Builder.Default
    private Boolean growthAnalyticsEnabled = false;

    @Column(name = "growth_delivery_integrations")
    @Builder.Default
    private Boolean growthDeliveryIntegrations = false;

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

    // --- Service provider verification (separate from product seller verification) ---

    /** Service provider verification: pending, verified, rejected. Enables listing services. */
    @Column(name = "service_provider_status", length = 32)
    @Builder.Default
    private String serviceProviderStatus = "pending";

    @Column(name = "service_provider_notes", columnDefinition = "TEXT")
    private String serviceProviderNotes;

    /** Primary service category approved for this provider (e.g. Consulting, Repair). */
    @Column(name = "service_provider_category_id")
    private UUID serviceProviderCategoryId;

    /** How they offer services: ONLINE, PHYSICAL, or BOTH. */
    @Column(name = "service_delivery_type", length = 32)
    private String serviceDeliveryType;

    @Column(name = "service_provider_verified_at")
    private Instant serviceProviderVerifiedAt;

    @Column(name = "service_provider_verified_by_user_id")
    private UUID serviceProviderVerifiedByUserId;

    /** Trust & Safety: total strike count (late_shipping=1, wrong_item=2, fraud=3 per incident). 3+=suspended, 5+=banned. */
    @Column(name = "strike_count")
    @Builder.Default
    private Integer strikeCount = 0;

    /** Trust & Safety: active, suspended, banned. */
    @Column(name = "account_status", length = 32)
    @Builder.Default
    private String accountStatus = "active";

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @Column(name = "banned_at")
    private Instant bannedAt;

    /** Verification checklist: phone verified (e.g. OTP or admin). */
    @Column(name = "phone_verified_at")
    private Instant phoneVerifiedAt;

    @Column(name = "mpesa_validated_at")
    private Instant mpesaValidatedAt;

    @Column(name = "business_location_verified_at")
    private Instant businessLocationVerifiedAt;

    @Column(name = "terms_accepted_at")
    private Instant termsAcceptedAt;

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
