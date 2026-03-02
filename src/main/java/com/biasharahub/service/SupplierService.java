package com.biasharahub.service;

import com.biasharahub.dto.request.CreateSupplierRequest;
import com.biasharahub.dto.request.UpdateSupplierRequest;
import com.biasharahub.dto.response.SupplierDto;
import com.biasharahub.entity.Supplier;
import com.biasharahub.entity.User;
import com.biasharahub.repository.SupplierRepository;
import com.biasharahub.repository.UserRepository;
import com.biasharahub.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    public List<SupplierDto> listMyBusinessSuppliers(AuthenticatedUser user) {
        UUID businessId = requireBusinessId(user);
        return supplierRepository.findByBusinessIdOrderByNameAsc(businessId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public SupplierDto create(AuthenticatedUser user, CreateSupplierRequest request) {
        User actor = userRepository.findById(user.userId()).orElseThrow(() -> new IllegalArgumentException("User not found"));
        UUID businessId = requireBusinessId(user);
        Supplier s = Supplier.builder()
                .businessId(businessId)
                .name(request.getName().trim())
                .phone(request.getPhone() != null ? request.getPhone().trim() : null)
                .email(request.getEmail() != null ? request.getEmail().trim().toLowerCase() : null)
                .createdBy(actor)
                .build();
        s = supplierRepository.save(s);
        // Optionally create a login account for this supplier (role = supplier) with temporary password
        if (s.getEmail() != null && !s.getEmail().isBlank()) {
            try {
                userService.addSupplierUser(actor, s);
            } catch (IllegalArgumentException ignored) {
                // If email already has an account, skip silently – supplier can reuse it
            }
        }
        return toDto(s);
    }

    @Transactional
    public SupplierDto update(AuthenticatedUser user, UUID supplierId, UpdateSupplierRequest request) {
        UUID businessId = requireBusinessId(user);
        Supplier s = supplierRepository.findById(supplierId).orElseThrow(() -> new IllegalArgumentException("Supplier not found"));
        if (!businessId.equals(s.getBusinessId())) {
            throw new IllegalArgumentException("Forbidden");
        }
        if (request.getName() != null) s.setName(request.getName().trim());
        if (request.getPhone() != null) s.setPhone(request.getPhone().trim());
        if (request.getEmail() != null) s.setEmail(request.getEmail().trim().toLowerCase());
        s = supplierRepository.save(s);
        return toDto(s);
    }

    @Transactional
    public void delete(AuthenticatedUser user, UUID supplierId) {
        UUID businessId = requireBusinessId(user);
        Supplier s = supplierRepository.findById(supplierId).orElseThrow(() -> new IllegalArgumentException("Supplier not found"));
        if (!businessId.equals(s.getBusinessId())) {
            throw new IllegalArgumentException("Forbidden");
        }
        supplierRepository.delete(s);
    }

    private UUID requireBusinessId(AuthenticatedUser user) {
        if (user == null) throw new IllegalArgumentException("Not authenticated");
        if (!"owner".equalsIgnoreCase(user.role())) {
            throw new IllegalArgumentException("Only owners can manage suppliers");
        }
        return userRepository.findById(user.userId())
                .map(User::getBusinessId)
                .filter(id -> id != null)
                .orElseThrow(() -> new IllegalArgumentException("Business not set"));
    }

    private SupplierDto toDto(Supplier s) {
        return SupplierDto.builder()
                .id(s.getSupplierId())
                .businessId(s.getBusinessId())
                .name(s.getName())
                .phone(s.getPhone())
                .email(s.getEmail())
                .createdAt(s.getCreatedAt())
                .build();
    }
}

