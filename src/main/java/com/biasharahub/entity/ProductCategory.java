package com.biasharahub.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "product_categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductCategory {

    @Id
    @Column(name = "category_id")
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID categoryId;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "display_order")
    @Builder.Default
    private Integer displayOrder = 0;
}
