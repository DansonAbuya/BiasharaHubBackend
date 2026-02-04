package com.biasharahub.mail;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * DTO for sending email. Supports plain text and optional HTML; multi-tenant via tenantId.
 */
@Data
@Builder
public class EmailMessage {

    /** Optional tenant ID for tenant-specific sender/config (null = default). */
    private String tenantId;

    private String from;
    private List<String> to;
    private List<String> cc;
    private List<String> bcc;
    private String subject;
    private String textBody;
    private String htmlBody;
}
