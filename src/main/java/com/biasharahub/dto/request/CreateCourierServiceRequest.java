package com.biasharahub.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCourierServiceRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Code is required")
    private String code;

    private String description;

    /** e.g. https://track.example.com/{trackingNumber} */
    private String trackingUrlTemplate;

    /** MANUAL, DHL, FEDEX, SENDY, REST */
    @Builder.Default
    private String providerType = "MANUAL";

    /** Provider API base URL (for REST or integrated providers). */
    private String apiBaseUrl;

    @Builder.Default
    private Boolean isActive = true;

    @NotNull(message = "Base rate is required")
    @Builder.Default
    private BigDecimal baseRate = BigDecimal.ZERO;

    @NotNull(message = "Rate per kg is required")
    @Builder.Default
    private BigDecimal ratePerKg = BigDecimal.ZERO;
}
