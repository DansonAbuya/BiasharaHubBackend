package com.biasharahub.controller;

import com.biasharahub.dto.request.CreateReviewRequest;
import com.biasharahub.dto.response.OrderReviewDto;
import com.biasharahub.security.AuthenticatedUser;
import com.biasharahub.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

/**
 * Order reviews: customers can leave a rating and comment after an order.
 */
@RestController
@RequestMapping("/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    public ResponseEntity<OrderReviewDto> createReview(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateReviewRequest request) {
        if (user == null) return ResponseEntity.status(401).build();
        OrderReviewDto review = reviewService.createReview(user, request);
        return ResponseEntity.ok(review);
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<OrderReviewDto> getReviewForOrder(@PathVariable UUID orderId) {
        Optional<OrderReviewDto> review = reviewService.getReviewForOrder(orderId);
        return review.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/business/{businessId}/rating")
    public ResponseEntity<Double> getBusinessRating(@PathVariable UUID businessId) {
        Double avg = reviewService.getAverageRatingForBusiness(businessId);
        return ResponseEntity.ok(avg != null ? avg : 0.0);
    }
}
