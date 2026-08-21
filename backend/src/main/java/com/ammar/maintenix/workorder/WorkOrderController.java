package com.ammar.maintenix.workorder;

import com.ammar.maintenix.workorder.dto.CreateWorkOrderRequest;
import com.ammar.maintenix.workorder.dto.WorkOrderResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.UUID;
import org.springframework.web.bind.annotation.PatchMapping;
import com.ammar.maintenix.workorder.dto.UpdateWorkOrderStatusRequest;

@RestController
@RequestMapping("/api/work-orders")
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    public WorkOrderController(WorkOrderService workOrderService) {
        this.workOrderService = workOrderService;
    }

    @PostMapping
    public ResponseEntity<WorkOrderResponse> createWorkOrder(@Valid @RequestBody CreateWorkOrderRequest request) {
        WorkOrderResponse response =
                workOrderService.createWorkOrder(request);
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping
    public ResponseEntity<List<WorkOrderResponse>> getAllWorkOrders() {
        List<WorkOrderResponse> responses = workOrderService.getAllWorkOrders();
        return ResponseEntity.status(200).body(responses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkOrderResponse> getWorkOrderById( @PathVariable UUID id)
    {
        WorkOrderResponse response = workOrderService.getWorkOrderById(id);
        return ResponseEntity.status(200).body(response);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<WorkOrderResponse> updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateWorkOrderStatusRequest request)
    {
        WorkOrderResponse response = workOrderService.updateStatus(id,request);
        return ResponseEntity.status(200).body(response);
    }
}
