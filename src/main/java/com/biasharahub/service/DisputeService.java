package com.biasharahub.service;

import com.biasharahub.dto.response.DisputeDto;
import com.biasharahub.entity.*;
import com.biasharahub.repository.DisputeRepository;
import com.biasharahub.repository.OrderRepository;
import com.biasharahub.repository.UserRepository;
import com.biasharahub.security.AuthenticatedUser;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Trust & Safety: dispute workflow. Create dispute, seller response, admin resolve with optional strike.
 * Strike policy: late_shipping=1, wrong_item=2, fraud=3. 3 strikes=suspended, 5=permanent ban.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DisputeService {

    private static final String STATUS_OPEN = "open";
    private static final String STATUS_SELLER_RESPONDED = "seller_responded";
    private static final String STATUS_UNDER_REVIEW = "under_review";
    private static final String STATUS_RESOLVED = "resolved";
    private static final String RESOLUTION_CUSTOMER_FAVOR = "customer_favor";
    private static final int STRIKES_SUSPEND = 3;
    private static final int STRIKES_BAN = 5;

    private final DisputeRepository disputeRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    @Transactional
    public Dispute createDispute(UUID orderId, UUID reporterUserId, String disputeType, String description, String deliveryProofUrl) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new IllegalArgumentException("Order not found"));
        User reporter = userRepository.findById(reporterUserId).orElseThrow(() -> new IllegalArgumentException("Reporter not found"));
        if (!disputeType.matches("late_shipping|wrong_item|fraud|other")) {
            disputeType = "other";
        }
        Dispute dispute = Dispute.builder()
                .order(order)
                .reporterUser(reporter)
                .disputeType(disputeType)
                .status(STATUS_OPEN)
                .description(description)
                .deliveryProofUrl(deliveryProofUrl)
                .build();
        return disputeRepository.save(dispute);
    }

    @Transactional
    public Dispute sellerRespond(UUID disputeId, UUID sellerUserId, String response) {
        Dispute dispute = disputeRepository.findById(disputeId).orElseThrow(() -> new IllegalArgumentException("Dispute not found"));
        if (!STATUS_OPEN.equals(dispute.getStatus())) {
            throw new IllegalStateException("Dispute is no longer open for response");
        }
        User seller = userRepository.findById(sellerUserId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!isSellerForOrder(seller, dispute.getOrder())) {
            throw new IllegalArgumentException("Only the seller for this order may respond");
        }
        dispute.setSellerResponse(response);
        dispute.setSellerRespondedAt(Instant.now());
        dispute.setStatus(STATUS_SELLER_RESPONDED);
        return disputeRepository.save(dispute);
    }

    @Transactional
    public Dispute resolveDispute(UUID disputeId, String resolution, String strikeReason, UUID resolvedByUserId) {
        Dispute dispute = disputeRepository.findById(disputeId).orElseThrow(() -> new IllegalArgumentException("Dispute not found"));
        if (STATUS_RESOLVED.equals(dispute.getStatus())) {
            throw new IllegalStateException("Dispute already resolved");
        }
        User resolver = userRepository.findById(resolvedByUserId).orElseThrow(() -> new IllegalArgumentException("Resolver not found"));
        if (!"super_admin".equalsIgnoreCase(resolver.getRole()) && !"assistant_admin".equalsIgnoreCase(resolver.getRole())) {
            throw new IllegalArgumentException("Only platform admin can resolve disputes");
        }
        dispute.setResolution(resolution);
        dispute.setResolvedAt(Instant.now());
        dispute.setResolvedByUser(resolver);
        dispute.setStatus(STATUS_RESOLVED);
        if (RESOLUTION_CUSTOMER_FAVOR.equals(resolution) && strikeReason != null && !strikeReason.isBlank()) {
            dispute.setStrikeReason(strikeReason.trim().toLowerCase());
            applyStrikeToSellerForOrder(dispute.getOrder(), strikeReason.trim().toLowerCase());
        }
        return disputeRepository.save(dispute);
    }

    private void applyStrikeToSellerForOrder(Order order, String reason) {
        User owner = getOwnerForOrder(order);
        if (owner == null) return;
        int add = 1;
        if ("wrong_item".equals(reason)) add = 2;
        else if ("fraud".equals(reason)) add = 3;
        int count = (owner.getStrikeCount() != null ? owner.getStrikeCount() : 0) + add;
        owner.setStrikeCount(count);
        Instant now = Instant.now();
        if (count >= STRIKES_BAN) {
            owner.setAccountStatus("banned");
            owner.setBannedAt(now);
            owner.setSuspendedAt(owner.getSuspendedAt() != null ? owner.getSuspendedAt() : now);
        } else if (count >= STRIKES_SUSPEND) {
            owner.setAccountStatus("suspended");
            if (owner.getSuspendedAt() == null) owner.setSuspendedAt(now);
        }
        userRepository.save(owner);
        log.info("Strike applied to seller {}: reason={}, new count={}, status={}", owner.getUserId(), reason, count, owner.getAccountStatus());
    }

    private User getOwnerForOrder(Order order) {
        if (order.getItems() == null || order.getItems().isEmpty()) return null;
        UUID businessId = order.getItems().get(0).getProduct().getBusinessId();
        if (businessId == null) return null;
        List<User> owners = userRepository.findByRoleAndBusinessId("owner", businessId);
        return owners.isEmpty() ? null : owners.get(0);
    }

    private boolean isSellerForOrder(User user, Order order) {
        if ("owner".equalsIgnoreCase(user.getRole()) && user.getBusinessId() != null) {
            if (order.getItems() == null || order.getItems().isEmpty()) return false;
            return order.getItems().stream().anyMatch(i -> user.getBusinessId().equals(i.getProduct().getBusinessId()));
        }
        if ("staff".equalsIgnoreCase(user.getRole()) && user.getBusinessId() != null) {
            if (order.getItems() == null || order.getItems().isEmpty()) return false;
            return order.getItems().stream().anyMatch(i -> user.getBusinessId().equals(i.getProduct().getBusinessId()));
        }
        return false;
    }

    public List<Dispute> findByOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .map(disputeRepository::findByOrderOrderByCreatedAtDesc)
                .orElse(List.of());
    }

    public List<Dispute> findAllByStatus(String status) {
        return disputeRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    public Optional<Dispute> findById(UUID disputeId) {
        return disputeRepository.findById(disputeId);
    }

    public DisputeDto toDto(Dispute d) {
        Order o = d.getOrder();
        User r = d.getReporterUser();
        return DisputeDto.builder()
                .id(d.getDisputeId())
                .orderId(o != null ? o.getOrderId() : null)
                .orderNumber(o != null ? o.getOrderNumber() : null)
                .reporterUserId(r != null ? r.getUserId() : null)
                .reporterName(r != null ? (r.getName() != null ? r.getName() : r.getEmail()) : null)
                .disputeType(d.getDisputeType())
                .status(d.getStatus())
                .description(d.getDescription())
                .deliveryProofUrl(d.getDeliveryProofUrl())
                .sellerResponse(d.getSellerResponse())
                .sellerRespondedAt(d.getSellerRespondedAt())
                .resolvedAt(d.getResolvedAt())
                .resolvedByUserId(d.getResolvedByUser() != null ? d.getResolvedByUser().getUserId() : null)
                .resolution(d.getResolution())
                .strikeReason(d.getStrikeReason())
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .build();
    }
}
