package com.biasharahub.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * BiasharaHub Services: predefined category for a service (e.g. Consulting, Repair, Training).
 * Provider selects a category when creating a service, then chooses online or physical delivery.
 */
@Entity
@Table(name = "service_categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceCategory {

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
