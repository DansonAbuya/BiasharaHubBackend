package com.biasharahub.repository;

import com.biasharahub.entity.ServiceProviderDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ServiceProviderDocumentRepository extends JpaRepository<ServiceProviderDocument, UUID> {

    List<ServiceProviderDocument> findByUserUserIdOrderByUploadedAtDesc(UUID userId);

    @Modifying
    @Query("DELETE FROM ServiceProviderDocument d WHERE d.user.userId = :userId")
    void deleteByUserUserId(@Param("userId") UUID userId);
}
