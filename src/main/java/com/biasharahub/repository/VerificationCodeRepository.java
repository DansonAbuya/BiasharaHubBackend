package com.biasharahub.repository;

import com.biasharahub.entity.VerificationCode;
import com.biasharahub.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface VerificationCodeRepository extends JpaRepository<VerificationCode, UUID> {

    Optional<VerificationCode> findByUserAndVerificationCodeAndExpiresAtAfter(
            User user, String code, Instant now);

    void deleteByUser(User user);
}
