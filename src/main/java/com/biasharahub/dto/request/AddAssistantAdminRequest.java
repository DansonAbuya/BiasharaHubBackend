package com.biasharahub.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request to add an assistant admin. Only platform admin (super_admin) can add assistant admins.
 * Temporary password is generated and sent by email; 2FA is always on and cannot be disabled.
 */
@Data
public class AddAssistantAdminRequest {
    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Email is required")
    @Email
    private String email;
}
