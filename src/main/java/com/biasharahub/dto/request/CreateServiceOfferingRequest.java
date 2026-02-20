package com.biasharahub.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CreateServiceOfferingRequest {

    @NotBlank(message = "Service name is required")
    private String name;

    /** Predefined service category (e.g. Consulting, Repair). Required. */
    @NotNull(message = "Service category is required")
    private UUID categoryId;

    /** Optional: override category name for display (otherwise from categoryId). */
    private String category;

    private String description;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0", message = "Price must be non-negative")
    private BigDecimal price;

    /** VIRTUAL (online) or PHYSICAL (in-person). */
    @NotBlank(message = "Delivery type is required")
    @Pattern(regexp = "VIRTUAL|PHYSICAL", message = "Delivery type must be VIRTUAL or PHYSICAL")
    private String deliveryType;

    /** For VIRTUAL: online meeting link (e.g. Zoom/Meet). */
    private String meetingLink;
    /** For VIRTUAL: extra instructions. */
    private String meetingDetails;
    /**
     * For VIRTUAL services: comma-separated delivery methods.
     * VIDEO_CALL, PHONE_CALL, WHATSAPP, LIVE_CHAT, EMAIL, SCREEN_SHARE, FILE_DELIVERY, RECORDED_CONTENT, SOCIAL_MEDIA
     */
    private String onlineDeliveryMethods;
    /** BEFORE_BOOKING, AFTER_SERVICE, or AS_PER_CONTRACT. */
    @Pattern(regexp = "BEFORE_BOOKING|AFTER_SERVICE|AS_PER_CONTRACT", message = "Invalid payment timing")
    private String paymentTiming;

    /** Optional duration in minutes. */
    private Integer durationMinutes;

    @NotNull(message = "Active flag is required")
    private Boolean isActive = true;
}
