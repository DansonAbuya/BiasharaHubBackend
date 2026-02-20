package com.biasharahub.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class CreateServiceAppointmentRequest {

    @NotNull(message = "Requested date is required")
    private LocalDate requestedDate;

    private LocalTime requestedTime;

    private String notes;
}
