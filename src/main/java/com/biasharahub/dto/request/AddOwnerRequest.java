package com.biasharahub.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request to add an owner. Only platform admin (super_admin) can add owners.
 * A temporary password is generated and sent by email; owner must log in and enable 2FA.
 * Payout details are stored on the tenant and used for auto-payout when orders are delivered.
 */
@Data
public class AddOwnerRequest {
    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Email is required")
    @Email
    private String email;

    @NotBlank(message = "Business name is required")
    private String businessName;

    /** Required: tier the business is applying for (tier1, tier2, tier3). Determines which documents they must submit. */
    @NotBlank(message = "Applying for tier is required (tier1, tier2, or tier3)")
    private String applyingForTier;

    /**
     * Payout method for releasing seller payments on order delivery: MPESA or BANK_TRANSFER.
     * Stored as the tenant's default payout destination (used automatically on delivery confirmation).
     */
    @NotBlank(message = "Payout method is required (MPESA or BANK_TRANSFER)")
    private String payoutMethod;

    /**
     * Payout destination: for MPESA the seller's M-Pesa phone (2547XXXXXXXX or 07XXXXXXXX);
     * for BANK_TRANSFER, bank name and account details. Stored encrypted on the tenant.
     */
    @NotBlank(message = "Payout destination is required (e.g. M-Pesa number or bank account)")
    private String payoutDestination;
}
