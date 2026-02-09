package com.biasharahub.repository;

import com.biasharahub.entity.OrderReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface OrderReviewRepository extends JpaRepository<OrderReview, UUID> {

    Optional<OrderReview> findByOrderOrderId(UUID orderId);

    @Query("SELECT AVG(r.rating) FROM OrderReview r WHERE r.order IN (SELECT o FROM Order o JOIN o.items i WHERE i.product.businessId = :businessId)")
    Double getAverageRatingByBusinessId(UUID businessId);
}
