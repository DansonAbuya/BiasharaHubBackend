package com.biasharahub.service;

import com.biasharahub.entity.Order;
import com.biasharahub.entity.OrderItem;
import com.biasharahub.entity.User;
import com.biasharahub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Sends SMS notifications for seller events (e.g. new order) via Twilio.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SmsNotificationService {

    private final SmsClient smsClient;
    private final UserRepository userRepository;

    /**
     * Notify seller (owner + staff) via SMS when a new order is placed for their business.
     */
    public void notifySellerOrderCreated(Order order) {
        UUID businessId = getBusinessIdFromOrder(order);
        if (businessId == null) return;
        String customerName = order.getUser() != null && order.getUser().getName() != null
                ? order.getUser().getName()
                : "a customer";
        String body = String.format(
                "BiasharaHub: New order #%s from %s. Total: %s. Log in to process the order.",
                order.getOrderNumber(),
                customerName,
                order.getTotalAmount());
        for (User u : getSellerUsers(businessId)) {
            if (u.getPhone() != null && !u.getPhone().isBlank()) {
                smsClient.send(u.getPhone(), body);
            }
        }
    }

    private UUID getBusinessIdFromOrder(Order order) {
        if (order == null || order.getItems() == null || order.getItems().isEmpty()) return null;
        OrderItem first = order.getItems().get(0);
        if (first == null || first.getProduct() == null || first.getProduct().getBusinessId() == null) return null;
        return first.getProduct().getBusinessId();
    }

    private List<User> getSellerUsers(UUID businessId) {
        List<User> out = new ArrayList<>();
        List<User> owners = userRepository.findByRoleAndBusinessId("owner", businessId);
        List<User> staff = userRepository.findByRoleAndBusinessId("staff", businessId);
        if (owners != null) out.addAll(owners);
        if (staff != null) out.addAll(staff);
        return out;
    }
}
