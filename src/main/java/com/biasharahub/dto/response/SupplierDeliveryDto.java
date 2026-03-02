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
public class SupplierDeliveryDto {
    private UUID id;
    private UUID businessId;
    private UUID supplierId;
    private String supplierName;
    private String deliveryNoteRef;
    private Instant deliveredAt;
    private Instant receivedAt;
    private UUID receivedByUserId;
    private String receivedByName;
    private String status;
    private Instant createdAt;
    private List<SupplierDeliveryItemDto> items;
    /** Supplier-stated total quantity across all items. */
    private Integer totalQuantity;
    /** Supplier-stated total cost (sum of quantity × unitCost per item). */
    private java.math.BigDecimal totalCost;
    /** Seller-confirmed total cost (sum of receivedQuantity × unitCost). For P&L. */
    private java.math.BigDecimal totalReceivedCost;
    /** Potential revenue if all received items sold at current price. */
    private java.math.BigDecimal potentialRevenue;
    /** Profit/loss estimate: potentialRevenue − totalReceivedCost. */
    private java.math.BigDecimal profitLoss;
}

