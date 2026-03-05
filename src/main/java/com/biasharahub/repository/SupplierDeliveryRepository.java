package com.biasharahub.repository;

import com.biasharahub.entity.SupplierDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplierDeliveryRepository extends JpaRepository<SupplierDelivery, UUID> {

    List<SupplierDelivery> findByBusinessIdOrderByCreatedAtDesc(UUID businessId);

    List<SupplierDelivery> findByBusinessIdAndSupplier_SupplierIdOrderByCreatedAtDesc(UUID businessId, UUID supplierId);

    @Query("SELECT d FROM SupplierDelivery d " +
            "LEFT JOIN FETCH d.supplier " +
            "LEFT JOIN FETCH d.receivedBy " +
            "LEFT JOIN FETCH d.createdBy " +
            "WHERE d.deliveryId = :id")
    Optional<SupplierDelivery> findByIdWithParties(@Param("id") UUID id);

    boolean existsByPurchaseOrder_PurchaseOrderId(UUID purchaseOrderId);

    /** Staff performance: deliveries received by user (receivedBy) in date range, for insights. */
    @Query("""
        SELECT d.receivedBy.userId, d.receivedBy.name, COUNT(d)
        FROM SupplierDelivery d
        WHERE d.businessId = :businessId
        AND d.status = 'RECEIVED'
        AND d.receivedAt >= :from
        AND d.receivedAt < :toExclusive
        AND d.receivedBy IS NOT NULL
        GROUP BY d.receivedBy.userId, d.receivedBy.name
        ORDER BY COUNT(d) DESC
        """)
    List<Object[]> findDeliveriesReceivedByUserByBusinessIdAndDateRange(
            @Param("businessId") UUID businessId,
            @Param("from") Instant from,
            @Param("toExclusive") Instant toExclusive);
}

