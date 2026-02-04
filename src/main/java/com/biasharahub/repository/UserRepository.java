package com.biasharahub.repository;

import com.biasharahub.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findByRoleAndBusinessId(String role, UUID businessId);

    /** Find owners whose business name contains the given string (for customer filter by business). */
    List<User> findByRoleIgnoreCaseAndBusinessNameContainingIgnoreCase(String role, String businessName);

    /** Find owners for dropdown (businessId, businessName, owner name). */
    List<User> findByRoleIgnoreCaseAndBusinessIdIsNotNullOrderByBusinessNameAsc(String role);
}
