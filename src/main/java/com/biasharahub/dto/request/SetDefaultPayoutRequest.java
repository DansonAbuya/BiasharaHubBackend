package com.biasharahub.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to set the tenant's default payout destination for auto-payout on delivery.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetDefaultPayoutRequest {

    @NotBlank(message = "Method is required (MPESA or BANK_TRANSFER)")
    private String method;

    /**
     * For MPESA: recipient phone (2547XXXXXXXX or 07XXXXXXXX).
     * For BANK_TRANSFER: bank name and account (stored encrypted).
     */
    @NotBlank(message = "Destination is required")
    private String destination;
}
