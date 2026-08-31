package com.ammar.maintenix.workorder;

import java.util.Map;
import java.util.Set;

public enum WorkOrderStatus {
    NEW,
    ASSIGNED,
    IN_PROGRESS,
    ON_HOLD,
    COMPLETED,
    CANCELLED;

    private static final Map<WorkOrderStatus, Set<WorkOrderStatus>>
            ALLOWED_TRANSITIONS = Map.of(
                    NEW, Set.of(ASSIGNED, CANCELLED),
                    ASSIGNED, Set.of(IN_PROGRESS, CANCELLED),
                    IN_PROGRESS, Set.of(ON_HOLD, COMPLETED, CANCELLED),
                    ON_HOLD, Set.of(IN_PROGRESS, CANCELLED),
                    COMPLETED, Set.of(),
                    CANCELLED, Set.of()
            );

    public boolean canTransitionTo(WorkOrderStatus targetStatus) {
        return targetStatus != null
                && ALLOWED_TRANSITIONS.get(this).contains(targetStatus);
    }
}
