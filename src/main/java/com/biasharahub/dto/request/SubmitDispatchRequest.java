package com.biasharahub.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** Request for supplier to submit a dispatch (what they have sent to the seller). */
@Data
public class SubmitDispatchRequest {
    /** Purchase order this dispatch fulfills. Required so the seller can reconcile. */
    @NotNull(message = "Purchase order is required")
    private java.util.UUID purchaseOrderId;

    private String deliveryNoteRef;

    @NotEmpty(message = "At least one item required")
    private List<DispatchItem> items;

    @Data
    public static class DispatchItem {
        @NotNull
        private java.util.UUID productId;
        @NotNull
        private Integer quantity;
        private java.math.BigDecimal unitCost;
    }
}
