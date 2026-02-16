package com.biasharahub.service;

import com.biasharahub.config.TenantContext;
import com.biasharahub.dto.request.PayoutRequest;
import com.biasharahub.dto.response.DefaultPayoutDestinationDto;
import com.biasharahub.dto.response.PayoutDto;
import com.biasharahub.entity.Order;
import com.biasharahub.entity.Tenant;
import com.biasharahub.entity.TenantPayout;
import com.biasharahub.repository.TenantPayoutRepository;
import com.biasharahub.repository.TenantRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Handles payout requests from tenant wallet: validation, ledger debit, and M-Pesa B2C initiation.
 * BANK_TRANSFER payouts are created as PENDING for manual processing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PayoutService {

    private static final String METHOD_MPESA = "MPESA";
    private static final String METHOD_BANK_TRANSFER = "BANK_TRANSFER";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PROCESSING = "PROCESSING";

    private static final BigDecimal MIN_PAYOUT_KES = new BigDecimal("10");

    private final TenantRepository tenantRepository;
    private final TenantPayoutRepository payoutRepository;
    private final TenantWalletService tenantWalletService;
    private final MpesaClient mpesaClient;

    @Value("${app.wallet.min-payout-kes:10}")
    private BigDecimal minPayoutKes = MIN_PAYOUT_KES;

    @Value("${app.wallet.platform-commission-rate:0.1}")
    private BigDecimal platformCommissionRate = new BigDecimal("0.1");

    /**
     * Request a payout for the current tenant. Validates balance, creates payout record, debits wallet,
     * and if method is MPESA and B2C is configured, initiates B2C and sets status to PROCESSING.
     */
    @Transactional
    public PayoutDto requestPayout(PayoutRequest request) {
        Tenant tenant = resolveCurrentTenant().orElseThrow(() -> new IllegalStateException("Tenant context required"));
        BigDecimal amount = request.getAmount();
        String method = normaliseMethod(request.getMethod());

        if (amount.compareTo(minPayoutKes) < 0) {
            throw new IllegalArgumentException("Minimum payout is " + minPayoutKes + " KES");
        }

        BigDecimal balance = tenantWalletService.getCurrentTenantBalance().orElse(BigDecimal.ZERO);
        if (balance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient wallet balance");
        }

        TenantPayout payout = TenantPayout.builder()
                .tenant(tenant)
                .method(method)
                .amount(amount)
                .destinationDetails(request.getDestinationDetails().trim())
                .status(STATUS_PENDING)
                .build();
        payout = payoutRepository.save(payout);

        String payoutIdStr = payout.getPayoutId().toString();
        tenantWalletService.recordDebitForCurrentTenantPayout(
                amount, payoutIdStr, "Payout to seller (" + method + ")");

        if (METHOD_MPESA.equals(method)) {
            String conversationId = mpesaClient.initiateB2C(
                    request.getDestinationDetails().trim(),
                    amount,
                    payoutIdStr,
                    "BiasharaHub payout");
            if (conversationId != null) {
                payout.setExternalReference(conversationId);
                payout.setStatus(STATUS_PROCESSING);
                payoutRepository.save(payout);
            }
        }
        // BANK_TRANSFER or MPESA when B2C not configured: remains PENDING

        return toDto(payout);
    }

    public List<PayoutDto> listPayoutsForCurrentTenant() {
        Tenant tenant = resolveCurrentTenant().orElseThrow(() -> new IllegalStateException("Tenant context required"));
        return payoutRepository.findByTenantOrderByCreatedAtDesc(tenant).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public Optional<DefaultPayoutDestinationDto> getDefaultPayoutForCurrentTenant() {
        return resolveCurrentTenant()
                .filter(t -> t.getDefaultPayoutDestination() != null && !t.getDefaultPayoutDestination().isBlank())
                .map(t -> DefaultPayoutDestinationDto.builder()
                        .method(normaliseMethod(t.getDefaultPayoutMethod()))
                        .destinationMasked(maskDestination(t.getDefaultPayoutDestination()))
                        .build());
    }

    @Transactional
    public void setDefaultPayoutForCurrentTenant(String method, String destination) {
        Tenant tenant = resolveCurrentTenant().orElseThrow(() -> new IllegalStateException("Tenant context required"));
        tenant.setDefaultPayoutMethod(normaliseMethod(method));
        tenant.setDefaultPayoutDestination(destination != null ? destination.trim() : null);
        tenantRepository.save(tenant);
    }

    private static String maskDestination(String destination) {
        if (destination == null || destination.isBlank()) return "••••••••";
        String digits = destination.replaceAll("[^0-9]", "");
        if (digits.length() >= 6) {
            return digits.substring(0, 4) + "***" + digits.substring(digits.length() - 2);
        }
        return "••••••••";
    }

    /**
     * Called when order delivery is confirmed (payout released). Automatically transfers the order's net amount
     * to the tenant's default payout destination (e.g. M-Pesa) if configured; otherwise no transfer is initiated.
     */
    @Transactional
    public void triggerAutoPayoutForOrder(Order order) {
        if (order == null || order.getTotalAmount() == null) return;
        Tenant tenant = resolveCurrentTenant().orElse(null);
        if (tenant == null) return;

        String destination = tenant.getDefaultPayoutDestination();
        String method = normaliseMethod(tenant.getDefaultPayoutMethod());
        if (destination == null || destination.isBlank()) {
            log.info("Auto-payout skipped for order {}: tenant has no default payout destination", order.getOrderId());
            return;
        }

        BigDecimal total = order.getTotalAmount();
        BigDecimal commission = total.multiply(platformCommissionRate).setScale(2, BigDecimal.ROUND_HALF_UP);
        BigDecimal netAmount = total.subtract(commission);
        if (netAmount.compareTo(BigDecimal.ZERO) <= 0) return;
        if (netAmount.compareTo(minPayoutKes) < 0) {
            log.debug("Auto-payout skipped for order {}: net amount {} below minimum {}", order.getOrderId(), netAmount, minPayoutKes);
            return;
        }

        BigDecimal balance = tenantWalletService.getCurrentTenantBalance().orElse(BigDecimal.ZERO);
        if (balance.compareTo(netAmount) < 0) {
            log.warn("Auto-payout skipped for order {}: insufficient wallet balance (need {}, have {})", order.getOrderId(), netAmount, balance);
            return;
        }

        TenantPayout payout = TenantPayout.builder()
                .tenant(tenant)
                .method(method)
                .amount(netAmount)
                .destinationDetails(destination.trim())
                .status(STATUS_PENDING)
                .build();
        payout = payoutRepository.save(payout);

        String payoutIdStr = payout.getPayoutId().toString();
        String description = "Auto-payout for order " + order.getOrderNumber();
        tenantWalletService.recordDebitForCurrentTenantPayout(netAmount, payoutIdStr, description);

        if (METHOD_MPESA.equals(method)) {
            String conversationId = mpesaClient.initiateB2C(
                    destination.trim(),
                    netAmount,
                    payoutIdStr,
                    "Order " + order.getOrderNumber());
            if (conversationId != null) {
                payout.setExternalReference(conversationId);
                payout.setStatus(STATUS_PROCESSING);
                payoutRepository.save(payout);
            }
        }
        log.info("Auto-payout initiated for order {}: {} KES to tenant default destination", order.getOrderId(), netAmount);
    }

    private Optional<Tenant> resolveCurrentTenant() {
        String schema = TenantContext.getTenantSchema();
        if (schema == null || schema.isBlank()) {
            return Optional.empty();
        }
        return tenantRepository.findBySchemaName(schema);
    }

    private String normaliseMethod(String method) {
        if (method == null) return METHOD_BANK_TRANSFER;
        String m = method.toUpperCase().trim();
        return METHOD_MPESA.equals(m) ? METHOD_MPESA : METHOD_BANK_TRANSFER;
    }

    /**
     * Idempotent update of payout status from M-Pesa B2C callback.
     * Only updates if status is still PENDING or PROCESSING.
     */
    @Transactional
    public void handleB2CResult(String conversationId, int resultCode, String resultDesc) {
        if (conversationId == null || conversationId.isBlank()) return;
        payoutRepository.findByExternalReference(conversationId).ifPresent(payout -> {
            if (!STATUS_PENDING.equals(payout.getStatus()) && !STATUS_PROCESSING.equals(payout.getStatus())) {
                return;
            }
            payout.setProcessedAt(Instant.now());
            if (resultCode == 0) {
                payout.setStatus("COMPLETED");
                payout.setFailureReason(null);
            } else {
                payout.setStatus("FAILED");
                payout.setFailureReason(resultDesc != null && !resultDesc.isBlank() ? resultDesc : "B2C failed");
            }
            payoutRepository.save(payout);
        });
    }

    private PayoutDto toDto(TenantPayout p) {
        return PayoutDto.builder()
                .id(p.getPayoutId())
                .amount(p.getAmount())
                .method(p.getMethod())
                .status(p.getStatus())
                .createdAt(p.getCreatedAt())
                .processedAt(p.getProcessedAt())
                .failureReason(p.getFailureReason())
                .build();
    }
}
