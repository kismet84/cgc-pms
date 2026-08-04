ALTER TABLE sys_file
    ADD COLUMN active_file_name VARCHAR(255)
        GENERATED ALWAYS AS (CASE WHEN deleted_flag = 0 THEN file_name ELSE NULL END);
ALTER TABLE sys_file
    ADD CONSTRAINT ck_sys_file_business_type_normalized
        CHECK (business_type = UPPER(TRIM(business_type)));
CREATE UNIQUE INDEX uk_sys_file_active_content
    ON sys_file(tenant_id, business_type, business_id, active_file_name);

CREATE TABLE sys_file_object_task (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    operation VARCHAR(16) NOT NULL,
    source_bucket VARCHAR(100) NOT NULL,
    source_path VARCHAR(500) NOT NULL,
    idempotency_key VARCHAR(700) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error_code VARCHAR(100) NULL,
    completed_at TIMESTAMP(3) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_file_object_task_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT ck_sys_file_object_task_operation CHECK (operation = 'DELETE'),
    CONSTRAINT ck_sys_file_object_task_status CHECK (status IN ('PENDING', 'PROCESSING', 'RETRY', 'SUCCEEDED', 'FAILED'))
);
CREATE INDEX idx_sys_file_object_task_dispatch
    ON sys_file_object_task(status, next_retry_at);
