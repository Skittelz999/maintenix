package com.ammar.maintenix.workorder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, UUID> {

    @EntityGraph(attributePaths = {"property", "createdBy", "assignedTo"})
    @Query("select workOrder from WorkOrder workOrder")
    List<WorkOrder> findAllWithDetails();

    @EntityGraph(attributePaths = {"property", "createdBy", "assignedTo"})
    @Query("""
            select workOrder
            from WorkOrder workOrder
            where exists (
                select propertyMember.id
                from PropertyMember propertyMember
                where propertyMember.property.id = workOrder.property.id
                  and propertyMember.user.id = :userId
            )
            """)
    List<WorkOrder> findAllVisibleToTenant(@Param("userId") UUID userId);

    @EntityGraph(attributePaths = {"property", "createdBy", "assignedTo"})
    List<WorkOrder> findByAssignedToId(UUID technicianId);
}
