package com.ammar.maintenix.workorder.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public class AssignTechnicianRequest {

    @NotNull
    private UUID technicianId;

    public UUID getTechnicianId() {
        return technicianId;
    }

    public void setTechnicianId(UUID technicianId) {
        this.technicianId = technicianId;
    }
}