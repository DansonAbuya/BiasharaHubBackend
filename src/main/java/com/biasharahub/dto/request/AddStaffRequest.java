package com.biasharahub.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request to add a staff member. Only owners can add staff.
 * A temporary password is generated and sent by email; staff must log in and enable 2FA.
 */
@Data
public class AddStaffRequest {
    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Email is required")
    @Email
    private String email;
}
