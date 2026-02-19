package com.biasharahub.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @Column(name = "notification_id")
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID notificationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Notification type: order, shipment, payment, system, alert, etc.
     */
    @Column(name = "type", nullable = false, length = 32)
    private String type;

    @Column(name = "title", nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    /**
     * Optional deep-link URL within the app (e.g. /dashboard/orders).
     */
    @Column(name = "action_url", length = 255)
    private String actionUrl;

    /**
     * Optional JSON payload for future use (e.g. IDs).
     */
    @Column(name = "data", columnDefinition = "TEXT")
    private String data;

    @Column(name = "read", nullable = false)
    @Builder.Default
    private boolean read = false;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}

