package com.biasharahub.repository;

import com.biasharahub.entity.OwnerVerificationDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OwnerVerificationDocumentRepository extends JpaRepository<OwnerVerificationDocument, UUID> {

    List<OwnerVerificationDocument> findByUserUserIdOrderByUploadedAtDesc(UUID userId);
}
