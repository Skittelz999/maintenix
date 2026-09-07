package com.ammar.maintenix.property.dto;

import com.ammar.maintenix.user.UserRole;

import java.time.Instant;
import java.util.UUID;

public record PropertyMemberResponse(
        UUID id,
        UUID propertyId,
        UUID userId,
        String email,
        String firstName,
        String lastName,
        UserRole role,
        Instant createdAt
) {
}
