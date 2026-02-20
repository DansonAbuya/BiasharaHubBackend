package com.biasharahub.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class UpdateServiceOfferingRequest {

    private String name;

    private UUID categoryId;

    private String category;

    private String description;

    @DecimalMin(value = "0", message = "Price must be non-negative")
    private BigDecimal price;

    @Pattern(regexp = "VIRTUAL|PHYSICAL", message = "Delivery type must be VIRTUAL or PHYSICAL")
    private String deliveryType;

    private String meetingLink;
    private String meetingDetails;
    @Pattern(regexp = "BEFORE_BOOKING|AFTER_SERVICE|AS_PER_CONTRACT", message = "Invalid payment timing")
    private String paymentTiming;

    private Integer durationMinutes;

    private Boolean isActive;
}
