ALTER TABLE work_orders
    DROP CONSTRAINT ck_work_orders_status;

ALTER TABLE work_orders
    ADD CONSTRAINT ck_work_orders_status
        CHECK (
            status IN (
                'NEW',
                'ASSIGNED',
                'IN_PROGRESS',
                'ON_HOLD',
                'COMPLETED',
                'CLOSED',
                'CANCELLED'
            )
        );
