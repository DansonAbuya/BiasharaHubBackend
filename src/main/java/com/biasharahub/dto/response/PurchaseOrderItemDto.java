package com.biasharahub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderItemDto {
    private UUID id;
    private UUID purchaseOrderId;
    private UUID productId;
    private String productName;
    private String description;
    private String unitOfMeasure;
    private Integer requestedQuantity;
    private BigDecimal expectedUnitCost;
    private Instant createdAt;
}

