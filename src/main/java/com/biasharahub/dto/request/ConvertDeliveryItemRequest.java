package com.biasharahub.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request to convert part of a received delivery item into customer-facing sale units.
 * Subdivision is the customer-facing name for the product; the supplier-facing name remains the original.
 * One supplier-facing product can be divided into several subdivisions (e.g. 500g, 1kg, fillet).
 * Supports two modes: (1) pieces per unit (e.g. 3 fillets per fish), (2) unit-based (e.g. 10 kg → 500 g sub-units).
 */
@Data
public class ConvertDeliveryItemRequest {

    /**
     * Optional existing target product. If not provided, a new customer-facing product will be created
     * using targetName/targetPrice.
     */
    private UUID targetProductId;

    /**
     * Customer-facing name for this subdivision (e.g. "Tilapia 500g"). The supplier-facing name
     * stays on the original product. Required when targetProductId is null.
     */
    private String targetName;

    /**
     * Optional override for customer-facing price per unit for the sale product.
     * If null, the system will calculate a default from the supplier cost and subdivision size (no loss).
     */
    private BigDecimal targetPrice;

    /**
     * Optional explicit number of sale units to create for the target product.
     * If null, derived from piecesPerUnit or from targetUnitSize + targetUnit when source has a unit.
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
     * value is 3. When provided (and not using targetUnitSize/targetUnit), the system
     * derives producedQuantity and a default unit price from the supplier cost.
     */
    private Integer piecesPerUnit;

    /**
     * For unit-based subdivision: size of each sub-unit in the target unit (e.g. 500 for "500 grams").
     * Use with targetUnit. Example: 10 kg supplied at 2000/kg → subdivide into 500 g → 20 sub-units, cost 1000 per 500 g.
     */
    private BigDecimal targetUnitSize;

    /**
     * For unit-based subdivision: unit of each sub-unit (e.g. "g", "kg", "L", "ml", "piece").
     * Must be compatible with the source item's unit for conversion (e.g. kg and g).
     */
    private String targetUnit;

    /**
     * Optional note for the conversion, stored in the stock ledger.
     */
    private String note;
}

