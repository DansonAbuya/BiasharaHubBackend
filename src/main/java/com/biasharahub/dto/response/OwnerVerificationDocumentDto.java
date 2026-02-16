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
public class OwnerVerificationDocumentDto {
    private UUID documentId;
    private UUID userId;
    private String documentType;
    private String fileUrl;
    private Instant uploadedAt;
}
