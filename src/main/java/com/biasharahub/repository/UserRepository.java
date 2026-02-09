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

    /** Find verified owners with a business (for marketplace “shops” list — only these have visible stores). */
    List<User> findByRoleIgnoreCaseAndVerificationStatusAndBusinessIdIsNotNullOrderByBusinessNameAsc(String role, String verificationStatus);

    /** Find users by role (e.g. customers for staff/owner "order for" dropdown). */
    List<User> findByRoleIgnoreCaseOrderByNameAsc(String role);

    /** Find owners pending verification for admin review queue. */
    List<User> findByRoleIgnoreCaseAndVerificationStatusOrderByCreatedAtAsc(String role, String verificationStatus);
}
