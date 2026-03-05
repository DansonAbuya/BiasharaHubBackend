package com.biasharahub.repository;

import com.biasharahub.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    /**
     * Item-level revenue (accurate for marketplace); excludes shipping allocated per-business.
     */
    @Query("SELECT COALESCE(SUM(i.quantity * i.priceAtOrder), 0) FROM OrderItem i JOIN i.order o " +
            "WHERE i.product.businessId = :businessId AND o.orderStatus = 'delivered' " +
            "AND o.orderedAt >= :from AND o.orderedAt < :toExclusive")
    BigDecimal sumRevenueByBusinessIdAndDateRange(
            @Param("businessId") UUID businessId,
            @Param("from") Instant from,
            @Param("toExclusive") Instant toExclusive);

    /**
     * Product-level revenue for a business in a date range (delivered orders only).
     * Returns: productId, productName, category, totalQtySold, totalRevenue.
     */
    @Query("""
        SELECT i.product.productId, i.product.name, i.product.category,
               SUM(i.quantity), SUM(i.quantity * i.priceAtOrder)
        FROM OrderItem i
        JOIN i.order o
        WHERE i.product.businessId = :businessId
        AND o.orderStatus = 'delivered'
        AND o.orderedAt >= :from AND o.orderedAt < :toExclusive
        GROUP BY i.product.productId, i.product.name, i.product.category
        ORDER BY SUM(i.quantity * i.priceAtOrder) DESC
        """)
    List<Object[]> findProductRevenueByBusinessIdAndDateRange(
            @Param("businessId") UUID businessId,
            @Param("from") Instant from,
            @Param("toExclusive") Instant toExclusive);

    /**
     * Category-level revenue for a business in a date range.
     */
    @Query("""
        SELECT COALESCE(i.product.category, 'Uncategorized'),
               SUM(i.quantity * i.priceAtOrder)
        FROM OrderItem i
        JOIN i.order o
        WHERE i.product.businessId = :businessId
        AND o.orderStatus = 'delivered'
        AND o.orderedAt >= :from AND o.orderedAt < :toExclusive
        GROUP BY i.product.category
        ORDER BY SUM(i.quantity * i.priceAtOrder) DESC
        """)
    List<Object[]> findCategoryRevenueByBusinessIdAndDateRange(
            @Param("businessId") UUID businessId,
            @Param("from") Instant from,
            @Param("toExclusive") Instant toExclusive);

    /** Revenue for a single product in date range (for product filter in insights). */
    @Query("SELECT COALESCE(SUM(i.quantity * i.priceAtOrder), 0) FROM OrderItem i JOIN i.order o " +
            "WHERE i.product.businessId = :businessId AND i.product.productId = :productId " +
            "AND o.orderStatus = 'delivered' AND o.orderedAt >= :from AND o.orderedAt < :toExclusive")
    BigDecimal sumRevenueByBusinessIdAndProductIdAndDateRange(
            @Param("businessId") UUID businessId,
            @Param("productId") UUID productId,
            @Param("from") Instant from,
            @Param("toExclusive") Instant toExclusive);
}
