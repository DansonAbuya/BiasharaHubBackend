package com.biasharahub.service;

import com.biasharahub.entity.*;
import com.biasharahub.repository.StockLedgerEntryRepository;
import com.biasharahub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Central place to record stock movements for auditability.
 */
@Service
@RequiredArgsConstructor
public class StockLedgerService {

    private final StockLedgerEntryRepository stockLedgerEntryRepository;
    private final UserRepository userRepository;

    @Transactional
    public void recordManualAdjustment(UUID businessId, Product product, int previousQty, int newQty, UUID performedByUserId, String note) {
        int change = newQty - previousQty;
        if (change == 0) return;
        StockLedgerEntry entry = StockLedgerEntry.builder()
                .businessId(businessId)
                .product(product)
                .changeQty(change)
                .previousQty(previousQty)
                .newQty(newQty)
                .entryType("MANUAL_ADJUSTMENT")
                .performedBy(performedByUserId != null ? userRepository.findById(performedByUserId).orElse(null) : null)
                .note(note)
                .build();
        stockLedgerEntryRepository.save(entry);
    }

    @Transactional
    public void recordSupplierReceive(UUID businessId, Product product, int previousQty, int newQty, Supplier supplier, SupplierDelivery delivery, UUID performedByUserId, String note) {
        int change = newQty - previousQty;
        if (change == 0) return;
        StockLedgerEntry entry = StockLedgerEntry.builder()
                .businessId(businessId)
                .product(product)
                .changeQty(change)
                .previousQty(previousQty)
                .newQty(newQty)
                .entryType("SUPPLIER_RECEIVED")
                .supplier(supplier)
                .delivery(delivery)
                .performedBy(performedByUserId != null ? userRepository.findById(performedByUserId).orElse(null) : null)
                .note(note)
                .build();
        stockLedgerEntryRepository.save(entry);
    }

    @Transactional
    public void recordOrderMovement(UUID businessId, Product product, int previousQty, int newQty, Order order, UUID performedByUserId, String entryType) {
        int change = newQty - previousQty;
        if (change == 0) return;
        StockLedgerEntry entry = StockLedgerEntry.builder()
                .businessId(businessId)
                .product(product)
                .changeQty(change)
                .previousQty(previousQty)
                .newQty(newQty)
                .entryType(entryType != null ? entryType : "ORDER_MOVEMENT")
                .order(order)
                .performedBy(performedByUserId != null ? userRepository.findById(performedByUserId).orElse(null) : null)
                .build();
        stockLedgerEntryRepository.save(entry);
    }
}

