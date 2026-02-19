package com.biasharahub.controller;

import com.biasharahub.dto.request.CreateExpenseRequest;
import com.biasharahub.dto.response.ExpenseDto;
import com.biasharahub.security.AuthenticatedUser;
import com.biasharahub.service.ExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/expenses")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER', 'STAFF', 'SUPER_ADMIN', 'ASSISTANT_ADMIN')")
public class ExpenseController {

    private final ExpenseService expenseService;

    @PostMapping
    public ResponseEntity<ExpenseDto> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateExpenseRequest request) {
        try {
            ExpenseDto dto = expenseService.create(user, request);
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping
    public ResponseEntity<List<ExpenseDto>> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(expenseService.listByBusiness(user));
    }
}
