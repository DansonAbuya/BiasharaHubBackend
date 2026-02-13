package com.biasharahub.controller;

import com.biasharahub.dto.request.PayoutRequest;
import com.biasharahub.dto.response.PayoutDto;
import com.biasharahub.service.PayoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Payout requests from tenant wallet. Requires X-Tenant-ID header and allowed role.
 */
@RestController
@RequestMapping("/payouts")
@RequiredArgsConstructor
public class PayoutController {

    private final PayoutService payoutService;

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF', 'SUPER_ADMIN', 'ASSISTANT_ADMIN')")
    public ResponseEntity<?> requestPayout(@Valid @RequestBody PayoutRequest request) {
        try {
            PayoutDto dto = payoutService.requestPayout(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(dto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF', 'SUPER_ADMIN', 'ASSISTANT_ADMIN')")
    public ResponseEntity<?> listPayouts() {
        try {
            return ResponseEntity.ok(payoutService.listPayoutsForCurrentTenant());
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
