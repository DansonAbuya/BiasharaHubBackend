package com.biasharahub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tenant's default payout destination for auto-payout; destination is masked.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DefaultPayoutDestinationDto {
    private String method;           // MPESA or BANK_TRANSFER
    private String destinationMasked; // e.g. 2547***6789
}
