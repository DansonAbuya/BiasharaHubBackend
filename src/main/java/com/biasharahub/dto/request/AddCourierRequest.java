package com.biasharahub.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request to add a courier user. Only owners can add couriers.
 * A temporary password is generated and sent by email.
 * Phone is required for matching shipments by rider_phone.
 */
@Data
public class AddCourierRequest {
    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Email is required")
    @Email
    private String email;

    @NotBlank(message = "Phone is required for courier matching")
    private String phone;
}
