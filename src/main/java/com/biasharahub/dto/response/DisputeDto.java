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
public class DisputeDto {
    private UUID id;
    private UUID orderId;
    private String orderNumber;
    private UUID reporterUserId;
    private String reporterName;
    private String disputeType;
    private String status;
    private String description;
    private String deliveryProofUrl;
    private String sellerResponse;
    private Instant sellerRespondedAt;
    private Instant resolvedAt;
    private UUID resolvedByUserId;
    private String resolution;
    private String strikeReason;
    private Instant createdAt;
    private Instant updatedAt;
}
