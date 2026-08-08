package com.ammar.maintenix.workorder;

import com.ammar.maintenix.workorder.dto.CreateWorkOrderRequest;
import com.ammar.maintenix.workorder.dto.WorkOrderResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
