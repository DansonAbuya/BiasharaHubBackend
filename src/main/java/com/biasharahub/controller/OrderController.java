package com.biasharahub.controller;

import com.biasharahub.dto.request.CreateOrderRequest;
import com.biasharahub.dto.response.OrderDto;
import com.biasharahub.dto.response.OrderItemDto;
import com.biasharahub.entity.*;
import com.biasharahub.repository.OrderRepository;
import com.biasharahub.repository.PaymentRepository;
import com.biasharahub.repository.ProductRepository;
import com.biasharahub.repository.UserRepository;
import com.biasharahub.security.AuthenticatedUser;
import com.biasharahub.service.InAppNotificationService;
import com.biasharahub.service.OrderEventPublisher;
import com.biasharahub.service.SmsNotificationService;
import com.biasharahub.service.WhatsAppNotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    /** Low-stock threshold for seller alerts when stock drops after order. */
    private static final int LOW_STOCK_THRESHOLD = 10;

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;
    private final OrderEventPublisher orderEventPublisher;
    private final InAppNotificationService inAppNotificationService;
    private final WhatsAppNotificationService whatsAppNotificationService;
    private final SmsNotificationService smsNotificationService;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<OrderDto>> listOrders(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "false") boolean all) {
        if (user == null) return ResponseEntity.status(401).build();
        User u = userRepository.findById(user.userId()).orElse(null);
        if (u == null) return ResponseEntity.status(401).build();
        List<Order> orders;
        if ("owner".equalsIgnoreCase(user.role()) || "staff".equalsIgnoreCase(user.role())) {
            UUID businessId = u.getBusinessId();
            orders = (businessId != null)
                    ? orderRepository.findOrdersContainingProductsByBusinessId(businessId)
                    : List.of();
        } else {
            // Customer: filter strictly by authenticated user ID so only their orders are returned
            orders = orderRepository.findByUserIdOrderByOrderedAtDesc(user.userId());
        }
        return ResponseEntity.ok(orders.stream().map(this::toDto).collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<OrderDto> getOrder(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id) {
        if (user == null) return ResponseEntity.status(401).build();
        User u = userRepository.findById(user.userId()).orElse(null);
        if (u == null) return ResponseEntity.status(401).build();
        return orderRepository.findById(id)
                .filter(o -> canAccess(o, u))
                .map(o -> ResponseEntity.ok(toDto(o)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> createOrder(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateOrderRequest request) {
        if (user == null) return ResponseEntity.status(401).build();
        User currentUser = userRepository.findById(user.userId()).orElse(null);
        if (currentUser == null) return ResponseEntity.status(401).build();

        User orderOwner = currentUser;
        if (request.getOrderForCustomerId() != null) {
            if (!"owner".equalsIgnoreCase(currentUser.getRole()) && !"staff".equalsIgnoreCase(currentUser.getRole())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                        java.util.Map.of("error", "Only staff or owners can place an order on behalf of a customer"));
            }
            User customer = userRepository.findById(request.getOrderForCustomerId()).orElse(null);
            if (customer == null || !"customer".equalsIgnoreCase(customer.getRole())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                        java.util.Map.of("error", "Invalid or unknown customer"));
            }
            orderOwner = customer;
        }

        List<String> errors = new ArrayList<>();
        for (CreateOrderRequest.OrderItemRequest item : request.getItems()) {
            Product product = productRepository.findById(item.getProductId()).orElse(null);
            if (product == null) {
                errors.add("Product " + item.getProductId() + " not found.");
                continue;
            }
            int available = product.getQuantity() != null ? product.getQuantity() : 0;
            int qty = item.getQuantity() != null && item.getQuantity() > 0 ? item.getQuantity() : 1;
            if (available < qty) {
                errors.add("Insufficient stock for '" + product.getName() + "': requested " + qty + ", available " + available + ".");
            }
        }
        if (!errors.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    java.util.Map.of("error", "Validation failed", "details", errors));
        }

        String orderNumber = "ORD-" + System.currentTimeMillis();
        BigDecimal total = BigDecimal.ZERO;

        String deliveryMode = request.getDeliveryMode() != null ? request.getDeliveryMode() : "SELLER_SELF";
        BigDecimal shippingFee = request.getShippingFee() != null ? request.getShippingFee() : BigDecimal.ZERO;
        Order order = Order.builder()
                .user(orderOwner)
                .orderNumber(orderNumber)
                .totalAmount(BigDecimal.ZERO)
                .orderStatus("pending")
                .shippingAddress(request.getShippingAddress())
                .deliveryMode(deliveryMode)
                .shippingFee(shippingFee)
                .build();
        for (CreateOrderRequest.OrderItemRequest item : request.getItems()) {
            Product product = productRepository.findById(item.getProductId()).orElseThrow();
            int qty = item.getQuantity() != null && item.getQuantity() > 0 ? item.getQuantity() : 1;
            BigDecimal price = product.getPrice();
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
        // Include shipping fee in total
        total = total.add(shippingFee);
        order.setTotalAmount(total);
        order = orderRepository.save(order);

        // Notify seller when any product is now low stock (in-app, WhatsApp, SMS) – once per product
        Set<UUID> lowStockNotified = new HashSet<>();
        for (OrderItem oi : order.getItems()) {
            Product p = oi.getProduct();
            if (p != null && p.getQuantity() != null && p.getQuantity() <= LOW_STOCK_THRESHOLD && lowStockNotified.add(p.getProductId())) {
                try { inAppNotificationService.notifySellerLowStock(p); } catch (Exception ignored) {}
                try { whatsAppNotificationService.notifySellerLowStock(p); } catch (Exception ignored) {}
                try { smsNotificationService.notifySellerLowStock(p); } catch (Exception ignored) {}
            }
        }

        String paymentMethod = request.getPaymentMethod() != null && "cash".equalsIgnoreCase(request.getPaymentMethod().trim())
                ? "Cash" : "M-Pesa";
        Payment payment = Payment.builder()
                .order(order)
                .user(orderOwner)
                .amount(total)
                .paymentStatus("pending")
                .paymentMethod(paymentMethod)
                .build();
        paymentRepository.save(payment);

        orderEventPublisher.orderCreated(order);

        return ResponseEntity.ok(toDto(order));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderDto> updateOrderStatus(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestParam String status) {
        if (user == null) return ResponseEntity.status(401).build();
        User u = userRepository.findById(user.userId()).orElse(null);
        if (u == null) return ResponseEntity.status(401).build();
        return orderRepository.findById(id)
                .filter(o -> canAccess(o, u))
                .filter(o -> "owner".equalsIgnoreCase(user.role()) || "staff".equalsIgnoreCase(user.role()))
                .map(o -> {
                    o.setOrderStatus(status);
                    if ("delivered".equalsIgnoreCase(status)) {
                        o.setDeliveredAt(java.time.Instant.now());
                    }
                    orderRepository.save(o);
                    return ResponseEntity.ok(toDto(o));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Allow a user to cancel an order as long as it has not been confirmed yet.
     * - Only pending orders can be cancelled.
     * - Only the customer (or staff/owner acting on behalf) can cancel orders they can access.
     * - Pending payments are marked as cancelled and inventory is restored.
     */
    @PatchMapping("/{id}/cancel")
    @Transactional
    public ResponseEntity<OrderDto> cancelOrder(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id) {
        if (user == null) return ResponseEntity.status(401).<OrderDto>build();
        User currentUser = userRepository.findById(user.userId()).orElse(null);
        if (currentUser == null) return ResponseEntity.status(401).<OrderDto>build();

        return orderRepository.findById(id)
                .filter(o -> canAccess(o, currentUser))
                .map(o -> {
                    if (!"pending".equalsIgnoreCase(o.getOrderStatus())) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).<OrderDto>build();
                    }

                    // Restore inventory for all items
                    for (OrderItem oi : o.getItems()) {
                        Product product = oi.getProduct();
                        if (product != null) {
                            Integer currentQty = product.getQuantity() != null ? product.getQuantity() : 0;
                            product.setQuantity(currentQty + oi.getQuantity());
                            productRepository.save(product);
                        }
                    }

                    // Mark any pending payments as cancelled
                    for (Payment p : o.getPayments()) {
                        if ("pending".equalsIgnoreCase(p.getPaymentStatus())) {
                            p.setPaymentStatus("cancelled");
                            paymentRepository.save(p);
                        }
                    }

                    o.setOrderStatus("cancelled");
                    orderRepository.save(o);
                    // Notify seller (owner + staff): in-app, WhatsApp, SMS
                    try { inAppNotificationService.notifySellerOrderCancelled(o); } catch (Exception ignored) {}
                    try { whatsAppNotificationService.notifySellerOrderCancelled(o); } catch (Exception ignored) {}
                    try { smsNotificationService.notifySellerOrderCancelled(o); } catch (Exception ignored) {}
                    return ResponseEntity.ok(toDto(o));
                })
                .orElse(ResponseEntity.status(404).<OrderDto>build());
    }

    private boolean canAccess(Order o, User currentUser) {
        if (currentUser.getUserId().equals(o.getUser().getUserId())) return true;
        if ("owner".equalsIgnoreCase(currentUser.getRole()) || "staff".equalsIgnoreCase(currentUser.getRole())) {
            UUID businessId = currentUser.getBusinessId();
            if (businessId == null) return false;
            return o.getItems().stream()
                    .anyMatch(i -> businessId.equals(i.getProduct().getBusinessId()));
        }
        return false;
    }

    private OrderDto toDto(Order o) {
        String paymentStatus = o.getPayments().stream()
                .anyMatch(p -> "completed".equals(p.getPaymentStatus())) ? "completed" : "pending";
        String paymentMethod = o.getPayments().stream()
                .filter(p -> "completed".equals(p.getPaymentStatus()))
                .findFirst()
                .map(Payment::getPaymentMethod)
                .orElse(o.getPayments().stream()
                        .filter(p -> "pending".equals(p.getPaymentStatus()))
                        .findFirst()
                        .map(Payment::getPaymentMethod)
                        .orElse(null));
        UUID paymentId = o.getPayments().stream()
                .filter(p -> "pending".equals(p.getPaymentStatus()))
                .findFirst()
                .map(Payment::getPaymentId)
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
                .paymentId(paymentId)
                .deliveryMode(o.getDeliveryMode())
                .shippingFee(o.getShippingFee())
                .createdAt(o.getOrderedAt())
                .updatedAt(o.getUpdatedAt())
                .shippingAddress(o.getShippingAddress())
                .build();
    }
}
