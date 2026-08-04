ALTER TABLE sys_file
    ADD COLUMN active_content_sha256 VARCHAR(64)
        GENERATED ALWAYS AS (
            CASE WHEN deleted_flag = 0 THEN SUBSTRING(file_name, 1, 64) ELSE NULL END
        );
DROP INDEX uk_sys_file_active_content;
CREATE UNIQUE INDEX uk_sys_file_active_content
    ON sys_file(tenant_id, business_type, business_id, active_content_sha256);
CREATE INDEX idx_sys_file_object_path
    ON sys_file(bucket_name, storage_path, deleted_flag, id);

ALTER TABLE sys_operation_audit_log ADD COLUMN file_id BIGINT NULL;
CREATE INDEX idx_operation_audit_file
    ON sys_operation_audit_log(tenant_id, file_id, created_at);
