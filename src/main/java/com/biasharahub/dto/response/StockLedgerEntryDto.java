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
public class StockLedgerEntryDto {
    private UUID id;
    private UUID businessId;
    private UUID productId;
    private String productName;
    private Integer changeQty;
    private Integer previousQty;
    private Integer newQty;
    private String entryType;
    private UUID supplierId;
    private String supplierName;
    private UUID deliveryId;
    private UUID orderId;
    private UUID performedByUserId;
    private String performedByName;
    private String note;
    private Instant createdAt;
}

