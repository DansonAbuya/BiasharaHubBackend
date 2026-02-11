package com.biasharahub.service;

import com.biasharahub.dto.request.CreateReviewRequest;
import com.biasharahub.dto.response.OrderReviewDto;
import com.biasharahub.entity.Order;
import com.biasharahub.entity.OrderReview;
import com.biasharahub.entity.User;
import com.biasharahub.repository.OrderRepository;
import com.biasharahub.repository.OrderReviewRepository;
import com.biasharahub.repository.UserRepository;
import com.biasharahub.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final OrderReviewRepository reviewRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    @Transactional
    @CacheEvict(cacheNames = {"orderReviews", "businessRatings"}, allEntries = true)
    public OrderReviewDto createReview(AuthenticatedUser currentUser, CreateReviewRequest request) {
        User reviewer = userRepository.findById(currentUser.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        if (!order.getUser().getUserId().equals(currentUser.userId())) {
            throw new IllegalArgumentException("You can only review your own orders");
        }
        if (reviewRepository.findByOrderOrderId(order.getOrderId()).isPresent()) {
            throw new IllegalArgumentException("You have already reviewed this order");
        }
        OrderReview review = OrderReview.builder()
                .order(order)
                .reviewer(reviewer)
                .rating(request.getRating())
                .comment(request.getComment())
                .build();
        review = reviewRepository.save(review);
        return toDto(review);
    }

    @Cacheable(
            cacheNames = "orderReviews",
            key = "T(com.biasharahub.config.TenantContext).getTenantSchema() + '|' + #orderId"
    )
    public Optional<OrderReviewDto> getReviewForOrder(UUID orderId) {
        return reviewRepository.findByOrderOrderId(orderId).map(this::toDto);
    }

    @Cacheable(
            cacheNames = "businessRatings",
            key = "T(com.biasharahub.config.TenantContext).getTenantSchema() + '|' + #businessId"
    )
    public Double getAverageRatingForBusiness(UUID businessId) {
        return reviewRepository.getAverageRatingByBusinessId(businessId);
    }

    private OrderReviewDto toDto(OrderReview r) {
        return OrderReviewDto.builder()
                .reviewId(r.getReviewId())
                .orderId(r.getOrder().getOrderId())
                .reviewerUserId(r.getReviewer().getUserId())
                .reviewerName(r.getReviewer().getName())
                .rating(r.getRating())
                .comment(r.getComment())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
