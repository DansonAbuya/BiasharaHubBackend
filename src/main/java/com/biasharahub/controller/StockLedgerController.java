package com.biasharahub.controller;

import com.biasharahub.dto.response.StockLedgerEntryDto;
import com.biasharahub.entity.StockLedgerEntry;
import com.biasharahub.entity.User;
import com.biasharahub.repository.StockLedgerEntryRepository;
import com.biasharahub.repository.UserRepository;
import com.biasharahub.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/stock-ledger")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class StockLedgerController {

    private final StockLedgerEntryRepository stockLedgerEntryRepository;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<StockLedgerEntryDto>> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) UUID productId) {
        if (user == null) return ResponseEntity.status(401).build();
        User u = userRepository.findById(user.userId()).orElse(null);
        if (u == null || u.getBusinessId() == null) return ResponseEntity.ok(List.of());
        UUID businessId = u.getBusinessId();

        List<StockLedgerEntry> entries = productId != null
                ? stockLedgerEntryRepository.findByBusinessIdAndProductIdWithDetails(businessId, productId)
                : stockLedgerEntryRepository.findByBusinessIdWithDetails(businessId);

        return ResponseEntity.ok(entries.stream().map(this::toDto).toList());
    }

    private StockLedgerEntryDto toDto(StockLedgerEntry e) {
        return StockLedgerEntryDto.builder()
                .id(e.getEntryId())
                .businessId(e.getBusinessId())
                .productId(e.getProduct() != null ? e.getProduct().getProductId() : null)
                .productName(e.getProduct() != null ? e.getProduct().getName() : null)
                .changeQty(e.getChangeQty())
                .previousQty(e.getPreviousQty())
                .newQty(e.getNewQty())
                .entryType(e.getEntryType())
                .supplierId(e.getSupplier() != null ? e.getSupplier().getSupplierId() : null)
                .supplierName(e.getSupplier() != null ? e.getSupplier().getName() : null)
                .deliveryId(e.getDelivery() != null ? e.getDelivery().getDeliveryId() : null)
                .orderId(e.getOrder() != null ? e.getOrder().getOrderId() : null)
                .performedByUserId(e.getPerformedBy() != null ? e.getPerformedBy().getUserId() : null)
                .performedByName(e.getPerformedBy() != null ? (e.getPerformedBy().getName() != null ? e.getPerformedBy().getName() : e.getPerformedBy().getEmail()) : null)
                .note(e.getNote())
                .createdAt(e.getCreatedAt())
                .build();
    }
}

