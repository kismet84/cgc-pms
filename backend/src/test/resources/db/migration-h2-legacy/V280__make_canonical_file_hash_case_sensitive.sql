DROP INDEX uk_sys_file_active_content;
ALTER TABLE sys_file DROP COLUMN active_content_sha256;
ALTER TABLE sys_file
    ADD COLUMN active_content_sha256 VARCHAR(64)
        GENERATED ALWAYS AS (
            CASE
                WHEN deleted_flag = 0 AND REGEXP_LIKE(file_name, '^[0-9a-f]{64}[.]')
                THEN SUBSTRING(file_name, 1, 64)
                ELSE NULL
            END
        );
CREATE UNIQUE INDEX uk_sys_file_active_content
    ON sys_file(tenant_id, business_type, business_id, active_content_sha256);
