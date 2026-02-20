package com.biasharahub.dto.request;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateServiceAppointmentRequest {

    /** New status: CONFIRMED, COMPLETED, CANCELLED, NO_SHOW, SERVICE_PROVIDED, CUSTOMER_CONFIRMED, CUSTOMER_DISPUTED */
    @Pattern(regexp = "CONFIRMED|COMPLETED|CANCELLED|NO_SHOW|SERVICE_PROVIDED|CUSTOMER_CONFIRMED|CUSTOMER_DISPUTED", message = "Invalid status")
    private String status;
    /** When marking SERVICE_PROVIDED: evidence URL (e.g. meeting recording). */
    private String evidenceUrl;
    /** When marking SERVICE_PROVIDED: optional notes. */
    private String evidenceNotes;
}
