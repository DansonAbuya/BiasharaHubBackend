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
public class SupplierDeliveryItemDto {
    private UUID id;
    private UUID productId;
    private String productName;
    /** Quantity supplier stated they dispatched. */
    private Integer quantity;
    /** Quantity seller confirmed received. Null = not yet confirmed; use quantity when moving to stock. */
    private Integer receivedQuantity;
    /** Cost per unit (supplier stated). */
    private BigDecimal unitCost;
    /** Unit of measure for quantity (e.g. kg, g, piece). */
    private String unitOfMeasure;
    /** Quantity already consumed by conversions before stock is added. */
    private Integer convertedQuantity;
    /** Line total: quantity × unitCost (supplier stated). */
    private BigDecimal lineTotal;
    /** Received line total: receivedQuantity × unitCost (for P&L). */
    private BigDecimal receivedLineTotal;
    /** Current product selling price (for P&L: potential revenue = receivedQty × productPrice). */
    private BigDecimal productPrice;
    private Instant createdAt;
}

