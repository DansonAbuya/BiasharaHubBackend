package com.biasharahub.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/** Owner applies to become a verified service provider: category, delivery type, location (if physical), qualification document URLs. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceProviderApplyRequest {
    /** Primary service category (e.g. Consulting, Repair). */
    private UUID serviceCategoryId;
    /** ONLINE, PHYSICAL, or BOTH. */
    private String serviceDeliveryType;
    /** Service location latitude. Required if serviceDeliveryType is PHYSICAL or BOTH. */
    private Double locationLat;
    /** Service location longitude. Required if serviceDeliveryType is PHYSICAL or BOTH. */
    private Double locationLng;
    /** Description of service location (address, landmark, directions). Required if serviceDeliveryType is PHYSICAL or BOTH. */
    private String locationDescription;
    /** List of document types and file URLs for qualification/expertise (e.g. qualification_cert, license). */
    private List<DocumentUpload> documents;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentUpload {
        private String documentType;
        private String fileUrl;
    }
}
