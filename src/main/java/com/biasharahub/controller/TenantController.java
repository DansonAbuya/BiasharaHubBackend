package com.biasharahub.controller;

import com.biasharahub.entity.Tenant;
import com.biasharahub.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Public endpoint for tenant branding (logo, colors). Used by frontend for white-labeling.
 */
@RestController
@RequestMapping("/public/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantRepository tenantRepository;

    @GetMapping("/{tenantId}/branding")
    public ResponseEntity<Map<String, String>> getTenantBranding(@PathVariable UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .filter(t -> Boolean.TRUE.equals(t.getIsActive()))
                .map(t -> ResponseEntity.ok(Map.of(
                        "name", t.getName() != null ? t.getName() : "BiasharaHub",
                        "logoUrl", t.getLogoUrl() != null ? t.getLogoUrl() : "/api/static/logo.png",
                        "primaryColor", t.getPrimaryColor() != null ? t.getPrimaryColor() : "#2E7D32",
                        "accentColor", t.getAccentColor() != null ? t.getAccentColor() : "#FF8F00"
                )))
                .orElse(ResponseEntity.notFound().build());
    }
}
