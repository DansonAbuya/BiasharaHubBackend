package com.biasharahub.repository;

import com.biasharahub.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    @Query("SELECT t.schemaName FROM Tenant t WHERE t.tenantId = :tenantId AND t.isActive = true")
    Optional<String> findSchemaByTenantId(@Param("tenantId") UUID tenantId);
}
