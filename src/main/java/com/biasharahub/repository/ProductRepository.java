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

    boolean existsByProductIdAndBusinessId(UUID productId, UUID businessId);
}
