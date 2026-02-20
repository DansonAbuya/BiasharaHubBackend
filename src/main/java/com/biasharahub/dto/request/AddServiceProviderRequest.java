package com.biasharahub.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request to onboard a service provider. Admin creates the account; a temporary password
 * (verification code) is sent by email. The service provider logs in, uploads service details,
 * verification and qualification documents; admin then verifies and approves so services are listed.
 */
@Data
public class AddServiceProviderRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Email is required")
    @Email
    private String email;

    @NotBlank(message = "Business or service name is required")
    private String businessName;
}
