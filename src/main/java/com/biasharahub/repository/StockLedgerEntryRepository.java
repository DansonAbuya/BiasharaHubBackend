package com.biasharahub.repository;

import com.biasharahub.entity.StockLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface StockLedgerEntryRepository extends JpaRepository<StockLedgerEntry, UUID> {

    @Query("SELECT e FROM StockLedgerEntry e " +
            "LEFT JOIN FETCH e.product p " +
            "LEFT JOIN FETCH e.performedBy " +
            "LEFT JOIN FETCH e.supplier " +
            "LEFT JOIN FETCH e.delivery " +
            "LEFT JOIN FETCH e.order " +
            "WHERE e.businessId = :businessId " +
            "ORDER BY e.createdAt DESC")
    List<StockLedgerEntry> findByBusinessIdWithDetails(@Param("businessId") UUID businessId);

    @Query("SELECT e FROM StockLedgerEntry e " +
            "LEFT JOIN FETCH e.product p " +
            "LEFT JOIN FETCH e.performedBy " +
            "LEFT JOIN FETCH e.supplier " +
            "LEFT JOIN FETCH e.delivery " +
            "LEFT JOIN FETCH e.order " +
            "WHERE e.businessId = :businessId AND p.productId = :productId " +
            "ORDER BY e.createdAt DESC")
    List<StockLedgerEntry> findByBusinessIdAndProductIdWithDetails(
            @Param("businessId") UUID businessId,
            @Param("productId") UUID productId
    );
}

