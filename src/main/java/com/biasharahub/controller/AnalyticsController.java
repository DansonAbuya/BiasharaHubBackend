package com.biasharahub.controller;

import com.biasharahub.repository.OrderRepository;
import com.biasharahub.repository.ProductRepository;
import com.biasharahub.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAnalytics(@AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null) return ResponseEntity.status(401).build();
        if (!"owner".equalsIgnoreCase(user.role()) && !"staff".equalsIgnoreCase(user.role())) {
            return ResponseEntity.status(403).build();
        }
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
        analytics.put("topProducts", productRepository.findAll().stream().limit(5).toList());
        return ResponseEntity.ok(analytics);
    }
}
