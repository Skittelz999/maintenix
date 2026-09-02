package com.ammar.maintenix.user.dto;

import com.ammar.maintenix.user.UserRole;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        UserRole role,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
