package com.biasharahub.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCourierServiceRequest {

    private String name;
    private String description;
    /** e.g. https://track.example.com/{trackingNumber} */
    private String trackingUrlTemplate;
    private String providerType;
    private String apiBaseUrl;
    private Boolean isActive;
    private BigDecimal baseRate;
    private BigDecimal ratePerKg;
}
