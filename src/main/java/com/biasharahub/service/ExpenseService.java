package com.biasharahub.service;

import com.biasharahub.dto.request.CreateExpenseRequest;
import com.biasharahub.dto.response.ExpenseDto;
import com.biasharahub.entity.Expense;
import com.biasharahub.entity.User;
import com.biasharahub.repository.ExpenseRepository;
import com.biasharahub.repository.UserRepository;
import com.biasharahub.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;

    @Transactional
    public ExpenseDto create(AuthenticatedUser user, CreateExpenseRequest request) {
        User owner = userRepository.findById(user.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (owner.getBusinessId() == null) {
            throw new IllegalArgumentException("Business not set. Only owners/staff can add expenses.");
        }
        Expense expense = Expense.builder()
                .category(request.getCategory().trim())
                .amount(request.getAmount())
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .receiptReference(request.getReceiptReference() != null ? request.getReceiptReference().trim() : null)
                .expenseDate(request.getExpenseDate())
                .createdBy(owner)
                .build();
        expense = expenseRepository.save(expense);
        return toDto(expense);
    }

    public List<ExpenseDto> listByBusiness(AuthenticatedUser user) {
        User u = userRepository.findById(user.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (u.getBusinessId() == null) {
            return List.of();
        }
        return expenseRepository.findByBusinessIdOrderByExpenseDateDesc(u.getBusinessId())
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private ExpenseDto toDto(Expense e) {
        return ExpenseDto.builder()
                .id(e.getExpenseId())
                .category(e.getCategory())
                .amount(e.getAmount())
                .description(e.getDescription())
                .receiptReference(e.getReceiptReference())
                .expenseDate(e.getExpenseDate())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
