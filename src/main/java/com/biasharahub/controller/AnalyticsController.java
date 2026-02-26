package com.biasharahub.controller;

import com.biasharahub.repository.OrderRepository;
import com.biasharahub.repository.ProductRepository;
import com.biasharahub.repository.UserRepository;
import com.biasharahub.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAnalytics(@AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null) return ResponseEntity.status(401).build();
        String role = user.role() != null ? user.role().toLowerCase() : "";
        if (!"owner".equals(role) && !"staff".equals(role) && !"super_admin".equals(role) && !"assistant_admin".equals(role)) {
            return ResponseEntity.status(403).build();
        }

        UUID businessId = null;
        if ("owner".equals(role) || "staff".equals(role)) {
            businessId = userRepository.findById(user.userId())
                    .map(u -> u.getBusinessId())
                    .orElse(null);
        }

        if (businessId != null) {
            // Seller-scoped: stats for this business only
            BigDecimal totalRevenue = orderRepository.sumRevenueByBusinessId(businessId);
            long totalOrders = orderRepository.countOrdersContainingProductsByBusinessId(businessId);
            long pendingOrders = orderRepository.countPendingOrdersByBusinessId(businessId);
            BigDecimal avgOrderValue = totalOrders > 0
                    ? totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            List<Map<String, Object>> topProducts = productRepository.findByBusinessId(businessId).stream()
                    .limit(5)
                    .map(p -> (Map<String, Object>) Map.<String, Object>of(
                            "id", p.getProductId(),
                            "name", p.getName() != null ? p.getName() : "",
                            "quantity", p.getQuantity() != null ? p.getQuantity() : 0,
                            "price", p.getPrice() != null ? p.getPrice() : java.math.BigDecimal.ZERO
                    ))
                    .toList();
            Map<String, Object> analytics = new HashMap<>();
            analytics.put("totalOrders", totalOrders);
            analytics.put("totalRevenue", totalRevenue);
            analytics.put("pendingOrders", pendingOrders);
            analytics.put("averageOrderValue", avgOrderValue);
            analytics.put("topProducts", topProducts);
            return ResponseEntity.ok(analytics);
        }

        // Admin or no businessId: system-wide stats (legacy)
        BigDecimal totalRevenue = orderRepository.sumTotalRevenue();
        long totalOrders = orderRepository.count();
        long pendingOrders = orderRepository.countPendingOrders();
        BigDecimal avgOrderValue = totalOrders > 0
                ? totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        Map<String, Object> analytics = new HashMap<>();
        analytics.put("totalOrders", totalOrders);
        analytics.put("totalRevenue", totalRevenue);
        analytics.put("pendingOrders", pendingOrders);
        analytics.put("averageOrderValue", avgOrderValue);
        analytics.put("topProducts", productRepository.findAll().stream().limit(5)
                .map(p -> java.util.Map.<String, Object>of(
                        "id", p.getProductId(),
                        "name", p.getName() != null ? p.getName() : "",
                        "quantity", p.getQuantity() != null ? p.getQuantity() : 0,
                        "price", p.getPrice() != null ? p.getPrice() : java.math.BigDecimal.ZERO
                ))
                .toList());
        return ResponseEntity.ok(analytics);
    }
}
