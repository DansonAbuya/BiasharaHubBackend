package com.biasharahub.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreateSupplierRequest {
    @NotBlank(message = "Supplier name is required")
    private String name;

    @NotBlank(message = "Supplier phone is required")
    @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "Phone must be in international format, e.g. +254712345678")
    private String phone;

    @NotBlank(message = "Supplier email is required")
    @Email(message = "Invalid email")
    private String email;
}

