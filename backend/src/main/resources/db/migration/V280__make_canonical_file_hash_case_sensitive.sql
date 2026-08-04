ALTER TABLE sys_file
    DROP INDEX uk_sys_file_active_content,
    DROP COLUMN active_content_sha256,
    ADD COLUMN active_content_sha256 CHAR(64)
        GENERATED ALWAYS AS (
            CASE
                WHEN deleted_flag = 0
                    AND BINARY file_name REGEXP BINARY '^[0-9a-f]{64}[.]'
                THEN SUBSTRING(file_name, 1, 64)
                ELSE NULL
            END
        ) STORED,
    ADD UNIQUE KEY uk_sys_file_active_content
        (tenant_id, business_type, business_id, active_content_sha256);
