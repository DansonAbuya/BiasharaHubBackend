package com.biasharahub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderDto {
    private UUID id;
    private UUID businessId;
    private UUID supplierId;
    private String supplierName;
    private String poNumber;
    private String deliveryNoteRef;
    private Instant expectedDeliveryDate;
    private String status;
    private Instant createdAt;
    private String createdByName;
    private List<PurchaseOrderItemDto> items;
}

