package com.ammar.maintenix.property.dto;

import java.time.Instant;
import java.util.UUID;

public record PropertyResponse(
        UUID id,
        String name,
        String addressLine,
        String postalCode,
        String city,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
