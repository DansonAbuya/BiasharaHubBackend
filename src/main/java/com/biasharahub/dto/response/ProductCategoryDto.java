package com.biasharahub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductCategoryDto implements Serializable {
    private static final long serialVersionUID = 1L;
    private UUID id;
    private String name;
    private Integer displayOrder;
}
