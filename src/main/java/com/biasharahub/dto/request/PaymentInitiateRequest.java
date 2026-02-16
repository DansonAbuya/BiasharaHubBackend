package com.biasharahub.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for initiating an M-Pesa payment (STK Push).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentInitiateRequest {

    /**
     * Customer's M-Pesa phone number (e.g. 07XXXXXXXX or 2547XXXXXXXX).
     */
    @NotBlank
    private String phoneNumber;
}

