package com.biasharahub.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateSupplierRequest {
    @NotBlank(message = "Supplier name is required")
    private String name;

    private String phone;

    @Email(message = "Invalid email")
    private String email;
}

