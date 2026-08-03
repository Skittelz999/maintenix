CREATE TABLE app_users (
                           id UUID PRIMARY KEY,
                           email VARCHAR(255) NOT NULL,
                           password_hash VARCHAR(255) NOT NULL,
                           first_name VARCHAR(100) NOT NULL,
                           last_name VARCHAR(100) NOT NULL,
                           role VARCHAR(30) NOT NULL,
                           active BOOLEAN NOT NULL DEFAULT TRUE,
                           created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

                           CONSTRAINT uk_app_users_email UNIQUE (email),
                           CONSTRAINT ck_app_users_email_lowercase
                               CHECK (email = lower(email)),
                           CONSTRAINT ck_app_users_role
                               CHECK (role IN ('TENANT', 'TECHNICIAN', 'ADMIN'))
);


CREATE TABLE properties (
                            id UUID PRIMARY KEY,
                            name VARCHAR(150) NOT NULL,
                            address_line VARCHAR(255) NOT NULL,
                            postal_code VARCHAR(20) NOT NULL,
                            city VARCHAR(100) NOT NULL,
                            active BOOLEAN NOT NULL DEFAULT TRUE,
                            created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);


CREATE TABLE property_members (
                                  id UUID PRIMARY KEY,
                                  property_id UUID NOT NULL,
                                  user_id UUID NOT NULL,
                                  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                  CONSTRAINT fk_property_members_property
                                      FOREIGN KEY (property_id)
                                          REFERENCES properties (id)
                                          ON DELETE CASCADE,

                                  CONSTRAINT fk_property_members_user
                                      FOREIGN KEY (user_id)
                                          REFERENCES app_users (id)
                                          ON DELETE CASCADE,

                                  CONSTRAINT uk_property_members_property_user
                                      UNIQUE (property_id, user_id)
);


CREATE TABLE work_orders (
                             id UUID PRIMARY KEY,
                             property_id UUID NOT NULL,
                             created_by_user_id UUID NOT NULL,
                             assigned_to_user_id UUID,
                             title VARCHAR(200) NOT NULL,
                             description TEXT NOT NULL,
                             status VARCHAR(30) NOT NULL DEFAULT 'NEW',
                             priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
                             created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

                             CONSTRAINT fk_work_orders_property
                                 FOREIGN KEY (property_id)
                                     REFERENCES properties (id),

                             CONSTRAINT fk_work_orders_created_by
                                 FOREIGN KEY (created_by_user_id)
                                     REFERENCES app_users (id),

                             CONSTRAINT fk_work_orders_assigned_to
                                 FOREIGN KEY (assigned_to_user_id)
                                     REFERENCES app_users (id),

                             CONSTRAINT ck_work_orders_status
                                 CHECK (
                                     status IN (
                                                'NEW',
                                                'ASSIGNED',
                                                'IN_PROGRESS',
                                                'WAITING_FOR_PARTS',
                                                'RESOLVED',
                                                'CLOSED',
                                                'CANCELLED'
                                         )
                                     ),

                             CONSTRAINT ck_work_orders_priority
                                 CHECK (
                                     priority IN (
                                                  'LOW',
                                                  'MEDIUM',
                                                  'HIGH',
                                                  'URGENT'
                                         )
                                     ),

                             CONSTRAINT ck_work_orders_title_not_blank
                                 CHECK (length(trim(title)) > 0),

                             CONSTRAINT ck_work_orders_description_not_blank
                                 CHECK (length(trim(description)) > 0)
);


CREATE INDEX idx_property_members_user_id
    ON property_members (user_id);

CREATE INDEX idx_property_members_property_id
    ON property_members (property_id);

CREATE INDEX idx_work_orders_property_id
    ON work_orders (property_id);

CREATE INDEX idx_work_orders_created_by_user_id
    ON work_orders (created_by_user_id);

CREATE INDEX idx_work_orders_assigned_to_user_id
    ON work_orders (assigned_to_user_id);

CREATE INDEX idx_work_orders_status
    ON work_orders (status);

CREATE INDEX idx_work_orders_priority
    ON work_orders (priority);