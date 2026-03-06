package com.biasharahub.repository;

import com.biasharahub.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    /** Fetch all products with images in one query (avoids LazyInitializationException when open-in-view is false). */
    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.images")
    List<Product> findAllWithImages();

    /** Fetch one product by id with images (avoids LazyInitializationException). */
    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.images WHERE p.productId = :id")
    Optional<Product> findByProductIdWithImages(@Param("id") UUID id);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.images WHERE p.category = :category")
    List<Product> findByCategory(@Param("category") String category);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.images WHERE p.businessId = :businessId")
    List<Product> findByBusinessId(@Param("businessId") UUID businessId);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.images WHERE p.businessId = :businessId AND p.category = :category")
    List<Product> findByBusinessIdAndCategory(@Param("businessId") UUID businessId, @Param("category") String category);

    /** For customer filter: products from any of these businesses (with images). */
    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.images WHERE p.businessId IN :businessIds")
    List<Product> findByBusinessIdIn(@Param("businessIds") Set<UUID> businessIds);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.images WHERE p.businessId IN :businessIds AND p.category = :category")
    List<Product> findByBusinessIdInAndCategory(@Param("businessIds") Set<UUID> businessIds, @Param("category") String category);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.images WHERE p.productId = :productId AND p.businessId = :businessId")
    Optional<Product> findByProductIdAndBusinessId(@Param("productId") UUID productId, @Param("businessId") UUID businessId);

    /** Find an existing subdivision of a parent product by name (for reuse across dispatches). */
    Optional<Product> findFirstByBusinessIdAndSourceProductIdAndNameIgnoreCase(
            UUID businessId, UUID sourceProductId, String name);

    /** List all subdivisions (customer-facing products) for a given parent product. */
    List<Product> findByBusinessIdAndSourceProductIdOrderByNameAsc(UUID businessId, UUID sourceProductId);

    /**
     * Products visible to customers for a single shop: exclude supplier-facing-only, and exclude
     * originals that have subdivisions (show subdivisions only, not the raw product).
     */
    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.images WHERE p.businessId = :businessId "
            + "AND p.supplierFacingOnly = false AND (p.sourceProductId IS NOT NULL OR "
            + "NOT EXISTS (SELECT 1 FROM Product p2 WHERE p2.sourceProductId = p.productId))")
    List<Product> findCustomerFacingByBusinessId(@Param("businessId") UUID businessId);

    /**
     * All products visible to customers (all shops): exclude supplier-facing-only, and exclude
     * originals that have subdivisions (show subdivisions only).
     */
    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.images WHERE p.supplierFacingOnly = false "
            + "AND (p.sourceProductId IS NOT NULL OR NOT EXISTS (SELECT 1 FROM Product p2 WHERE p2.sourceProductId = p.productId))")
    List<Product> findCustomerFacingAll();

    boolean existsByProductIdAndBusinessId(UUID productId, UUID businessId);
}
