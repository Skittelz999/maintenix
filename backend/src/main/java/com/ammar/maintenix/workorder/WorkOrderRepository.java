package com.ammar.maintenix.workorder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, UUID> {
}
