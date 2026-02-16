package com.biasharahub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourierServiceDto {
    private UUID courierId;
    private String name;
    private String code;
    private String description;
    private String trackingUrlTemplate;
    /** MANUAL, DHL, FEDEX, SENDY, REST */
    private String providerType;
    private String apiBaseUrl;
    private Boolean isActive;
    private BigDecimal baseRate;
    private BigDecimal ratePerKg;
    private Instant createdAt;
    private Instant updatedAt;
}
