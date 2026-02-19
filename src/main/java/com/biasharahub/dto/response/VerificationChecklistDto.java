package com.biasharahub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Trust & Safety: seller verification checklist (ID/docs, phone, M-Pesa, location, terms).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationChecklistDto {
    private boolean phoneVerified;
    private boolean mpesaValidated;
    private boolean businessLocationVerified;
    private boolean termsAccepted;
}
