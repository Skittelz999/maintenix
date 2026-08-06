package com.ammar.maintenix.workorder.dto;

import com.ammar.maintenix.workorder.WorkOrderPriority;
import com.ammar.maintenix.workorder.WorkOrderStatus;

import java.time.Instant;
import java.util.UUID;

public class WorkOrderResponse {

    private UUID id;
    private UUID propertyId;
    private String propertyName;
    private UUID createdByUserId;
    private String createdByName;
    private UUID assignedToUserId;
    private String title;
    private String description;
    private WorkOrderStatus status;
    private WorkOrderPriority priority;
    private Instant createdAt;
    private Instant updatedAt;

    public WorkOrderResponse() {
    }

    public WorkOrderResponse(
            UUID id,
            UUID propertyId,
            String propertyName,
            UUID createdByUserId,
            String createdByName,
            UUID assignedToUserId,
            String title,
            String description,
            WorkOrderStatus status,
            WorkOrderPriority priority,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.propertyId = propertyId;
        this.propertyName = propertyName;
        this.createdByUserId = createdByUserId;
        this.createdByName = createdByName;
        this.assignedToUserId = assignedToUserId;
        this.title = title;
        this.description = description;
        this.status = status;
        this.priority = priority;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getPropertyId() {
        return propertyId;
    }

    public void setPropertyId(UUID propertyId) {
        this.propertyId = propertyId;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(UUID createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public String getCreatedByName() {
        return createdByName;
    }

    public void setCreatedByName(String createdByName) {
        this.createdByName = createdByName;
    }

    public UUID getAssignedToUserId() {
        return assignedToUserId;
    }

    public void setAssignedToUserId(UUID assignedToUserId) {
        this.assignedToUserId = assignedToUserId;
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

    public WorkOrderStatus getStatus() {
        return status;
    }

    public void setStatus(WorkOrderStatus status) {
        this.status = status;
    }

    public WorkOrderPriority getPriority() {
        return priority;
    }

    public void setPriority(WorkOrderPriority priority) {
        this.priority = priority;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
