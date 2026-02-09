package com.biasharahub.service;

import com.biasharahub.config.TenantContext;
import com.biasharahub.entity.Tenant;
import com.biasharahub.entity.TenantWalletEntry;
import com.biasharahub.repository.TenantRepository;
import com.biasharahub.repository.TenantWalletEntryRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Handles credit/debit operations for the tenant wallet ledger.
 */
@Service
@RequiredArgsConstructor
public class TenantWalletService {

    private final TenantRepository tenantRepository;
    private final TenantWalletEntryRepository walletEntryRepository;

    /**
     * Platform commission rate applied to incoming payments (e.g. 0.1 = 10%).
     */
    @Value("${app.wallet.platform-commission-rate:0.1}")
    private BigDecimal platformCommissionRate;

    @Transactional
    public void recordIncomingPaymentForCurrentTenant(BigDecimal amount, String orderId, String paymentId) {
        Tenant tenant = resolveCurrentTenant().orElse(null);
        if (tenant == null || amount == null) {
            return;
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BigDecimal commission = amount.multiply(platformCommissionRate).setScale(2, BigDecimal.ROUND_HALF_UP);
        BigDecimal netToTenant = amount.subtract(commission);

        // Credit tenant with net amount
        TenantWalletEntry credit = TenantWalletEntry.builder()
                .tenant(tenant)
                .entryType("CREDIT")
                .amount(netToTenant)
                .commissionAmount(commission)
                .referenceId(orderId != null ? orderId : paymentId)
                .description("Customer payment credited to tenant wallet")
                .build();
        walletEntryRepository.save(credit);

        // Optional: separate entry for platform commission (for reporting)
        TenantWalletEntry commissionEntry = TenantWalletEntry.builder()
                .tenant(tenant)
                .entryType("COMMISSION")
                .amount(commission)
                .commissionAmount(commission)
                .referenceId(orderId != null ? orderId : paymentId)
                .description("Platform commission on payment")
                .build();
        walletEntryRepository.save(commissionEntry);
    }

    public Optional<BigDecimal> getCurrentTenantBalance() {
        return resolveCurrentTenant().map(walletEntryRepository::calculateBalance);
    }

    private Optional<Tenant> resolveCurrentTenant() {
        String schema = TenantContext.getTenantSchema();
        if (schema == null || schema.isBlank()) {
            return Optional.empty();
        }
        return tenantRepository.findBySchemaName(schema);
    }
}

