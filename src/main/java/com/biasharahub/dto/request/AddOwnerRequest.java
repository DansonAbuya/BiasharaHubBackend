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
}
