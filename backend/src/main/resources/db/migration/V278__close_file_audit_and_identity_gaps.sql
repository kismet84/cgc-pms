ALTER TABLE sys_file
    DROP CHECK ck_sys_file_business_type_normalized,
    ADD CONSTRAINT ck_sys_file_business_type_normalized
        CHECK (BINARY business_type = BINARY UPPER(TRIM(business_type))),
    ADD COLUMN active_content_sha256 CHAR(64)
        GENERATED ALWAYS AS (
            CASE WHEN deleted_flag = 0 THEN SUBSTRING(file_name, 1, 64) ELSE NULL END
        ) STORED,
    DROP INDEX uk_sys_file_active_content,
    ADD UNIQUE KEY uk_sys_file_active_content
        (tenant_id, business_type, business_id, active_content_sha256),
    MODIFY bucket_name VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin
        NOT NULL DEFAULT 'cgc-pms',
    MODIFY storage_path VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    ADD KEY idx_sys_file_object_path (bucket_name, storage_path, deleted_flag, id);

ALTER TABLE sys_file_object_task
    MODIFY source_bucket VARCHAR(100)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    MODIFY source_path VARCHAR(500)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    MODIFY idempotency_key VARCHAR(700)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL;

ALTER TABLE sys_operation_audit_log
    ADD COLUMN file_id BIGINT NULL AFTER business_id,
    ADD KEY idx_operation_audit_file (tenant_id, file_id, created_at);
