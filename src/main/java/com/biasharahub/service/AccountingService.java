package com.biasharahub.service;

import com.biasharahub.entity.User;
import com.biasharahub.repository.ExpenseRepository;
import com.biasharahub.repository.OrderRepository;
import com.biasharahub.repository.UserRepository;
import com.biasharahub.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Micro-accounting for KRA-ready reports: daily sales, expenses, P&L, export.
 */
@Service
@RequiredArgsConstructor
public class AccountingService {

    private final OrderRepository orderRepository;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;

    /**
     * Daily sales and expenses summary for a date range. KRA-ready format.
     */
    public Map<String, Object> getDailySummary(AuthenticatedUser user, LocalDate from, LocalDate to) {
        User u = userRepository.findById(user.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (u.getBusinessId() == null) {
            return Map.of("sales", List.of(), "expenses", List.of(), "totalSales", BigDecimal.ZERO,
                    "totalExpenses", BigDecimal.ZERO, "netIncome", BigDecimal.ZERO);
        }

        Instant fromInstant = from.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        BigDecimal totalSales = orderRepository.sumRevenueByBusinessIdAndDateRange(
                u.getBusinessId(), fromInstant, toInstant);
        BigDecimal totalExpenses = expenseRepository.sumAmountByBusinessIdAndDateRange(
                u.getBusinessId(), from, to);

        if (totalSales == null) totalSales = BigDecimal.ZERO;
        if (totalExpenses == null) totalExpenses = BigDecimal.ZERO;
        BigDecimal netIncome = totalSales.subtract(totalExpenses);

        List<Map<String, Object>> dailySales = new ArrayList<>();
        List<Map<String, Object>> dailyExpenses = expenseRepository.findByBusinessIdAndDateRange(
                u.getBusinessId(), from, to)
                .stream()
                .map(e -> Map.<String, Object>of(
                        "date", e.getExpenseDate().toString(),
                        "category", e.getCategory(),
                        "amount", e.getAmount(),
                        "description", e.getDescription() != null ? e.getDescription() : ""))
                .collect(Collectors.toList());

        return Map.of(
                "from", from.toString(),
                "to", to.toString(),
                "totalSales", totalSales.setScale(2, RoundingMode.HALF_UP),
                "totalExpenses", totalExpenses.setScale(2, RoundingMode.HALF_UP),
                "netIncome", netIncome.setScale(2, RoundingMode.HALF_UP),
                "dailyExpenses", dailyExpenses,
                "currency", "KES"
        );
    }

    /**
     * KRA-ready export: income statement format for date range.
     */
    public List<Map<String, String>> getKraExport(AuthenticatedUser user, LocalDate from, LocalDate to) {
        Map<String, Object> summary = getDailySummary(user, from, to);
        List<Map<String, String>> rows = new ArrayList<>();
        rows.add(Map.of("Item", "Report", "Amount", "BiasharaHub Income Summary"));
        rows.add(Map.of("Item", "Period", "Amount", from + " to " + to));
        rows.add(Map.of("Item", "Total Sales (KES)", "Amount", summary.get("totalSales").toString()));
        rows.add(Map.of("Item", "Total Expenses (KES)", "Amount", summary.get("totalExpenses").toString()));
        rows.add(Map.of("Item", "Net Income (KES)", "Amount", summary.get("netIncome").toString()));
        return rows;
    }
}
