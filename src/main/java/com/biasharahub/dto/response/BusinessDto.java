package com.biasharahub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/** Shop (business) info for marketplace; includes seller tier for categorisation. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessDto implements Serializable {
    private static final long serialVersionUID = 1L;
    private UUID id;           // businessId (shop id)
    private String name;       // shop/business name
    private String ownerName;  // owner display name
    private String sellerTier; // tier1, tier2, tier3 for grouping (Informal, Registered SME, Corporate)
    /**
     * Public URL to this shop on the frontend (e.g. https://app.example.com/shops/{businessId}).
     * Computed by backend using app.frontend-url so admin and sellers can copy/share it.
     */
    private String shopUrl;
}
