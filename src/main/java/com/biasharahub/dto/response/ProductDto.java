package com.biasharahub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDto implements Serializable {
    private static final long serialVersionUID = 1L;
    private UUID id;
    private String name;
    private String category;
    private BigDecimal price;
    private Integer quantity;
    /** Quantity in supplier deliveries that are being processed (not yet in stock). Shown to customers as "coming soon". */
    private Integer processingQuantity;
    private String description;
    private String image;
    private List<String> images;
    private String businessId;
    private String moderationStatus;
    /** When set, this product is a customer-facing subdivision of the supplier-facing product with this ID. */
    private UUID sourceProductId;
}
