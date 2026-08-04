ALTER TABLE sys_file
    ADD COLUMN active_file_name VARCHAR(255)
        GENERATED ALWAYS AS (CASE WHEN deleted_flag = 0 THEN file_name ELSE NULL END) STORED,
    ADD CONSTRAINT ck_sys_file_business_type_normalized
        CHECK (business_type = UPPER(TRIM(business_type))),
    ADD UNIQUE KEY uk_sys_file_active_content
        (tenant_id, business_type, business_id, active_file_name);

CREATE TABLE sys_file_object_task (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    operation VARCHAR(16) NOT NULL,
    source_bucket VARCHAR(100) NOT NULL,
    source_path VARCHAR(500) NOT NULL,
    idempotency_key VARCHAR(700) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_error_code VARCHAR(100) NULL,
    completed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_file_object_task_idempotency (tenant_id, idempotency_key),
    KEY idx_sys_file_object_task_dispatch (status, next_retry_at),
    CONSTRAINT ck_sys_file_object_task_operation CHECK (operation = 'DELETE'),
    CONSTRAINT ck_sys_file_object_task_status CHECK (status IN ('PENDING', 'PROCESSING', 'RETRY', 'SUCCEEDED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件对象存储持久化补偿任务';
