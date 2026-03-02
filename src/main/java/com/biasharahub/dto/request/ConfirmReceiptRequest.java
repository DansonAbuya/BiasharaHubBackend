package com.biasharahub.dto.request;

import lombok.Data;

import java.util.Map;
import java.util.UUID;

/** Request for seller to confirm receipt; can override received quantity per item if different from supplier's stated. */
@Data
public class ConfirmReceiptRequest {
    /** Optional: itemId -> receivedQuantity. If not set for an item, uses supplier's stated quantity. */
    private Map<UUID, Integer> receivedQuantities;
}
