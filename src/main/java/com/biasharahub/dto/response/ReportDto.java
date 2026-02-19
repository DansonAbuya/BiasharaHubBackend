package com.biasharahub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportDto {
    private UUID reportId;
    private UUID reporterUserId;
    private String reporterEmail;
    private String targetType;
    private UUID targetId;
    private String reason;
    private String description;
    private String status;
    private Instant createdAt;
    private Instant resolvedAt;
    private UUID resolvedByUserId;
    private String resolutionNotes;
}
