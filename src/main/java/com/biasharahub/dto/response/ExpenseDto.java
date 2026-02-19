package com.biasharahub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseDto {
    private UUID id;
    private String category;
    private BigDecimal amount;
    private String description;
    private String receiptReference;
    private LocalDate expenseDate;
    private Instant createdAt;
}
