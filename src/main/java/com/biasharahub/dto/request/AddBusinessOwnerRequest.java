package com.biasharahub.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request to onboard a business owner who can sell products, offer services, or both.
 * Admin creates the account; a temporary password is sent by email.
 * The owner logs in and completes verification for their selected business type(s).
 */
@Data
public class AddBusinessOwnerRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Email is required")
    @Email
    private String email;

    @NotBlank(message = "Business name is required")
    private String businessName;

    /** If true, owner intends to sell products and must complete product seller verification. */
    private boolean sellsProducts;

    /** If true, owner intends to offer services and must complete service provider verification. */
    private boolean offersServices;

    // ---- Product seller fields (required if sellsProducts = true) ----

    /** Tier the business is applying for (tier1, tier2, tier3). Required if sellsProducts is true. */
    private String applyingForTier;

    /** Payout method: MPESA or BANK_TRANSFER. Required if sellsProducts is true. */
    private String payoutMethod;

    /** Payout destination (M-Pesa number or bank account). Required if sellsProducts is true. */
    private String payoutDestination;
}
