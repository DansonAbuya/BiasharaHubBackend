package com.biasharahub.repository;

import com.biasharahub.entity.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    List<PurchaseOrder> findByBusinessIdOrderByCreatedAtDesc(UUID businessId);

    List<PurchaseOrder> findByBusinessIdAndSupplier_SupplierIdOrderByCreatedAtDesc(UUID businessId, UUID supplierId);
}

