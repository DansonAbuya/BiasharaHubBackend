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

    Optional<Shipment> findByTrackingNumber(String trackingNumber);

    @Query("SELECT o.orderId FROM Shipment s JOIN s.order o WHERE s.shipmentId = :shipmentId")
    Optional<UUID> findOrderIdByShipmentId(@Param("shipmentId") UUID shipmentId);
}
