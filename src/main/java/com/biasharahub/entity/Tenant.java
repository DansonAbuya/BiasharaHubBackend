package com.biasharahub.entity;

import com.biasharahub.config.EncryptedStringAttributeConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Tenant entity - stored in public schema. Each tenant has an isolated schema.
 */
@Entity
@Table(name = "tenants", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "schema_name", nullable = false, unique = true)
    private String schemaName;

    @Column(nullable = false)
    private String name;

    private String domain;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "primary_color")
    private String primaryColor;

    @Column(name = "accent_color")
    private String accentColor;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * Default payout method for auto-payout on delivery: MPESA or BANK_TRANSFER.
     */
    @Column(name = "default_payout_method", length = 32)
    private String defaultPayoutMethod;

    /**
     * Default payout destination (encrypted): M-Pesa phone or bank account details.
     */
    @Convert(converter = EncryptedStringAttributeConverter.class)
    @Column(name = "default_payout_destination", length = 1024)
    private String defaultPayoutDestination;
}
