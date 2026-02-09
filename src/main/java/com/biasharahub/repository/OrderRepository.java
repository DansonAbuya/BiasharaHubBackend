package com.biasharahub.repository;

import com.biasharahub.entity.Order;
import com.biasharahub.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    List<Order> findByUserOrderByOrderedAtDesc(User user);

    /** Orders for the given user ID only (avoids any cross-user data). */
    @Query("SELECT o FROM Order o WHERE o.user.userId = :userId ORDER BY o.orderedAt DESC")
    List<Order> findByUserIdOrderByOrderedAtDesc(@Param("userId") UUID userId);

    @Query("SELECT DISTINCT o FROM Order o JOIN o.items i JOIN i.product p WHERE p.businessId = :businessId ORDER BY o.orderedAt DESC")
    List<Order> findOrdersContainingProductsByBusinessId(UUID businessId);

    Optional<Order> findByOrderNumber(String orderNumber);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.orderStatus = 'delivered'")
    BigDecimal sumTotalRevenue();

    @Query("SELECT COUNT(o) FROM Order o WHERE o.orderStatus = 'pending'")
    long countPendingOrders();
}
