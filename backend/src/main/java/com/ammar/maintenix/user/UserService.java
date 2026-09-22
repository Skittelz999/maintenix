package com.ammar.maintenix.user;

import com.ammar.maintenix.user.dto.CreateUserRequest;
import com.ammar.maintenix.user.dto.UserResponse;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse createUser(CreateUserRequest request) {
        String email = normalizeEmail(request.getEmail());

        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException(email);
        }

        User user = new User(
                email,
                passwordEncoder.encode(request.getPassword()),
                request.getFirstName().strip(),
                request.getLastName().strip(),
                request.getRole()
        );

        try {
            return mapToResponse(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateEmailException(email);
        }
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserResponse> getUsers(UserRole role, Boolean active) {
        List<User> users;

        if (role != null && active != null) {
            users = userRepository.findAllByRoleAndActiveOrderByEmailAsc(
                    role, active);
        } else if (role != null) {
            users = userRepository.findAllByRoleOrderByEmailAsc(role);
        } else if (active != null) {
            users = userRepository.findAllByActiveOrderByEmailAsc(active);
        } else {
            users = userRepository.findAllByOrderByEmailAsc();
        }

        return users.stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse getUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "User not found with id: " + userId));
        return mapToResponse(user);
    }

    private UserResponse mapToResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
