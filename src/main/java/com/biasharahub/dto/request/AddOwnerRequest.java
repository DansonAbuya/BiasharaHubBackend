package com.biasharahub.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request to add an owner. Only platform admin (super_admin) can add owners.
 * A temporary password is generated and sent by email; owner must log in and enable 2FA.
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
}
