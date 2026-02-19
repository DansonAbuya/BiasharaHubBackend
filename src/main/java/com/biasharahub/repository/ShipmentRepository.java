package com.biasharahub.repository;

import com.biasharahub.entity.Order;
import com.biasharahub.entity.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {

    List<Shipment> findByOrder(Order order);

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
