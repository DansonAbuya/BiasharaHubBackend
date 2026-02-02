package com.biasharahub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentDto {
    private UUID id;
    private UUID orderId;
    private String status;
    private String carrier;
    private String trackingNumber;
    private Instant shippedAt;
    private Instant estimatedDelivery;
    private Instant actualDelivery;
}
