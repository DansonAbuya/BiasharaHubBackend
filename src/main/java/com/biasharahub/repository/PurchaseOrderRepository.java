package com.biasharahub.repository;

import com.biasharahub.entity.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    List<PurchaseOrder> findByBusinessIdOrderByCreatedAtDesc(UUID businessId);

    List<PurchaseOrder> findByBusinessIdAndSupplier_SupplierIdOrderByCreatedAtDesc(UUID businessId, UUID supplierId);

    long countByBusinessId(UUID businessId);

    @Query("SELECT p FROM PurchaseOrder p LEFT JOIN FETCH p.items WHERE p.purchaseOrderId = :id")
    Optional<PurchaseOrder> findByIdWithItems(@Param("id") UUID id);
}

