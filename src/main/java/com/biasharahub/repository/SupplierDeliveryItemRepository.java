package com.biasharahub.repository;

import com.biasharahub.entity.SupplierDeliveryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SupplierDeliveryItemRepository extends JpaRepository<SupplierDeliveryItem, UUID> {

    @Query("SELECT i FROM SupplierDeliveryItem i " +
            "LEFT JOIN FETCH i.product " +
            "WHERE i.delivery.deliveryId = :deliveryId " +
            "ORDER BY i.createdAt ASC")
    List<SupplierDeliveryItem> findByDeliveryIdWithProduct(@Param("deliveryId") UUID deliveryId);

    /** Sum of quantities in PROCESSING deliveries per product, for customer visibility. */
    @Query("SELECT i.product.productId, COALESCE(SUM(i.quantity), 0) FROM SupplierDeliveryItem i " +
            "WHERE i.delivery.status = 'PROCESSING' AND i.product.productId IN :productIds " +
            "GROUP BY i.product.productId")
    List<Object[]> sumProcessingQuantityByProductIds(@Param("productIds") List<UUID> productIds);
}

