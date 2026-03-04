package com.biasharahub.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
public class CreatePurchaseOrderRequest {

    @NotNull
    private UUID supplierId;

    private String poNumber;
    private String deliveryNoteRef;
    private Instant expectedDeliveryDate;

    @NotEmpty(message = "At least one purchase order item is required")
    @Valid
    private List<Item> items;

    @Data
    public static class Item {
        private UUID productId; // optional – allow free-form description
        private String description; // required if productId is null
        private String unitOfMeasure; // e.g. kg, piece, box

        /**
         * Optional customer-facing name for this item. If provided and productId is null,
         * this will be used as the initial Product.name; otherwise the description is used.
         */
        private String customerName;

        /**
         * Optional initial customer-facing price for this item. If provided and productId
         * is null, this will be used as the initial Product.price; otherwise expectedUnitCost
         * (supplier-facing expected cost) is used.
         */
        private BigDecimal customerPrice;

        @NotNull
        private Integer requestedQuantity;

        // optional; supplier will quote actual unit cost when dispatching
        private BigDecimal expectedUnitCost;
    }
}

