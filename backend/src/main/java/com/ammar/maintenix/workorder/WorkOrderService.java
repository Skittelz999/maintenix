package com.ammar.maintenix.workorder;

import com.ammar.maintenix.property.Property;
import com.ammar.maintenix.property.PropertyMemberRepository;
import com.ammar.maintenix.property.PropertyRepository;
import com.ammar.maintenix.user.User;
import com.ammar.maintenix.user.UserRepository;
import com.ammar.maintenix.user.UserRole;
import com.ammar.maintenix.workorder.dto.CreateWorkOrderRequest;
import com.ammar.maintenix.workorder.dto.WorkOrderResponse;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
@Service
public class WorkOrderService {

    private final WorkOrderRepository workOrderRepository;
    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final PropertyMemberRepository propertyMemberRepository;

    public WorkOrderService(
            WorkOrderRepository workOrderRepository,
            PropertyRepository propertyRepository,
            UserRepository userRepository,
            PropertyMemberRepository propertyMemberRepository
    ) {
        this.workOrderRepository = workOrderRepository;
        this.propertyRepository = propertyRepository;
        this.userRepository = userRepository;
        this.propertyMemberRepository = propertyMemberRepository;
    }

    @Transactional
    public WorkOrderResponse createWorkOrder(CreateWorkOrderRequest request) {
        Property property = propertyRepository
                .findById(request.getPropertyId())
                .orElseThrow(() -> new EntityNotFoundException("Property not found"));

        User user = userRepository
                .findById(request.getCreatedByUserId())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        if (user.getRole() == UserRole.TENANT
                && !propertyMemberRepository.existsByPropertyIdAndUserId(property.getId(), user.getId())) {
            throw new AccessDeniedException("Tenant is not a member of the property");
        }

        WorkOrderPriority priority = request.getPriority();
        if (priority == null) {
            priority = WorkOrderPriority.MEDIUM;
        }

        WorkOrder workOrder = new WorkOrder(
                property,
                user,
                request.getTitle(),
                request.getDescription()
        );
        workOrder.setPriority(priority);

        WorkOrder savedWorkOrder = workOrderRepository.save(workOrder);
        WorkOrderResponse response = new WorkOrderResponse(
                savedWorkOrder.getId(),
                savedWorkOrder.getProperty().getId(),
                savedWorkOrder.getProperty().getName(),
                savedWorkOrder.getCreatedBy().getId(),
                savedWorkOrder.getCreatedBy().getFirstName()
                        + " "
                        + savedWorkOrder.getCreatedBy().getLastName(),
                savedWorkOrder.getAssignedTo() == null
                        ? null
                        : savedWorkOrder.getAssignedTo().getId(),
                savedWorkOrder.getTitle(),
                savedWorkOrder.getDescription(),
                savedWorkOrder.getStatus(),
                savedWorkOrder.getPriority(),
                savedWorkOrder.getCreatedAt(),
                savedWorkOrder.getUpdatedAt()
        );

        return response;
    }

    @Transactional
    public List<WorkOrderResponse> getAllWorkOrders() {
        List<WorkOrder> workOrders = workOrderRepository.findAll();
        List<WorkOrderResponse> responses = new ArrayList<>();

        for (WorkOrder workOrder : workOrders) {
            WorkOrderResponse response = new WorkOrderResponse(
                    workOrder.getId(),
                    workOrder.getProperty().getId(),
                    workOrder.getProperty().getName(),
                    workOrder.getCreatedBy().getId(),
                    workOrder.getCreatedBy().getFirstName()
                            + " "
                            + workOrder.getCreatedBy().getLastName(),
                    workOrder.getAssignedTo() == null
                            ? null
                            : workOrder.getAssignedTo().getId(),
                    workOrder.getTitle(),
                    workOrder.getDescription(),
                    workOrder.getStatus(),
                    workOrder.getPriority(),
                    workOrder.getCreatedAt(),
                    workOrder.getUpdatedAt()
            );
            responses.add(response);
        }
        return responses;
    }
    @Transactional
    public WorkOrderResponse getWorkOrderById(UUID id) {

        WorkOrder workOrder = workOrderRepository
                .findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Work order not found with id: " + id));

        WorkOrderResponse response = new WorkOrderResponse(
                workOrder.getId(),
                workOrder.getProperty().getId(),
                workOrder.getProperty().getName(),
                workOrder.getCreatedBy().getId(),
                workOrder.getCreatedBy().getFirstName()
                        + " "
                        + workOrder.getCreatedBy().getLastName(),
                workOrder.getAssignedTo() == null
                        ? null
                        : workOrder.getAssignedTo().getId(),
                workOrder.getTitle(),
                workOrder.getDescription(),
                workOrder.getStatus(),
                workOrder.getPriority(),
                workOrder.getCreatedAt(),
                workOrder.getUpdatedAt()
        );
        return response;

    }

}
