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
     * Optional override for customer-facing price per unit for the sale product.
     * If null, the system will calculate a default from the supplier cost and subdivision size.
     */
    private BigDecimal targetPrice;

    /**
     * Optional explicit number of sale units to create for the target product.
     * If null and piecesPerUnit is provided, the system will derive this value.
     */
    private Integer producedQuantity;

    /**
     * Quantity of the source product to consume from stock for this conversion.
     * If null, defaults to the delivery item's receivedQuantity (or quantity if not set).
     */
    private Integer sourceQuantityUsed;

    /**
     * "Size" of each subdivision expressed as how many pieces/units you get from
     * a single source unit. For example, if each fish is cut into 3 fillets, this
     * value is 3. When provided, the system will derive producedQuantity and a
     * default unit price from the supplier cost.
     */
    private Integer piecesPerUnit;

    /**
     * Optional note for the conversion, stored in the stock ledger.
     */
    private String note;
}

