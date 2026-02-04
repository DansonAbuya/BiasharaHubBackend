package com.biasharahub.repository;

import com.biasharahub.entity.PasswordResetToken;
import com.biasharahub.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenAndExpiresAtAfter(String token, Instant now);

    void deleteByUser(User user);

    void deleteByExpiresAtBefore(Instant instant);
}
