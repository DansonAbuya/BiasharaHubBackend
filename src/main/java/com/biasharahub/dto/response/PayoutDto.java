package com.biasharahub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Payout response; does not expose full destination details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutDto {
    private UUID id;
    private BigDecimal amount;
    private String method;
    private String status;
    private Instant createdAt;
    private Instant processedAt;
    private String failureReason;
}
