package com.biasharahub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceOfferingDto implements Serializable {
    private static final long serialVersionUID = 1L;
    private UUID id;
    private String name;
    private UUID categoryId;
    private String category;
    private String description;
    private BigDecimal price;
    private String businessId;
    /** VIRTUAL or PHYSICAL */
    private String deliveryType;
    private String meetingLink;
    private String meetingDetails;
    /** Comma-separated online delivery methods: VIDEO_CALL, PHONE_CALL, WHATSAPP, LIVE_CHAT, EMAIL, SCREEN_SHARE, FILE_DELIVERY, RECORDED_CONTENT, SOCIAL_MEDIA */
    private String onlineDeliveryMethods;
    /** BEFORE_BOOKING, AFTER_SERVICE, AS_PER_CONTRACT */
    private String paymentTiming;
    private Integer durationMinutes;
    private Boolean isActive;
    /** Main image URL to showcase this service */
    private String imageUrl;
    /** Optional video URL to demonstrate this service */
    private String videoUrl;
    /** Comma-separated list of additional image URLs */
    private String galleryUrls;
    private Instant createdAt;
    private Instant updatedAt;
}
