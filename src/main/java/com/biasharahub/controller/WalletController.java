package com.biasharahub.controller;

import com.biasharahub.dto.request.SetDefaultPayoutRequest;
import com.biasharahub.dto.response.DefaultPayoutDestinationDto;
import com.biasharahub.dto.response.WalletBalanceDto;
import com.biasharahub.service.PayoutService;
import com.biasharahub.service.TenantWalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * Tenant wallet balance and default payout destination for auto-payout. Requires X-Tenant-ID header and allowed role.
 */
@RestController
@RequestMapping("/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final TenantWalletService tenantWalletService;
    private final PayoutService payoutService;

    @GetMapping("/balance")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF', 'SUPER_ADMIN', 'ASSISTANT_ADMIN')")
    public ResponseEntity<WalletBalanceDto> getBalance() {
        BigDecimal balance = tenantWalletService.getCurrentTenantBalance().orElse(BigDecimal.ZERO);
        return ResponseEntity.ok(WalletBalanceDto.builder().balance(balance).build());
    }

    @GetMapping("/payout-destination")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF', 'SUPER_ADMIN', 'ASSISTANT_ADMIN')")
    public ResponseEntity<DefaultPayoutDestinationDto> getDefaultPayoutDestination() {
        DefaultPayoutDestinationDto dto = payoutService.getDefaultPayoutForCurrentTenant()
                .orElse(DefaultPayoutDestinationDto.builder().method(null).destinationMasked(null).build());
        return ResponseEntity.ok(dto);
    }

    @PutMapping("/payout-destination")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF', 'SUPER_ADMIN', 'ASSISTANT_ADMIN')")
    public ResponseEntity<?> setDefaultPayoutDestination(@Valid @RequestBody SetDefaultPayoutRequest request) {
        try {
            payoutService.setDefaultPayoutForCurrentTenant(request.getMethod(), request.getDestination());
            return ResponseEntity.ok().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
