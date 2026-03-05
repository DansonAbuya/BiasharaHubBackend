package com.biasharahub.service;

import com.biasharahub.entity.User;
import com.biasharahub.repository.ExpenseRepository;
import com.biasharahub.repository.OrderItemRepository;
import com.biasharahub.repository.OrderRepository;
import com.biasharahub.repository.ShipmentRepository;
import com.biasharahub.repository.SupplierDeliveryRepository;
import com.biasharahub.repository.UserRepository;
import com.biasharahub.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

/**
 * Business insights: periodic profit/loss, product performance, staff performance, and filters.
 * Periods: daily, weekly, monthly, quarterly, yearly; optional filter by product.
 */
@Service
@RequiredArgsConstructor
public class BusinessInsightsService {

    public static final String PERIOD_DAY = "DAY";
    public static final String PERIOD_WEEK = "WEEK";
    public static final String PERIOD_MONTH = "MONTH";
    public static final String PERIOD_QUARTER = "QUARTER";
    public static final String PERIOD_YEAR = "YEAR";
    public static final String PERIOD_CUSTOM = "CUSTOM";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ExpenseRepository expenseRepository;
    private final SupplierDeliveryRepository supplierDeliveryRepository;
    private final ShipmentRepository shipmentRepository;
    private final UserRepository userRepository;

    private static final ZoneId ZONE = ZoneId.systemDefault();

    /**
     * Compute date range from period type and optional from/to.
     * For CUSTOM, from and to must be provided.
     */
    public LocalDate[] resolveDateRange(String period, LocalDate fromParam, LocalDate toParam) {
        LocalDate today = LocalDate.now(ZONE);
        LocalDate from;
        LocalDate to = today;

        switch (period != null ? period.toUpperCase() : PERIOD_MONTH) {
            case PERIOD_DAY:
                from = today;
                break;
            case PERIOD_WEEK:
                from = today.minusWeeks(1);
                break;
            case PERIOD_MONTH:
                from = today.minusMonths(1);
                break;
            case PERIOD_QUARTER:
                from = today.minusMonths(3);
                break;
            case PERIOD_YEAR:
                from = today.minusYears(1);
                break;
            case PERIOD_CUSTOM:
                if (fromParam == null || toParam == null) {
                    from = today.minusMonths(1);
                } else {
                    from = fromParam;
                    to = toParam;
                    if (from.isAfter(to)) {
                        LocalDate t = from;
                        from = to;
                        to = t;
                    }
                }
                break;
            default:
                from = today.minusMonths(1);
        }

        return new LocalDate[]{from, to};
    }

    /**
     * Full business insights for an owner: P&L, product performance, staff performance, period breakdown.
     * @param productId optional; when set, revenue and product performance are filtered to this product.
     */
    public Map<String, Object> getInsights(AuthenticatedUser user, String period, LocalDate fromParam, LocalDate toParam, UUID productId) {
        User u = userRepository.findById(user.userId()).orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (u.getBusinessId() == null) {
            return emptyInsights(period, fromParam, toParam);
        }

        LocalDate[] range = resolveDateRange(period, fromParam, toParam);
        LocalDate from = range[0];
        LocalDate to = range[1];

        Instant fromInstant = from.atStartOfDay(ZONE).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(ZONE).toInstant();

        UUID businessId = u.getBusinessId();

        // Revenue: all or filtered by product
        BigDecimal revenue;
        if (productId != null) {
            revenue = orderItemRepository.sumRevenueByBusinessIdAndProductIdAndDateRange(businessId, productId, fromInstant, toInstant);
            if (revenue == null) revenue = BigDecimal.ZERO;
        } else {
            revenue = orderItemRepository.sumRevenueByBusinessIdAndDateRange(businessId, fromInstant, toInstant);
            if (revenue == null) revenue = BigDecimal.ZERO;
        }

        // Expenses (period total; no per-product allocation)
        BigDecimal expenses = expenseRepository.sumAmountByBusinessIdAndDateRange(businessId, from, to);
        if (expenses == null) expenses = BigDecimal.ZERO;

        // Profit / Loss
        BigDecimal profitLoss = revenue.subtract(expenses);

        // Order count (when product filter set, we don't have order count for that product easily; keep total)
        long orderCount = orderRepository.countDeliveredOrdersByBusinessIdAndDateRange(businessId, fromInstant, toInstant);

        // Product performance: all products or single product
        List<Map<String, Object>> productPerformance = new ArrayList<>();
        for (Object[] row : orderItemRepository.findProductRevenueByBusinessIdAndDateRange(businessId, fromInstant, toInstant)) {
            UUID rowProductId = row[0] != null ? (UUID) row[0] : null;
            if (productId != null && !productId.equals(rowProductId)) continue;
            productPerformance.add(Map.<String, Object>of(
                    "productId", row[0] != null ? row[0].toString() : "",
                    "productName", row[1] != null ? row[1].toString() : "Unknown",
                    "category", row[2] != null ? row[2].toString() : "Uncategorized",
                    "quantitySold", row[3] != null ? ((Number) row[3]).longValue() : 0,
                    "revenue", row[4] != null ? ((BigDecimal) row[4]).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO
            ));
        }

        // Category performance (exclude when filtering by product to avoid confusion)
        List<Map<String, Object>> categoryPerformance = new ArrayList<>();
        if (productId == null) {
            for (Object[] row : orderItemRepository.findCategoryRevenueByBusinessIdAndDateRange(businessId, fromInstant, toInstant)) {
                categoryPerformance.add(Map.of(
                        "category", row[0] != null ? row[0].toString() : "Uncategorized",
                        "revenue", row[1] != null ? ((BigDecimal) row[1]).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO
                ));
            }
        }

        // Staff performance: expenses logged + deliveries received
        List<Map<String, Object>> staffPerformance = buildStaffPerformance(businessId, from, to, fromInstant, toInstant);

        // Period breakdown (optionally by product when productId set)
        List<Map<String, Object>> periodBreakdown = buildPeriodBreakdown(businessId, from, to, fromInstant, toInstant, productId);

        Map<String, Object> result = new HashMap<>();
        result.put("period", period);
        result.put("from", from.toString());
        result.put("to", to.toString());
        result.put("productId", productId != null ? productId.toString() : null);
        result.put("revenue", revenue.setScale(2, RoundingMode.HALF_UP));
        result.put("expenses", expenses.setScale(2, RoundingMode.HALF_UP));
        result.put("profitLoss", profitLoss.setScale(2, RoundingMode.HALF_UP));
        result.put("orderCount", orderCount);
        result.put("averageOrderValue", orderCount > 0
                ? revenue.divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        result.put("productPerformance", productPerformance);
        result.put("categoryPerformance", categoryPerformance);
        result.put("staffPerformance", staffPerformance);
        result.put("periodBreakdown", periodBreakdown);
        result.put("currency", "KES");
        return result;
    }

    private List<Map<String, Object>> buildStaffPerformance(UUID businessId, LocalDate from, LocalDate to,
                                                            Instant fromInstant, Instant toInstant) {
        Map<String, Map<String, Object>> byUser = new LinkedHashMap<>();
        for (Object[] row : expenseRepository.findStaffExpenseActivityByBusinessIdAndDateRange(businessId, from, to)) {
            String userId = row[0] != null ? row[0].toString() : "";
            byUser.put(userId, new LinkedHashMap<>(Map.of(
                    "userId", userId,
                    "name", row[1] != null ? row[1].toString() : "Unknown",
                    "expenseCount", row[2] != null ? ((Number) row[2]).intValue() : 0,
                    "totalExpensesLogged", row[3] != null ? ((BigDecimal) row[3]).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO,
                    "deliveriesReceivedCount", 0,
                    "shipmentsCreatedCount", 0
            )));
        }
        for (Object[] row : supplierDeliveryRepository.findDeliveriesReceivedByUserByBusinessIdAndDateRange(businessId, fromInstant, toInstant)) {
            String userId = row[0] != null ? row[0].toString() : "";
            byUser.computeIfAbsent(userId, k -> new LinkedHashMap<>(Map.of(
                    "userId", userId,
                    "name", row[1] != null ? row[1].toString() : "Unknown",
                    "expenseCount", 0,
                    "totalExpensesLogged", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    "deliveriesReceivedCount", 0,
                    "shipmentsCreatedCount", 0
            )));
            Map<String, Object> m = byUser.get(userId);
            m.put("deliveriesReceivedCount", row[2] != null ? ((Number) row[2]).intValue() : 0);
        }
        for (Object[] row : shipmentRepository.findShipmentsCreatedByUserByBusinessIdAndDateRange(businessId, fromInstant, toInstant)) {
            String userId = row[0] != null ? row[0].toString() : "";
            byUser.computeIfAbsent(userId, k -> new LinkedHashMap<>(Map.of(
                    "userId", userId,
                    "name", row[1] != null ? row[1].toString() : "Unknown",
                    "expenseCount", 0,
                    "totalExpensesLogged", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    "deliveriesReceivedCount", 0,
                    "shipmentsCreatedCount", 0
            )));
            Map<String, Object> m = byUser.get(userId);
            m.put("shipmentsCreatedCount", row[2] != null ? ((Number) row[2]).intValue() : 0);
        }
        return new ArrayList<>(byUser.values());
    }

    private Map<String, Object> emptyInsights(String period, LocalDate from, LocalDate to) {
        Map<String, Object> r = new HashMap<>();
        r.put("period", period != null ? period : PERIOD_MONTH);
        r.put("from", from != null ? from.toString() : LocalDate.now().toString());
        r.put("to", to != null ? to.toString() : LocalDate.now().toString());
        r.put("productId", (Object) null);
        r.put("revenue", BigDecimal.ZERO);
        r.put("expenses", BigDecimal.ZERO);
        r.put("profitLoss", BigDecimal.ZERO);
        r.put("orderCount", 0L);
        r.put("averageOrderValue", BigDecimal.ZERO);
        r.put("productPerformance", List.<Map<String, Object>>of());
        r.put("categoryPerformance", List.<Map<String, Object>>of());
        r.put("staffPerformance", List.<Map<String, Object>>of());
        r.put("periodBreakdown", List.<Map<String, Object>>of());
        r.put("currency", "KES");
        return r;
    }

    private List<Map<String, Object>> buildPeriodBreakdown(UUID businessId, LocalDate from, LocalDate to,
                                                           Instant fromInstant, Instant toInstant, UUID productId) {
        List<Map<String, Object>> breakdown = new ArrayList<>();
        long days = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;

        if (days <= 31) {
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                Instant start = d.atStartOfDay(ZONE).toInstant();
                Instant end = d.plusDays(1).atStartOfDay(ZONE).toInstant();
                BigDecimal rev = productId != null
                        ? orderItemRepository.sumRevenueByBusinessIdAndProductIdAndDateRange(businessId, productId, start, end)
                        : orderItemRepository.sumRevenueByBusinessIdAndDateRange(businessId, start, end);
                BigDecimal exp = expenseRepository.sumAmountByBusinessIdAndDateRange(businessId, d, d);
                breakdown.add(Map.of(
                        "label", d.toString(),
                        "revenue", rev != null ? rev.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO,
                        "expenses", exp != null ? exp.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO,
                        "profitLoss", (rev != null ? rev : BigDecimal.ZERO).subtract(exp != null ? exp : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
                ));
            }
        } else if (days <= 93) {
            LocalDate d = from;
            while (!d.isAfter(to)) {
                LocalDate weekEnd = d.plusDays(6);
                if (weekEnd.isAfter(to)) weekEnd = to;
                Instant start = d.atStartOfDay(ZONE).toInstant();
                Instant end = weekEnd.plusDays(1).atStartOfDay(ZONE).toInstant();
                BigDecimal rev = productId != null
                        ? orderItemRepository.sumRevenueByBusinessIdAndProductIdAndDateRange(businessId, productId, start, end)
                        : orderItemRepository.sumRevenueByBusinessIdAndDateRange(businessId, start, end);
                BigDecimal exp = expenseRepository.sumAmountByBusinessIdAndDateRange(businessId, d, weekEnd);
                breakdown.add(Map.of(
                        "label", d + " – " + weekEnd,
                        "revenue", rev != null ? rev.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO,
                        "expenses", exp != null ? exp.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO,
                        "profitLoss", (rev != null ? rev : BigDecimal.ZERO).subtract(exp != null ? exp : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
                ));
                d = weekEnd.plusDays(1);
            }
        } else {
            YearMonth ym = YearMonth.from(from);
            YearMonth endYm = YearMonth.from(to);
            while (!ym.isAfter(endYm)) {
                LocalDate monthStart = ym.atDay(1);
                LocalDate monthEnd = ym.atEndOfMonth();
                if (monthStart.isBefore(from)) monthStart = from;
                if (monthEnd.isAfter(to)) monthEnd = to;
                Instant start = monthStart.atStartOfDay(ZONE).toInstant();
                Instant end = monthEnd.plusDays(1).atStartOfDay(ZONE).toInstant();
                BigDecimal rev = productId != null
                        ? orderItemRepository.sumRevenueByBusinessIdAndProductIdAndDateRange(businessId, productId, start, end)
                        : orderItemRepository.sumRevenueByBusinessIdAndDateRange(businessId, start, end);
                BigDecimal exp = expenseRepository.sumAmountByBusinessIdAndDateRange(businessId, monthStart, monthEnd);
                breakdown.add(Map.of(
                        "label", ym.toString(),
                        "revenue", rev != null ? rev.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO,
                        "expenses", exp != null ? exp.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO,
                        "profitLoss", (rev != null ? rev : BigDecimal.ZERO).subtract(exp != null ? exp : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
                ));
                ym = ym.plusMonths(1);
            }
        }
        return breakdown;
    }
}
