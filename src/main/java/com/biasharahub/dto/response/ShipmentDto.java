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
    private String deliveryMode;
    private String status;
    private String carrier;
    private String trackingNumber;
    private String riderName;
    private String riderPhone;
    private String riderVehicle;
    private String riderJobId;
    private String pickupLocation;
    private Instant shippedAt;
    private Instant estimatedDelivery;
    private Instant actualDelivery;
    private Instant escrowReleasedAt;
    // For Phase 1 MVP we expose OTP so customer can see it in-app.
    private String otpCode;
}
