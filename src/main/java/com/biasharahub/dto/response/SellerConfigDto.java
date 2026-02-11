package com.biasharahub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/**
 * Admin-facing view of a seller's commercial configuration:
 * - Pricing plan (marketing/commercial model)
 * - White-label branding settings
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerConfigDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID userId;
    private String email;
    private String name;
    private String role;

    private UUID businessId;
    private String businessName;
    private String sellerTier;
    private String verificationStatus;

    private String pricingPlan;

    /** Growth plan feature flags (only meaningful when pricingPlan is "growth"). */
    private Boolean growthInventoryAutomation;
    private Boolean growthWhatsappEnabled;
    private Boolean growthAnalyticsEnabled;
    private Boolean growthDeliveryIntegrations;

    private Boolean brandingEnabled;
    private String brandingName;
    private String brandingLogoUrl;
    private String brandingPrimaryColor;
    private String brandingSecondaryColor;
}

