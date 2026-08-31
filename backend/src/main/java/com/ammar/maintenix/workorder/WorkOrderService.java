package com.ammar.maintenix.workorder;

import com.ammar.maintenix.property.Property;
import com.ammar.maintenix.property.PropertyMemberRepository;
import com.ammar.maintenix.property.PropertyRepository;
import com.ammar.maintenix.user.User;
import com.ammar.maintenix.user.UserRepository;
import com.ammar.maintenix.user.UserRole;
import com.ammar.maintenix.workorder.dto.AssignTechnicianRequest;
import com.ammar.maintenix.workorder.dto.CreateWorkOrderRequest;
import com.ammar.maintenix.workorder.dto.UpdateWorkOrderStatusRequest;
import com.ammar.maintenix.workorder.dto.WorkOrderResponse;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
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
    @PreAuthorize("hasAnyRole('TENANT', 'ADMIN')")
    public WorkOrderResponse createWorkOrder(
            CreateWorkOrderRequest request,
            String currentUserEmail
    ) {

        Property property = propertyRepository
                .findById(request.getPropertyId())
                .orElseThrow(() ->
                        new EntityNotFoundException("Property not found"));

        User user = getCurrentUser(currentUserEmail);

        if (user.getRole() == UserRole.TENANT
                && !propertyMemberRepository.existsByPropertyIdAndUserId(
                property.getId(),
                user.getId())) {

            throw new AccessDeniedException(
                    "Tenant is not a member of the property");
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

        WorkOrder savedWorkOrder =
                workOrderRepository.save(workOrder);

        return mapToResponse(savedWorkOrder);
    }

    @Transactional(readOnly = true)
    public List<WorkOrderResponse> getVisibleWorkOrders(String currentUserEmail) {
        User currentUser = getCurrentUser(currentUserEmail);

        List<WorkOrder> workOrders = switch (currentUser.getRole()) {
            case ADMIN -> workOrderRepository.findAllWithDetails();
            case TENANT -> workOrderRepository.findAllVisibleToTenant(
                    currentUser.getId());
            case TECHNICIAN -> workOrderRepository.findByAssignedToId(
                    currentUser.getId());
        };

        List<WorkOrderResponse> responses =
                new ArrayList<>();

        for (WorkOrder workOrder : workOrders) {
            responses.add(mapToResponse(workOrder));
        }

        return responses;
    }

    @Transactional(readOnly = true)
    public WorkOrderResponse getVisibleWorkOrderById(
            UUID id,
            String currentUserEmail
    ) {

        WorkOrder workOrder = workOrderRepository
                .findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Work order not found with id: " + id));

        User currentUser = getCurrentUser(currentUserEmail);

        boolean hasAccess = switch (currentUser.getRole()) {
            case ADMIN -> true;
            case TENANT -> propertyMemberRepository
                    .existsByPropertyIdAndUserId(
                            workOrder.getProperty().getId(),
                            currentUser.getId());
            case TECHNICIAN -> workOrder.getAssignedTo() != null
                    && workOrder.getAssignedTo().getId()
                    .equals(currentUser.getId());
        };

        if (!hasAccess) {
            throw new AccessDeniedException(
                    "You do not have access to this work order");
        }

        return mapToResponse(workOrder);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMIN')")
    public WorkOrderResponse updateStatus(
            UUID id,
            UpdateWorkOrderStatusRequest request) {

        WorkOrder workOrder = workOrderRepository
                .findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Work order not found with id: " + id));

        WorkOrderStatus status = request.getStatus();

        workOrder.setStatus(status);

        WorkOrder savedWorkOrder =
                workOrderRepository.save(workOrder);

        return mapToResponse(savedWorkOrder);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public WorkOrderResponse assignTechnician(
            UUID workOrderId,
            AssignTechnicianRequest request) {

        WorkOrder workOrder = workOrderRepository
                .findById(workOrderId)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Work order not found with id: "
                                        + workOrderId));

        User user = userRepository
                .findById(request.getTechnicianId())
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "User not found with id: "
                                        + request.getTechnicianId()));

        if (user.getRole() != UserRole.TECHNICIAN) {
            throw new IllegalArgumentException(
                    "User is not a technician");
        }

        workOrder.setAssignedTo(user);

        WorkOrder savedWorkOrder =
                workOrderRepository.save(workOrder);

        return mapToResponse(savedWorkOrder);
    }

    private WorkOrderResponse mapToResponse(WorkOrder workOrder) {

        return new WorkOrderResponse(
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
    }

    private User getCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new AccessDeniedException("Authenticated user not found"));

        if (!user.isActive()) {
            throw new AccessDeniedException("User account is inactive");
        }

        return user;
    }
}
