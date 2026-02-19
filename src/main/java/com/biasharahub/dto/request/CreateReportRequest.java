package com.biasharahub.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateReportRequest {
    @NotBlank(message = "Target type is required (product, order, business)")
    private String targetType;

    @NotNull
    private UUID targetId;

    private String reason;

    private String description;
}
