ALTER TABLE work_orders
    DROP CONSTRAINT ck_work_orders_status;

UPDATE work_orders
SET status = 'ON_HOLD'
WHERE status = 'WAITING_FOR_PARTS';

UPDATE work_orders
SET status = 'COMPLETED'
WHERE status IN ('RESOLVED', 'CLOSED');

ALTER TABLE work_orders
    ADD CONSTRAINT ck_work_orders_status
        CHECK (
            status IN (
                'NEW',
                'ASSIGNED',
                'IN_PROGRESS',
                'ON_HOLD',
                'COMPLETED',
                'CANCELLED'
            )
        );
