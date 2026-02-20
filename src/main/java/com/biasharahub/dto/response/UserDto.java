package com.biasharahub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private UUID id;
    private String name;
    private String email;
    /** Optional phone for WhatsApp and notifications (e.g. +254712345678). */
    private String phone;
    private String role;
    private String businessId;
    private String businessName;
    private String verificationStatus;
    private java.time.Instant verifiedAt;
    private UUID verifiedByUserId;
    private String verificationNotes;
    private String sellerTier;
    /** Optional: tier the owner is applying for (tier1/tier2/tier3). Set at onboarding. */
    private String applyingForTier;
    /** Trust & Safety: strike count (3+=suspended, 5+=banned). */
    private Integer strikeCount;
    /** Trust & Safety: active, suspended, banned. */
    private String accountStatus;

    // --- Service provider verification (separate from product seller) ---
    private String serviceProviderStatus;
    private String serviceProviderNotes;
    private UUID serviceProviderCategoryId;
    private String serviceDeliveryType;
    private java.time.Instant serviceProviderVerifiedAt;
    private UUID serviceProviderVerifiedByUserId;
}
