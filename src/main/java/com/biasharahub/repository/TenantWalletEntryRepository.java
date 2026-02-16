package com.biasharahub.repository;

import com.biasharahub.entity.Tenant;
import com.biasharahub.entity.TenantWalletEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface TenantWalletEntryRepository extends JpaRepository<TenantWalletEntry, UUID> {

    List<TenantWalletEntry> findByTenantOrderByCreatedAtDesc(Tenant tenant);

    @Query("SELECT COALESCE(SUM(CASE WHEN e.entryType = 'CREDIT' THEN e.amount ELSE -e.amount END), 0) " +
           "FROM TenantWalletEntry e WHERE e.tenant = :tenant")
    BigDecimal calculateBalance(Tenant tenant);
}

