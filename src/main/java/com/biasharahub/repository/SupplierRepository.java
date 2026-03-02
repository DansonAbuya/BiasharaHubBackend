package com.biasharahub.repository;

import com.biasharahub.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {
    List<Supplier> findByBusinessIdOrderByNameAsc(UUID businessId);

    /** Resolve supplier record for a supplier user (matched by email + businessId). */
    java.util.Optional<Supplier> findByEmailIgnoreCaseAndBusinessId(String email, UUID businessId);
}

