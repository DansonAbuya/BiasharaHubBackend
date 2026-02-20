package com.biasharahub.repository;

import com.biasharahub.entity.ServiceOffering;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, UUID> {

    List<ServiceOffering> findByBusinessId(UUID businessId);

    List<ServiceOffering> findByBusinessIdAndIsActiveTrue(UUID businessId);

    @Query("SELECT s FROM ServiceOffering s LEFT JOIN FETCH s.serviceCategory WHERE s.businessId IN :businessIds AND (s.isActive = true OR :includeInactive = true)")
    List<ServiceOffering> findByBusinessIdIn(@Param("businessIds") Set<UUID> businessIds, @Param("includeInactive") boolean includeInactive);

    @Query("SELECT s FROM ServiceOffering s LEFT JOIN FETCH s.serviceCategory WHERE s.businessId IN :businessIds AND s.deliveryType = :deliveryType AND (s.isActive = true OR :includeInactive = true)")
    List<ServiceOffering> findByBusinessIdInAndDeliveryType(
            @Param("businessIds") Set<UUID> businessIds,
            @Param("deliveryType") String deliveryType,
            @Param("includeInactive") boolean includeInactive);

    @Query("SELECT s FROM ServiceOffering s LEFT JOIN FETCH s.serviceCategory WHERE s.businessId IN :businessIds AND s.category = :category AND (s.isActive = true OR :includeInactive = true)")
    List<ServiceOffering> findByBusinessIdInAndCategory(
            @Param("businessIds") Set<UUID> businessIds,
            @Param("category") String category,
            @Param("includeInactive") boolean includeInactive);

    @Query("SELECT s FROM ServiceOffering s LEFT JOIN FETCH s.serviceCategory WHERE s.businessId IN :businessIds AND s.serviceCategory.categoryId = :categoryId AND (s.isActive = true OR :includeInactive = true)")
    List<ServiceOffering> findByBusinessIdInAndCategoryId(
            @Param("businessIds") Set<UUID> businessIds,
            @Param("categoryId") UUID categoryId,
            @Param("includeInactive") boolean includeInactive);

    @Query("SELECT s FROM ServiceOffering s LEFT JOIN FETCH s.serviceCategory WHERE s.businessId IN :businessIds AND (s.isActive = true OR :includeInactive = true)")
    List<ServiceOffering> findByBusinessIdInWithCategory(
            @Param("businessIds") Set<UUID> businessIds,
            @Param("includeInactive") boolean includeInactive);

    @Query("SELECT s FROM ServiceOffering s LEFT JOIN FETCH s.serviceCategory WHERE s.businessId = :businessId")
    List<ServiceOffering> findByBusinessIdWithCategory(@Param("businessId") UUID businessId);

    @Query("SELECT s FROM ServiceOffering s LEFT JOIN FETCH s.serviceCategory WHERE s.serviceId = :serviceId")
    Optional<ServiceOffering> findByServiceIdWithCategory(@Param("serviceId") UUID serviceId);

    Optional<ServiceOffering> findByServiceIdAndBusinessId(UUID serviceId, UUID businessId);

    boolean existsByServiceIdAndBusinessId(UUID serviceId, UUID businessId);
}
