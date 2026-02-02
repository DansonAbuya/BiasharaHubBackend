package com.biasharahub.controller;

import com.biasharahub.dto.response.OrderDto;
import com.biasharahub.dto.response.OrderItemDto;
import com.biasharahub.entity.*;
import com.biasharahub.repository.OrderRepository;
import com.biasharahub.repository.ProductRepository;
import com.biasharahub.repository.UserRepository;
import com.biasharahub.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    @GetMapping
    public ResponseEntity<List<OrderDto>> listOrders(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "false") boolean all) {
        if (user == null) return ResponseEntity.status(401).build();
        User u = userRepository.findById(user.userId()).orElse(null);
        if (u == null) return ResponseEntity.status(401).build();
        List<Order> orders = "owner".equalsIgnoreCase(user.role()) || "staff".equalsIgnoreCase(user.role())
                ? orderRepository.findAll()
                : orderRepository.findByUserOrderByOrderedAtDesc(u);
        return ResponseEntity.ok(orders.stream().map(this::toDto).collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderDto> getOrder(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id) {
        if (user == null) return ResponseEntity.status(401).build();
        return orderRepository.findById(id)
                .filter(o -> canAccess(o, user))
                .map(o -> ResponseEntity.ok(toDto(o)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Transactional
    public ResponseEntity<OrderDto> createOrder(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody OrderDto dto) {
        if (user == null) return ResponseEntity.status(401).build();
        User u = userRepository.findById(user.userId()).orElse(null);
        if (u == null) return ResponseEntity.status(401).build();
        String orderNumber = "ORD-" + System.currentTimeMillis();
        BigDecimal total = BigDecimal.ZERO;
        Order order = Order.builder()
                .user(u)
                .orderNumber(orderNumber)
                .totalAmount(BigDecimal.ZERO)
                .orderStatus("pending")
                .shippingAddress(dto.getShippingAddress())
                .build();
        for (OrderItemDto item : dto.getItems()) {
            Product product = productRepository.findById(item.getProductId()).orElse(null);
            if (product == null) continue;
            BigDecimal price = item.getPrice() != null ? item.getPrice() : product.getPrice();
            int qty = item.getQuantity() != null ? item.getQuantity() : 1;
            BigDecimal subtotal = price.multiply(BigDecimal.valueOf(qty));
            total = total.add(subtotal);
            InventoryImage img = product.getImages().isEmpty() ? null : product.getImages().get(0);
            OrderItem oi = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .inventoryImage(img)
                    .quantity(qty)
                    .priceAtOrder(price)
                    .build();
            order.getItems().add(oi);
            product.setQuantity(product.getQuantity() - qty);
            productRepository.save(product);
        }
        order.setTotalAmount(total);
        order = orderRepository.save(order);
        return ResponseEntity.ok(toDto(order));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderDto> updateOrderStatus(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestParam String status) {
        if (user == null) return ResponseEntity.status(401).build();
        return orderRepository.findById(id)
                .filter(o -> "owner".equalsIgnoreCase(user.role()) || "staff".equalsIgnoreCase(user.role()))
                .map(o -> {
                    o.setOrderStatus(status);
                    orderRepository.save(o);
                    return ResponseEntity.ok(toDto(o));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private boolean canAccess(Order o, AuthenticatedUser user) {
        if (o.getUser().getUserId().equals(user.userId())) return true;
        return "owner".equalsIgnoreCase(user.role()) || "staff".equalsIgnoreCase(user.role());
    }

    private OrderDto toDto(Order o) {
        String paymentStatus = o.getPayments().stream()
                .anyMatch(p -> "completed".equals(p.getPaymentStatus())) ? "completed" : "pending";
        String paymentMethod = o.getPayments().stream()
                .filter(p -> "completed".equals(p.getPaymentStatus()))
                .findFirst()
                .map(Payment::getPaymentMethod)
                .orElse(null);
        return OrderDto.builder()
                .id(o.getOrderId())
                .orderId(o.getOrderNumber())
                .customerId(o.getUser().getUserId())
                .customerName(o.getUser().getName())
                .customerEmail(o.getUser().getEmail())
                .items(o.getItems().stream().map(oi -> OrderItemDto.builder()
                        .productId(oi.getProduct().getProductId())
                        .productName(oi.getProduct().getName())
                        .quantity(oi.getQuantity())
                        .price(oi.getPriceAtOrder())
                        .subtotal(oi.getPriceAtOrder().multiply(BigDecimal.valueOf(oi.getQuantity())))
                        .build()).toList())
                .total(o.getTotalAmount())
                .status(o.getOrderStatus())
                .paymentStatus(paymentStatus)
                .paymentMethod(paymentMethod)
                .createdAt(o.getOrderedAt())
                .updatedAt(o.getUpdatedAt())
                .shippingAddress(o.getShippingAddress())
                .build();
    }
}
