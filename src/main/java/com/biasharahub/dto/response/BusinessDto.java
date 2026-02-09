package com.biasharahub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Shop (business) info for marketplace; includes seller tier for categorisation. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessDto {
    private UUID id;           // businessId (shop id)
    private String name;       // shop/business name
    private String ownerName;  // owner display name
    private String sellerTier; // tier1, tier2, tier3 for grouping (Informal, Registered SME, Corporate)
}
