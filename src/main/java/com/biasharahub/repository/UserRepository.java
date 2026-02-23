package com.biasharahub.repository;

import com.biasharahub.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** Find owners verified as service providers (for marketplace services list). */
    List<User> findByRoleIgnoreCaseAndServiceProviderStatusAndBusinessIdIsNotNullOrderByBusinessNameAsc(String role, String serviceProviderStatus);

    /** Verified product-seller owners with active account only (for customer-facing shops; excludes disabled/suspended/banned). */
    @Query("SELECT u FROM User u WHERE LOWER(u.role) = LOWER(:role) AND LOWER(u.verificationStatus) = LOWER(:verificationStatus) AND u.businessId IS NOT NULL AND (u.accountStatus IS NULL OR LOWER(u.accountStatus) = 'active') ORDER BY u.businessName ASC")
    List<User> findActiveOwnersByRoleAndVerificationStatusAndBusinessIdIsNotNullOrderByBusinessNameAsc(@Param("role") String role, @Param("verificationStatus") String verificationStatus);

    /** Verified service-provider owners with active account only (for customer-facing services; excludes disabled/suspended/banned). */
    @Query("SELECT u FROM User u WHERE LOWER(u.role) = LOWER(:role) AND LOWER(u.serviceProviderStatus) = LOWER(:serviceProviderStatus) AND u.businessId IS NOT NULL AND (u.accountStatus IS NULL OR LOWER(u.accountStatus) = 'active') ORDER BY u.businessName ASC")
    List<User> findActiveOwnersByRoleAndServiceProviderStatusAndBusinessIdIsNotNullOrderByBusinessNameAsc(@Param("role") String role, @Param("serviceProviderStatus") String serviceProviderStatus);

    /** Find users by role (e.g. customers for staff/owner "order for" dropdown). */
    List<User> findByRoleIgnoreCaseOrderByNameAsc(String role);

    /** Find customer by phone (for WhatsApp chatbot). Phone should be normalized (e.g. +254712345678 or 0712345678). */
    java.util.Optional<User> findFirstByRoleIgnoreCaseAndPhone(String role, String phone);

    /** Find owners pending verification for admin review queue. */
    List<User> findByRoleIgnoreCaseAndVerificationStatusOrderByCreatedAtAsc(String role, String verificationStatus);

    /** Find owners pending service provider verification. */
    List<User> findByRoleIgnoreCaseAndServiceProviderStatusOrderByCreatedAtAsc(String role, String serviceProviderStatus);
}
