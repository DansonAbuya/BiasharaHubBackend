package com.biasharahub.repository;

import com.biasharahub.entity.SupplierDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplierDeliveryRepository extends JpaRepository<SupplierDelivery, UUID> {

    List<SupplierDelivery> findByBusinessIdOrderByCreatedAtDesc(UUID businessId);

    @Query("SELECT d FROM SupplierDelivery d " +
            "LEFT JOIN FETCH d.supplier " +
            "LEFT JOIN FETCH d.receivedBy " +
            "LEFT JOIN FETCH d.createdBy " +
            "WHERE d.deliveryId = :id")
    Optional<SupplierDelivery> findByIdWithParties(@Param("id") UUID id);
}

