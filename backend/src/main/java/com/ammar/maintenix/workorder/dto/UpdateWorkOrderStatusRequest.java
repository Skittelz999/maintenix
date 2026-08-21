package com.ammar.maintenix.workorder.dto;

import com.ammar.maintenix.workorder.WorkOrderStatus;
import jakarta.validation.constraints.NotNull;

public class UpdateWorkOrderStatusRequest {

    @NotNull
    private WorkOrderStatus status;

    public UpdateWorkOrderStatusRequest() {
    }

    public UpdateWorkOrderStatusRequest(WorkOrderStatus status) {
        this.status = status;
    }

    public WorkOrderStatus getStatus() {
        return status;
    }

    public void setStatus(WorkOrderStatus status) {
        this.status = status;
    }
}