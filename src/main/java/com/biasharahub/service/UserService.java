package com.biasharahub.service;

import com.biasharahub.config.TenantContext;
import com.biasharahub.dto.request.AddAssistantAdminRequest;
import com.biasharahub.dto.request.AddBusinessOwnerRequest;
import com.biasharahub.dto.request.AddCourierRequest;
import com.biasharahub.dto.request.AddOwnerRequest;
import com.biasharahub.dto.request.AddServiceProviderRequest;
import com.biasharahub.dto.request.AddStaffRequest;
import com.biasharahub.dto.response.UserDto;
import com.biasharahub.entity.Tenant;
import com.biasharahub.entity.User;
import com.biasharahub.repository.TenantRepository;
import com.biasharahub.repository.UserRepository;
import com.biasharahub.security.AuthenticatedUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {

    private static final String TEMP_PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final int TEMP_PASSWORD_LENGTH = 14;

    private static final String PAYOUT_METHOD_MPESA = "MPESA";
    private static final String PAYOUT_METHOD_BANK = "BANK_TRANSFER";

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;

    public UserService(UserRepository userRepository, TenantRepository tenantRepository,
                       PasswordEncoder passwordEncoder, MailService mailService) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
    }

    /**
     * Platform admin adds an owner. Temporary password is generated and sent by email.
     * Owner must log in and enable 2FA.
     */
    @Transactional
    public UserDto addOwner(AddOwnerRequest request) {
        if (userRepository.existsByEmail(request.getEmail().toLowerCase())) {
            throw new IllegalArgumentException("Email already registered");
        }
        String t = request.getApplyingForTier() != null ? request.getApplyingForTier().trim().toLowerCase() : "";
        if (!"tier1".equals(t) && !"tier2".equals(t) && !"tier3".equals(t)) {
            throw new IllegalArgumentException("Applying for tier is required and must be tier1, tier2, or tier3");
        }
        String payoutMethod = normalisePayoutMethod(request.getPayoutMethod());
        if (request.getPayoutDestination() == null || request.getPayoutDestination().isBlank()) {
            throw new IllegalArgumentException("Payout destination is required (e.g. M-Pesa number or bank account)");
        }

        Tenant tenant = resolveCurrentTenant();
        if (tenant == null) {
            throw new IllegalStateException("Tenant context required when adding an owner. Send X-Tenant-ID header.");
        }

        String tempPassword = generateTemporaryPassword();
        User owner = User.builder()
                .email(request.getEmail().toLowerCase())
                .passwordHash(passwordEncoder.encode(tempPassword))
                .name(request.getName())
                .role("owner")
                .twoFactorEnabled(false)
                .businessName(request.getBusinessName() != null ? request.getBusinessName().trim() : null)
                .applyingForTier(t)
                .build();
        owner = userRepository.save(owner);
        owner.setBusinessId(owner.getUserId());
        owner = userRepository.save(owner);

        tenant.setDefaultPayoutMethod(payoutMethod);
        tenant.setDefaultPayoutDestination(request.getPayoutDestination().trim());
        tenantRepository.save(tenant);

        mailService.sendWelcomeOwner(owner.getEmail(), owner.getName(), owner.getBusinessName(), tempPassword);
        return toUserDto(owner);
    }

    /**
     * Platform admin onboard a service provider. Creates owner with business; sends verification code (temporary password) by email.
     * Service provider logs in, adds service category and details, uploads verification and qualification documents;
     * admin verifies and approves so services can be listed.
     */
    @Transactional
    public UserDto addServiceProvider(AddServiceProviderRequest request) {
        if (userRepository.existsByEmail(request.getEmail().toLowerCase())) {
            throw new IllegalArgumentException("Email already registered");
        }
        Tenant tenant = resolveCurrentTenant();
        if (tenant == null) {
            throw new IllegalStateException("Tenant context required when adding a service provider. Send X-Tenant-ID header.");
        }
        String tempPassword = generateTemporaryPassword();
        String businessName = request.getBusinessName() != null ? request.getBusinessName().trim() : null;
        User owner = User.builder()
                .email(request.getEmail().toLowerCase())
                .passwordHash(passwordEncoder.encode(tempPassword))
                .name(request.getName())
                .role("owner")
                .twoFactorEnabled(false)
                .businessName(businessName)
                .build();
        owner = userRepository.save(owner);
        owner.setBusinessId(owner.getUserId());
        owner = userRepository.save(owner);
        mailService.sendWelcomeServiceProvider(owner.getEmail(), owner.getName(), owner.getBusinessName(), tempPassword);
        return toUserDto(owner);
    }

    /**
     * Platform admin onboards a business owner who can sell products, offer services, or both.
     * Creates owner with business; sends temporary password by email.
     * Owner logs in and completes verification for their selected business type(s).
     */
    @Transactional
    public UserDto addBusinessOwner(AddBusinessOwnerRequest request) {
        if (userRepository.existsByEmail(request.getEmail().toLowerCase())) {
            throw new IllegalArgumentException("Email already registered");
        }
        if (!request.isSellsProducts() && !request.isOffersServices()) {
            throw new IllegalArgumentException("At least one of sellsProducts or offersServices must be true");
        }

        Tenant tenant = resolveCurrentTenant();
        if (tenant == null) {
            throw new IllegalStateException("Tenant context required when adding a business owner. Send X-Tenant-ID header.");
        }

        // Validate product seller fields if applicable
        String applyingForTier = null;
        if (request.isSellsProducts()) {
            String t = request.getApplyingForTier() != null ? request.getApplyingForTier().trim().toLowerCase() : "";
            if (!"tier1".equals(t) && !"tier2".equals(t) && !"tier3".equals(t)) {
                throw new IllegalArgumentException("Applying for tier is required for product sellers and must be tier1, tier2, or tier3");
            }
            applyingForTier = t;

            String payoutMethod = normalisePayoutMethod(request.getPayoutMethod());
            if (request.getPayoutDestination() == null || request.getPayoutDestination().isBlank()) {
                throw new IllegalArgumentException("Payout destination is required for product sellers (e.g. M-Pesa number or bank account)");
            }
            tenant.setDefaultPayoutMethod(payoutMethod);
            tenant.setDefaultPayoutDestination(request.getPayoutDestination().trim());
            tenantRepository.save(tenant);
        }

        String tempPassword = generateTemporaryPassword();
        String businessName = request.getBusinessName() != null ? request.getBusinessName().trim() : null;

        User owner = User.builder()
                .email(request.getEmail().toLowerCase())
                .passwordHash(passwordEncoder.encode(tempPassword))
                .name(request.getName())
                .role("owner")
                .twoFactorEnabled(false)
                .businessName(businessName)
                .applyingForTier(applyingForTier)
                .build();
        owner = userRepository.save(owner);
        owner.setBusinessId(owner.getUserId());
        owner = userRepository.save(owner);

        mailService.sendWelcomeBusinessOwner(
                owner.getEmail(),
                owner.getName(),
                owner.getBusinessName(),
                request.isSellsProducts(),
                request.isOffersServices(),
                tempPassword
        );
        return toUserDto(owner);
    }

    private Tenant resolveCurrentTenant() {
        String schema = TenantContext.getTenantSchema();
        if (schema == null || schema.isBlank()) return null;
        return tenantRepository.findBySchemaName(schema).orElse(null);
    }

    private static String normalisePayoutMethod(String method) {
        if (method == null) return PAYOUT_METHOD_BANK;
        String m = method.trim().toUpperCase();
        return PAYOUT_METHOD_MPESA.equals(m) ? PAYOUT_METHOD_MPESA : PAYOUT_METHOD_BANK;
    }

    /**
     * Owner adds a staff member. Temporary password is generated and sent by email.
     * Staff must log in and enable 2FA.
     */
    @Transactional
    public UserDto addStaff(AuthenticatedUser currentUser, AddStaffRequest request) {
        if (!"owner".equalsIgnoreCase(currentUser.role())) {
            throw new IllegalArgumentException("Only owners can add staff");
        }
        User owner = userRepository.findById(currentUser.userId())
                .orElseThrow(() -> new IllegalArgumentException("Owner not found"));
        if (owner.getBusinessId() == null || owner.getBusinessName() == null) {
            throw new IllegalArgumentException("Owner business is not set");
        }
        if (userRepository.existsByEmail(request.getEmail().toLowerCase())) {
            throw new IllegalArgumentException("Email already registered");
        }
        String tempPassword = generateTemporaryPassword();
        User staff = User.builder()
                .email(request.getEmail().toLowerCase())
                .passwordHash(passwordEncoder.encode(tempPassword))
                .name(request.getName())
                .role("staff")
                .twoFactorEnabled(true)   // 2FA mandatory for staff; cannot be disabled
                .businessId(owner.getBusinessId())
                .businessName(owner.getBusinessName())
                .build();
        staff = userRepository.save(staff);
        mailService.sendWelcomeStaff(staff.getEmail(), staff.getName(), owner.getBusinessName(), tempPassword);
        return toUserDto(staff);
    }

    /**
     * Owner adds a courier. Phone is required for matching shipments by rider_phone.
     * Temporary password is generated and sent by email.
     */
    @Transactional
    public UserDto addCourier(AuthenticatedUser currentUser, AddCourierRequest request) {
        if (!"owner".equalsIgnoreCase(currentUser.role())) {
            throw new IllegalArgumentException("Only owners can add couriers");
        }
        User owner = userRepository.findById(currentUser.userId())
                .orElseThrow(() -> new IllegalArgumentException("Owner not found"));
        if (owner.getBusinessId() == null || owner.getBusinessName() == null) {
            throw new IllegalArgumentException("Owner business is not set");
        }
        if (userRepository.existsByEmail(request.getEmail().toLowerCase())) {
            throw new IllegalArgumentException("Email already registered");
        }
        String phone = request.getPhone() != null ? request.getPhone().trim() : "";
        if (phone.isEmpty()) {
            throw new IllegalArgumentException("Phone is required for couriers");
        }
        String tempPassword = generateTemporaryPassword();
        User courier = User.builder()
                .email(request.getEmail().toLowerCase())
                .passwordHash(passwordEncoder.encode(tempPassword))
                .name(request.getName())
                .phone(phone)
                .role("courier")
                .twoFactorEnabled(false)
                .businessId(owner.getBusinessId())
                .businessName(owner.getBusinessName())
                .build();
        courier = userRepository.save(courier);
        mailService.sendWelcomeCourier(courier.getEmail(), courier.getName(), owner.getBusinessName(), tempPassword);
        return toUserDto(courier);
    }

    /**
     * Platform admin adds an assistant admin. Temporary password is generated and sent by email.
     * 2FA is always on for assistant admins and cannot be disabled.
     */
    @Transactional
    public UserDto addAssistantAdmin(AddAssistantAdminRequest request) {
        if (userRepository.existsByEmail(request.getEmail().toLowerCase())) {
            throw new IllegalArgumentException("Email already registered");
        }
        String tempPassword = generateTemporaryPassword();
        User assistantAdmin = User.builder()
                .email(request.getEmail().toLowerCase())
                .passwordHash(passwordEncoder.encode(tempPassword))
                .name(request.getName())
                .role("assistant_admin")
                .twoFactorEnabled(true)   // 2FA mandatory for assistant_admin; cannot be disabled
                .build();
        assistantAdmin = userRepository.save(assistantAdmin);
        mailService.sendWelcomeAssistantAdmin(assistantAdmin.getEmail(), assistantAdmin.getName(), tempPassword);
        return toUserDto(assistantAdmin);
    }

    private String generateTemporaryPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            sb.append(TEMP_PASSWORD_CHARS.charAt(random.nextInt(TEMP_PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }

    public List<UserDto> listStaff(AuthenticatedUser currentUser) {
        if (!"owner".equalsIgnoreCase(currentUser.role())) {
            throw new IllegalArgumentException("Only owners can list staff");
        }
        User owner = userRepository.findById(currentUser.userId())
                .orElseThrow(() -> new IllegalArgumentException("Owner not found"));
        if (owner.getBusinessId() == null) {
            return List.of();
        }
        return userRepository.findByRoleAndBusinessId("staff", owner.getBusinessId()).stream()
                .map(this::toUserDto)
                .collect(Collectors.toList());
    }

    public List<UserDto> listCouriers(AuthenticatedUser currentUser) {
        if (!"owner".equalsIgnoreCase(currentUser.role())) {
            throw new IllegalArgumentException("Only owners can list couriers");
        }
        User owner = userRepository.findById(currentUser.userId())
                .orElseThrow(() -> new IllegalArgumentException("Owner not found"));
        if (owner.getBusinessId() == null) {
            return List.of();
        }
        return userRepository.findByRoleAndBusinessId("courier", owner.getBusinessId()).stream()
                .map(this::toUserDto)
                .collect(Collectors.toList());
    }

    private UserDto toUserDto(User user) {
        return UserDto.builder()
                .id(user.getUserId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole())
                .businessId(user.getBusinessId() != null ? user.getBusinessId().toString() : null)
                .businessName(user.getBusinessName())
                .verificationStatus(user.getVerificationStatus())
                .verifiedAt(user.getVerifiedAt())
                .verifiedByUserId(user.getVerifiedByUserId())
                .verificationNotes(user.getVerificationNotes())
                .sellerTier(user.getSellerTier())
                .applyingForTier(user.getApplyingForTier())
                .build();
    }
}
