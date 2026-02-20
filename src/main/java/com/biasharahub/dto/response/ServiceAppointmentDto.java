package com.biasharahub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceAppointmentDto implements Serializable {
    private static final long serialVersionUID = 1L;
    private UUID id;
    private UUID serviceId;
    private String serviceName;
    private UUID userId;
    private String userName;
    private LocalDate requestedDate;
    private LocalTime requestedTime;
    private String status;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;
    /** For provider: business id of the service. */
    private String businessId;
    /** Payment for this booking: amount (service price) and status. */
    private java.math.BigDecimal amount;
    private String paymentStatus;
    /** For virtual: HELD, RELEASED, REFUNDED. */
    private String escrowStatus;
    /** Meeting link (from service); sent when provider confirms. */
    private String meetingLink;
    private Instant meetingLinkSentAt;
    private String evidenceUrl;
    private String evidenceNotes;
}
