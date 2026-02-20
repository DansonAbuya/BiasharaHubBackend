package com.biasharahub.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * BiasharaHub Services: booking for a physical service. Customer books appointment, then attends in person.
 * Virtual services are delivered via online meeting or other means (no appointment entity required).
 */
@Entity
@Table(name = "service_appointments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceAppointment {

    @Id
    @Column(name = "appointment_id")
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID appointmentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceOffering service;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "requested_date", nullable = false)
    private LocalDate requestedDate;

    @Column(name = "requested_time")
    private LocalTime requestedTime;

    /** PENDING, CONFIRMED, COMPLETED, CANCELLED, NO_SHOW, SERVICE_PROVIDED, CUSTOMER_CONFIRMED, CUSTOMER_DISPUTED */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** Per-appointment meeting link (e.g. Google Meet); overrides service default when set. */
    @Column(name = "meeting_link", columnDefinition = "TEXT")
    private String meetingLink;

    /** Google Calendar event id when meeting was created via Google Calendar/Meet. */
    @Column(name = "google_event_id", length = 255)
    private String googleEventId;

    /** When meeting link was sent to customer and provider (virtual services). */
    @Column(name = "meeting_link_sent_at")
    private Instant meetingLinkSentAt;

    /** Evidence that service was provided (e.g. meeting recording URL). */
    @Column(name = "evidence_url", columnDefinition = "TEXT")
    private String evidenceUrl;

    @Column(name = "evidence_notes", columnDefinition = "TEXT")
    private String evidenceNotes;

    @Column(name = "provider_marked_provided_at")
    private Instant providerMarkedProvidedAt;

    @Column(name = "customer_confirmed_at")
    private Instant customerConfirmedAt;

    @Column(name = "customer_disputed_at")
    private Instant customerDisputedAt;

    /** For virtual: HELD, RELEASED, REFUNDED. Null for physical or when no escrow. */
    @Column(name = "escrow_status", length = 20)
    private String escrowStatus;

    /** Customer location latitude (for physical services where customer specifies service location). */
    @Column(name = "customer_location_lat")
    private Double customerLocationLat;

    /** Customer location longitude (for physical services). */
    @Column(name = "customer_location_lng")
    private Double customerLocationLng;

    /** Customer's location description/address (for physical services). */
    @Column(name = "customer_location_description", columnDefinition = "TEXT")
    private String customerLocationDescription;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
