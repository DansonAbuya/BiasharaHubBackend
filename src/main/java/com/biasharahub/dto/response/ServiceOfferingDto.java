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
    /** BEFORE_BOOKING, AFTER_SERVICE, AS_PER_CONTRACT */
    private String paymentTiming;
    private Integer durationMinutes;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;
}
