CREATE TABLE project_file_code_scope (
    tenant_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件中心编号租户级并发锁';

INSERT IGNORE INTO project_file_code_scope(tenant_id)
SELECT DISTINCT tenant_id FROM project_file_catalog;

ALTER TABLE project_file_catalog
    DROP INDEX uk_project_file_catalog_code,
    ADD UNIQUE KEY uk_project_file_catalog_code (tenant_id, file_code);

CREATE TABLE document_template_code_scope (
    tenant_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务模板编号租户级并发锁';

INSERT IGNORE INTO document_template_code_scope(tenant_id)
SELECT DISTINCT tenant_id FROM biz_document_template;
