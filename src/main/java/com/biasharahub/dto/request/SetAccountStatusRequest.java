package com.biasharahub.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Admin request to set an owner's account status (active or disabled).
 */
@Data
public class SetAccountStatusRequest {

    @NotBlank(message = "Status is required")
    @Pattern(regexp = "(?i)active|disabled", message = "Status must be 'active' or 'disabled'")
    private String status;
}
