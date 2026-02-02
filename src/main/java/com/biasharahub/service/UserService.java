package com.biasharahub.service;

import com.biasharahub.dto.request.AddUserRequest;
import com.biasharahub.dto.response.UserDto;
import com.biasharahub.entity.User;
import com.biasharahub.repository.UserRepository;
import com.biasharahub.security.AuthenticatedUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Owner adds a staff member to their business.
     */
    @Transactional
    public UserDto addStaff(AuthenticatedUser currentUser, AddUserRequest request) {
        if (!"owner".equalsIgnoreCase(currentUser.role())) {
            throw new IllegalArgumentException("Only owners can add staff");
        }
        if (userRepository.existsByEmail(request.getEmail().toLowerCase())) {
            throw new IllegalArgumentException("Email already registered");
        }
        User staff = User.builder()
                .email(request.getEmail().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .role("staff")
                .twoFactorEnabled(false)
                .build();
        staff = userRepository.save(staff);
        return toUserDto(staff);
    }

    /**
     * Super admin adds an owner (new business owner).
     */
    @Transactional
    public UserDto addOwner(AddUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail().toLowerCase())) {
            throw new IllegalArgumentException("Email already registered");
        }
        User owner = User.builder()
                .email(request.getEmail().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .role("owner")
                .twoFactorEnabled(false)
                .build();
        owner = userRepository.save(owner);
        return toUserDto(owner);
    }

    public List<UserDto> listStaff(AuthenticatedUser currentUser) {
        if (!"owner".equalsIgnoreCase(currentUser.role())) {
            throw new IllegalArgumentException("Only owners can list staff");
        }
        return userRepository.findAll().stream()
                .filter(u -> "staff".equalsIgnoreCase(u.getRole()))
                .map(this::toUserDto)
                .collect(Collectors.toList());
    }

    private UserDto toUserDto(User user) {
        return UserDto.builder()
                .id(user.getUserId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }
}
