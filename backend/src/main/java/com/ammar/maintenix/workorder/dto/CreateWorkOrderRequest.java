package com.ammar.maintenix.workorder.dto;

import com.ammar.maintenix.workorder.WorkOrderPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public class CreateWorkOrderRequest {

    @NotNull
    private UUID propertyId;

    @NotNull
    private UUID createdByUserId;

    @NotBlank
    @Size(max = 200)
    private String title;

    @NotBlank
    private String description;

    private WorkOrderPriority priority;

    public CreateWorkOrderRequest() {
    }

    public CreateWorkOrderRequest(
            UUID propertyId,
            UUID createdByUserId,
            String title,
            String description,
            WorkOrderPriority priority
    ) {
        this.propertyId = propertyId;
        this.createdByUserId = createdByUserId;
        this.title = title;
        this.description = description;
        this.priority = priority;
    }

    public UUID getPropertyId() {
        return propertyId;
    }

    public void setPropertyId(UUID propertyId) {
        this.propertyId = propertyId;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(UUID createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public WorkOrderPriority getPriority() {
        return priority;
    }

    public void setPriority(WorkOrderPriority priority) {
        this.priority = priority;
    }
}
