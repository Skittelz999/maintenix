package com.ammar.maintenix.workorder;

public class InvalidWorkOrderStatusTransitionException extends RuntimeException {

    public InvalidWorkOrderStatusTransitionException(
            WorkOrderStatus currentStatus,
            WorkOrderStatus targetStatus
    ) {
        super("Invalid work order status transition: "
                + currentStatus + " -> " + targetStatus);
    }
}
