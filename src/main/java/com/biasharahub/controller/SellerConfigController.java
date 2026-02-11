package com.biasharahub.controller;

import com.biasharahub.dto.response.SellerConfigDto;
import com.biasharahub.entity.User;
import com.biasharahub.repository.UserRepository;
import com.biasharahub.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admin configuration for seller pricing models and white-label branding.
 *
 * - Only platform admins (SUPER_ADMIN / ASSISTANT_ADMIN) can change pricing/branding for sellers.
 * - Sellers can read their own configuration for display in dashboards and storefronts.
 */
@RestController
@RequestMapping
@RequiredArgsConstructor
public class SellerConfigController {

    private final UserRepository userRepository;

    private static boolean isOwner(User u) {
        return "owner".equalsIgnoreCase(u.getRole());
    }

    private static SellerConfigDto toDto(User u) {
        return SellerConfigDto.builder()
                .userId(u.getUserId())
                .email(u.getEmail())
                .name(u.getName())
                .role(u.getRole())
                .businessId(u.getBusinessId())
                .businessName(u.getBusinessName())
                .sellerTier(u.getSellerTier())
                .verificationStatus(u.getVerificationStatus())
                .pricingPlan(u.getPricingPlan())
                .growthInventoryAutomation(u.getGrowthInventoryAutomation())
                .growthWhatsappEnabled(u.getGrowthWhatsappEnabled())
                .growthAnalyticsEnabled(u.getGrowthAnalyticsEnabled())
                .growthDeliveryIntegrations(u.getGrowthDeliveryIntegrations())
                .brandingEnabled(u.getBrandingEnabled())
                .brandingName(u.getBrandingName())
                .brandingLogoUrl(u.getBrandingLogoUrl())
                .brandingPrimaryColor(u.getBrandingPrimaryColor())
                .brandingSecondaryColor(u.getBrandingSecondaryColor())
                .build();
    }

    /**
     * Get a single seller's config by owner ID (for admin setup screens).
     * Admin-only.
     */
    @GetMapping("/admin/sellers/{ownerId}/config")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ASSISTANT_ADMIN')")
    public ResponseEntity<SellerConfigDto> getSellerConfig(@PathVariable UUID ownerId) {
        return userRepository.findById(ownerId)
                .filter(SellerConfigController::isOwner)
                .map(SellerConfigController::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().<SellerConfigDto>build());
    }

    /**
     * List all owners (sellers) with their pricing plan and branding configuration.
     * Admin-only.
     */
    @GetMapping("/admin/sellers/config")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ASSISTANT_ADMIN')")
    public ResponseEntity<List<SellerConfigDto>> listSellerConfigs() {
        List<SellerConfigDto> sellers = userRepository.findByRoleIgnoreCaseAndBusinessIdIsNotNullOrderByBusinessNameAsc("owner")
                .stream()
                .map(SellerConfigController::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(sellers);
    }

    /**
     * Set/adjust pricing plan for a given seller (owner).
     * Body: { "pricingPlan": "starter" | "growth" | "pro" }
     *
     * Plans:
     *  - starter: KES 0 / month — basic marketplace participation (commission-only)
     *  - growth:  KES 1,500 / month — automation, analytics, delivery integrations
     *  - pro:     KES 5,000 / month — white-label store, advanced analytics, marketing
     */
    @PatchMapping("/admin/sellers/{ownerId}/pricing")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ASSISTANT_ADMIN')")
    public ResponseEntity<SellerConfigDto> setPricingPlan(
            @PathVariable UUID ownerId,
            @RequestBody Map<String, String> body) {
        String plan = body.getOrDefault("pricingPlan", "").trim().toLowerCase();
        if (plan.isEmpty() || !(plan.equals("starter") || plan.equals("growth") || plan.equals("pro"))) {
            return ResponseEntity.badRequest().<SellerConfigDto>build();
        }
        return userRepository.findById(ownerId)
                .filter(SellerConfigController::isOwner)
                .map(user -> {
                    // When leaving pro, turn off white-label branding.
                    if (!"pro".equals(plan)) {
                        user.setBrandingEnabled(false);
                        user.setBrandingName(null);
                        user.setBrandingLogoUrl(null);
                        user.setBrandingPrimaryColor(null);
                        user.setBrandingSecondaryColor(null);
                    }
                    // When leaving growth, clear Growth plan feature flags so they are not active.
                    if (!"growth".equals(plan)) {
                        user.setGrowthInventoryAutomation(false);
                        user.setGrowthWhatsappEnabled(false);
                        user.setGrowthAnalyticsEnabled(false);
                        user.setGrowthDeliveryIntegrations(false);
                    }
                    user.setPricingPlan(plan);
                    User saved = userRepository.save(user);
                    return ResponseEntity.ok(toDto(saved));
                })
                .orElse(ResponseEntity.notFound().<SellerConfigDto>build());
    }

    /**
     * Configure white-label branding for a seller (owner).
     * Body may contain:
     *  - brandingEnabled (Boolean)
     *  - brandingName
     *  - brandingLogoUrl
     *  - brandingPrimaryColor
     *  - brandingSecondaryColor
     *
     * The default platform brand applies when brandingEnabled is false or config fields are null.
     */
    @PutMapping("/admin/sellers/{ownerId}/branding")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ASSISTANT_ADMIN')")
    public ResponseEntity<SellerConfigDto> setBranding(
            @PathVariable UUID ownerId,
            @RequestBody Map<String, Object> body) {
        return userRepository.findById(ownerId)
                .filter(SellerConfigController::isOwner)
                .map(user -> {
                    Object enabledRaw = body.get("brandingEnabled");
                    if (enabledRaw instanceof Boolean enabled) {
                        // White-label branding is only available on the Pro plan.
                        if (enabled && (user.getPricingPlan() == null || !"pro".equalsIgnoreCase(user.getPricingPlan()))) {
                            return ResponseEntity.status(400).<SellerConfigDto>build();
                        }
                        user.setBrandingEnabled(enabled);
                    }
                    if (body.containsKey("brandingName")) {
                        user.setBrandingName((String) body.get("brandingName"));
                    }
                    if (body.containsKey("brandingLogoUrl")) {
                        user.setBrandingLogoUrl((String) body.get("brandingLogoUrl"));
                    }
                    if (body.containsKey("brandingPrimaryColor")) {
                        user.setBrandingPrimaryColor((String) body.get("brandingPrimaryColor"));
                    }
                    if (body.containsKey("brandingSecondaryColor")) {
                        user.setBrandingSecondaryColor((String) body.get("brandingSecondaryColor"));
                    }
                    User saved = userRepository.save(user);
                    return ResponseEntity.ok(toDto(saved));
                })
                .orElse(ResponseEntity.notFound().<SellerConfigDto>build());
    }

    /**
     * Configure Growth plan features for a seller (owner).
     * Only applicable when the seller's pricing plan is "growth".
     * Body may contain: growthInventoryAutomation, growthWhatsappEnabled,
     * growthAnalyticsEnabled, growthDeliveryIntegrations (all Boolean).
     * When enabled for a Growth seller, these features are active for that seller.
     */
    @PutMapping("/admin/sellers/{ownerId}/growth-config")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ASSISTANT_ADMIN')")
    public ResponseEntity<SellerConfigDto> setGrowthConfig(
            @PathVariable UUID ownerId,
            @RequestBody Map<String, Object> body) {
        return userRepository.findById(ownerId)
                .filter(SellerConfigController::isOwner)
                .map(user -> {
                    if (user.getPricingPlan() == null || !"growth".equalsIgnoreCase(user.getPricingPlan())) {
                        return ResponseEntity.badRequest().<SellerConfigDto>build();
                    }
                    if (body.containsKey("growthInventoryAutomation") && body.get("growthInventoryAutomation") instanceof Boolean b) {
                        user.setGrowthInventoryAutomation(b);
                    }
                    if (body.containsKey("growthWhatsappEnabled") && body.get("growthWhatsappEnabled") instanceof Boolean b) {
                        user.setGrowthWhatsappEnabled(b);
                    }
                    if (body.containsKey("growthAnalyticsEnabled") && body.get("growthAnalyticsEnabled") instanceof Boolean b) {
                        user.setGrowthAnalyticsEnabled(b);
                    }
                    if (body.containsKey("growthDeliveryIntegrations") && body.get("growthDeliveryIntegrations") instanceof Boolean b) {
                        user.setGrowthDeliveryIntegrations(b);
                    }
                    User saved = userRepository.save(user);
                    return ResponseEntity.ok(toDto(saved));
                })
                .orElse(ResponseEntity.notFound().<SellerConfigDto>build());
    }

    /**
     * Seller (owner) can read their own pricing/branding configuration for dashboards/storefront.
     */
    @GetMapping("/sellers/me/config")
    public ResponseEntity<SellerConfigDto> getMyConfig(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(401).<SellerConfigDto>build();
        }
        return userRepository.findById(currentUser.userId())
                .map(SellerConfigController::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().<SellerConfigDto>build());
    }
}

