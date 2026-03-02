package com.biasharahub.dto.request;

import jakarta.validation.constraints.Email;
import lombok.Data;

@Data
public class UpdateSupplierRequest {
    private String name;
    private String phone;

    @Email(message = "Invalid email")
    private String email;
}

