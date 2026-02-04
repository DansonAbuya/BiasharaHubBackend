package com.biasharahub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Business / owner info for customer filter dropdown. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessDto {
    private UUID id;           // businessId
    private String name;       // business name
    private String ownerName;  // owner display name
}
