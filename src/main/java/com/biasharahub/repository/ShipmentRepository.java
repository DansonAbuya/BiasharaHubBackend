package com.biasharahub.repository;

import com.biasharahub.entity.Order;
import com.biasharahub.entity.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {

    List<Shipment> findByOrder(Order order);

    Optional<Shipment> findByTrackingNumber(String trackingNumber);
}
