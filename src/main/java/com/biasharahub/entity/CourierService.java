package com.biasharahub.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Platform courier service (public schema). Sellers choose from these when delivery mode is COURIER.
 */
@Entity
@Table(name = "courier_services", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourierService {

    @Id
    @Column(name = "courier_id")
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID courierId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** e.g. https://track.example.com/{trackingNumber} - {trackingNumber} replaced when building link */
    @Column(name = "tracking_url_template", length = 1024)
    private String trackingUrlTemplate;

    /** MANUAL = no API (seller enters tracking); DHL, FEDEX, SENDY, REST = integrated provider. */
    @Column(name = "provider_type", length = 32)
    @Builder.Default
    private String providerType = "MANUAL";

    /** Provider API base URL (for REST or provider-specific client). API keys from env, not stored here. */
    @Column(name = "api_base_url", length = 512)
    private String apiBaseUrl;

    /** Non-secret provider config as JSON (e.g. defaultServiceType, accountId). */
    @Column(name = "config_json", columnDefinition = "TEXT")
    private String configJson;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "base_rate", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal baseRate = BigDecimal.ZERO;

    @Column(name = "rate_per_kg", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal ratePerKg = BigDecimal.ZERO;

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

    /** Build tracking URL for a tracking number (replaces {trackingNumber} in template). */
    public String buildTrackingUrl(String trackingNumber) {
        if (trackingUrlTemplate == null || trackingUrlTemplate.isBlank() || trackingNumber == null || trackingNumber.isBlank()) {
            return null;
        }
        return trackingUrlTemplate.replace("{trackingNumber}", trackingNumber.trim());
    }
}
