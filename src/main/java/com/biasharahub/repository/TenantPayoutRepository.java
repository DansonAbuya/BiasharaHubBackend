package com.biasharahub.repository;

import com.biasharahub.entity.Tenant;
import com.biasharahub.entity.TenantPayout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantPayoutRepository extends JpaRepository<TenantPayout, UUID> {

    List<TenantPayout> findByTenantOrderByCreatedAtDesc(Tenant tenant);

    Optional<TenantPayout> findByExternalReference(String externalReference);
}

