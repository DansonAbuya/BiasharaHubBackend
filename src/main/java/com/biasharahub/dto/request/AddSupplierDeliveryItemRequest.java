package com.biasharahub.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class AddSupplierDeliveryItemRequest {
    @NotNull(message = "Product is required")
    private UUID productId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    @DecimalMin(value = "0.00", inclusive = true, message = "Unit cost must be non-negative")
    private BigDecimal unitCost;

    /** Unit of measure (e.g. kg, g, L, piece). Visible to seller for pricing and subdivision. */
    private String unitOfMeasure;
}

