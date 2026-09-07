package com.ammar.maintenix.property.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class AddPropertyMemberRequest {

    @NotNull
    private UUID userId;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }
}
