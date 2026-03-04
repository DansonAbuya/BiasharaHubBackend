package com.biasharahub.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request to convert part of a received delivery item into a separate sale product/units.
 * Used when the seller wants to subdivide bulk stock into smaller units of sale.
 */
@Data
public class ConvertDeliveryItemRequest {

    /**
     * Optional existing target product. If not provided, a new product will be created
     * using targetName/targetPrice.
     */
    private UUID targetProductId;

    /**
     * Name for the sale product (customer-facing). Required when targetProductId is null.
     */
    private String targetName;

    /**
     * Customer-facing price per unit for the sale product. Required when targetProductId is null.
     */
    private BigDecimal targetPrice;

    /**
     * Number of sale units to create for the target product.
     */
    private Integer producedQuantity;

    /**
     * Quantity of the source product to consume from stock for this conversion.
     * If null, defaults to the delivery item's receivedQuantity (or quantity if not set).
     */
    private Integer sourceQuantityUsed;

    /**
     * Optional note for the conversion, stored in the stock ledger.
     */
    private String note;
}

