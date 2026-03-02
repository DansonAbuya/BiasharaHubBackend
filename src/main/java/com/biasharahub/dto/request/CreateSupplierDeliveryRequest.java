package com.biasharahub.dto.request;

import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class CreateSupplierDeliveryRequest {
    private UUID supplierId;
    private String deliveryNoteRef;
    private Instant deliveredAt;
}

