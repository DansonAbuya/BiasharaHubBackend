package com.biasharahub.repository;

import com.biasharahub.entity.Order;
import com.biasharahub.entity.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {

    List<Shipment> findByOrder(Order order);

    /** Staff performance: shipments created by user in date range (order must contain business products). */
    @Query("""
        SELECT s.createdBy.userId, s.createdBy.name, COUNT(s)
        FROM Shipment s JOIN s.order o JOIN o.items i JOIN i.product p
        WHERE p.businessId = :businessId
        AND s.createdBy IS NOT NULL
        AND s.createdAt >= :from
        AND s.createdAt < :toExclusive
        GROUP BY s.createdBy.userId, s.createdBy.name
        ORDER BY COUNT(s) DESC
        """)
    List<Object[]> findShipmentsCreatedByUserByBusinessIdAndDateRange(
            @Param("businessId") UUID businessId,
            @Param("from") Instant from,
            @Param("toExclusive") Instant toExclusive);


    /** Shipments for orders in the given set (for seller: shipments for their business's orders). */
    List<Shipment> findByOrder_OrderIdIn(List<UUID> orderIds);

    /** Find by order id (avoids proxy/context issues when creating shipment after confirm). */
    List<Shipment> findByOrder_OrderId(UUID orderId);

    Optional<Shipment> findByTrackingNumber(String trackingNumber);

    @Query("SELECT o.orderId FROM Shipment s JOIN s.order o WHERE s.shipmentId = :shipmentId")
    Optional<UUID> findOrderIdByShipmentId(@Param("shipmentId") UUID shipmentId);

    @Query("SELECT s FROM Shipment s JOIN s.order o WHERE ((s.assignedCourier IS NOT NULL AND s.assignedCourier.userId = :courierUserId) OR s.riderPhone = :phone1 OR s.riderPhone = :phone2) AND s.status NOT IN ('DELIVERED', 'COLLECTED') ORDER BY s.createdAt DESC")
    List<Shipment> findByAssignedCourierOrRiderPhone(@Param("courierUserId") UUID courierUserId, @Param("phone1") String phone1, @Param("phone2") String phone2);

    @Query("SELECT s FROM Shipment s JOIN s.order o WHERE ((s.assignedCourier IS NOT NULL AND s.assignedCourier.userId = :courierUserId) OR s.riderPhone = :phone1 OR s.riderPhone = :phone2) ORDER BY s.createdAt DESC")
    List<Shipment> findAllByAssignedCourierOrRiderPhone(@Param("courierUserId") UUID courierUserId, @Param("phone1") String phone1, @Param("phone2") String phone2);
}
