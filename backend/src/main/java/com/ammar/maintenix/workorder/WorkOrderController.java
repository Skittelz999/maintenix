package com.ammar.maintenix.workorder;

import com.ammar.maintenix.workorder.dto.AssignTechnicianRequest;
import com.ammar.maintenix.workorder.dto.CreateWorkOrderRequest;
import com.ammar.maintenix.workorder.dto.UpdateWorkOrderStatusRequest;
import com.ammar.maintenix.workorder.dto.WorkOrderResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/work-orders")
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    public WorkOrderController(WorkOrderService workOrderService) {
        this.workOrderService = workOrderService;
    }

    @PostMapping
    public ResponseEntity<WorkOrderResponse> createWorkOrder(
            @Valid @RequestBody CreateWorkOrderRequest request,
            Authentication authentication
    ) {
        WorkOrderResponse response = workOrderService.createWorkOrder(
                request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<WorkOrderResponse>> getAllWorkOrders(
            Authentication authentication
    ) {
        return ResponseEntity.ok(workOrderService.getVisibleWorkOrders(
                authentication.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkOrderResponse> getWorkOrderById(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(workOrderService.getVisibleWorkOrderById(
                id, authentication.getName()));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<WorkOrderResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateWorkOrderStatusRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(workOrderService.updateStatus(
                id, request, authentication.getName()));
    }

    @PatchMapping("/{id}/assign")
    public ResponseEntity<WorkOrderResponse> assignTechnician(
            @PathVariable UUID id,
            @Valid @RequestBody AssignTechnicianRequest request
    ) {
        return ResponseEntity.ok(workOrderService.assignTechnician(id, request));
    }
}
