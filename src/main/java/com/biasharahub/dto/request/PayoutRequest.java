package com.biasharahub.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request to create a payout from the tenant wallet.
 * method: MPESA (phone in destinationDetails) or BANK_TRANSFER (account info).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "10", message = "Minimum payout is 10 KES")
    private BigDecimal amount;

    @NotBlank(message = "Method is required (MPESA or BANK_TRANSFER)")
    private String method;

    /**
     * For MPESA: recipient phone (2547XXXXXXXX or 07XXXXXXXX).
     * For BANK_TRANSFER: bank name and account (stored encrypted).
     */
    @NotBlank(message = "Destination details are required")
    private String destinationDetails;
}
