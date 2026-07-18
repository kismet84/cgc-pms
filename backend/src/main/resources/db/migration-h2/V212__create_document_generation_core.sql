-- H2 mirror of V212__create_document_generation_core.sql.
CREATE TABLE biz_document_template (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    template_code VARCHAR(80) NOT NULL,
    template_name VARCHAR(200) NOT NULL,
    business_type VARCHAR(50) NOT NULL,
    engine_type VARCHAR(20) NOT NULL DEFAULT 'HTML_PDF',
    enabled SMALLINT NOT NULL DEFAULT 1,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    active_unique_token BIGINT GENERATED ALWAYS AS (CASE WHEN deleted_flag = 0 THEN 0 ELSE id END),
    PRIMARY KEY (id),
    CONSTRAINT uk_document_template_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_document_template_code UNIQUE (tenant_id, template_code, active_unique_token),
    CONSTRAINT ck_document_template_business CHECK (business_type IN ('PAYMENT', 'SETTLEMENT')),
    CONSTRAINT ck_document_template_engine CHECK (engine_type = 'HTML_PDF'),
    CONSTRAINT ck_document_template_enabled CHECK (enabled IN (0, 1))
);
CREATE INDEX idx_document_template_business ON biz_document_template (tenant_id, business_type, enabled, deleted_flag);

CREATE TABLE biz_document_template_version (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    template_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    schema_version VARCHAR(30) NOT NULL,
    template_content CLOB NOT NULL,
    content_hash CHAR(64) NOT NULL,
    field_manifest CLOB NOT NULL,
    published_by BIGINT NULL,
    published_at TIMESTAMP NULL,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_document_template_version_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_document_template_version_owner UNIQUE (tenant_id, id, template_id),
    CONSTRAINT uk_document_template_version_no UNIQUE (tenant_id, template_id, version_no),
    CONSTRAINT fk_document_template_version_template FOREIGN KEY (tenant_id, template_id)
        REFERENCES biz_document_template (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_document_template_version_no CHECK (version_no > 0),
    CONSTRAINT ck_document_template_version_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'DISABLED')),
    CONSTRAINT ck_document_template_publish_metadata CHECK (
        (status = 'DRAFT' AND published_by IS NULL AND published_at IS NULL)
        OR (status IN ('PUBLISHED', 'DISABLED') AND published_by IS NOT NULL AND published_at IS NOT NULL)
    )
);
CREATE INDEX idx_document_template_version_status ON biz_document_template_version (tenant_id, template_id, status, version_no);

CREATE TABLE biz_document_default_binding (
    tenant_id BIGINT NOT NULL,
    business_type VARCHAR(50) NOT NULL,
    template_id BIGINT NOT NULL,
    template_version_id BIGINT NOT NULL,
    lock_version INT NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, business_type),
    CONSTRAINT fk_document_default_template FOREIGN KEY (tenant_id, template_id)
        REFERENCES biz_document_template (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_document_default_version FOREIGN KEY (tenant_id, template_version_id, template_id)
        REFERENCES biz_document_template_version (tenant_id, id, template_id) ON DELETE RESTRICT,
    CONSTRAINT ck_document_default_business CHECK (business_type IN ('PAYMENT', 'SETTLEMENT')),
    CONSTRAINT ck_document_default_lock_version CHECK (lock_version >= 0)
);
CREATE INDEX idx_document_default_version ON biz_document_default_binding (tenant_id, template_version_id, template_id);

CREATE TABLE biz_document_generation (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    generation_no VARCHAR(50) NOT NULL,
    business_type VARCHAR(50) NOT NULL,
    business_id BIGINT NOT NULL,
    template_id BIGINT NOT NULL,
    template_version_id BIGINT NOT NULL,
    schema_version VARCHAR(30) NOT NULL,
    source_digest CHAR(64) NOT NULL,
    output_sha256 CHAR(64) NULL,
    renderer_id VARCHAR(50) NOT NULL,
    renderer_version VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    file_id BIGINT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    retry_of_generation_id BIGINT NULL,
    failure_code VARCHAR(80) NULL,
    requested_by BIGINT NOT NULL,
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_document_generation_no UNIQUE (tenant_id, generation_no),
    CONSTRAINT uk_document_generation_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT fk_document_generation_version FOREIGN KEY (tenant_id, template_version_id, template_id)
        REFERENCES biz_document_template_version (tenant_id, id, template_id) ON DELETE RESTRICT,
    CONSTRAINT fk_document_generation_file FOREIGN KEY (file_id) REFERENCES sys_file (id) ON DELETE RESTRICT,
    CONSTRAINT fk_document_generation_retry FOREIGN KEY (retry_of_generation_id) REFERENCES biz_document_generation (id) ON DELETE RESTRICT,
    CONSTRAINT ck_document_generation_business CHECK (business_type IN ('PAYMENT', 'SETTLEMENT')),
    CONSTRAINT ck_document_generation_status CHECK (status IN ('PENDING', 'RENDERING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_document_generation_completion CHECK (
        (status = 'SUCCEEDED' AND file_id IS NOT NULL AND output_sha256 IS NOT NULL AND completed_at IS NOT NULL AND failure_code IS NULL)
        OR (status = 'FAILED' AND file_id IS NULL AND output_sha256 IS NULL AND completed_at IS NOT NULL AND failure_code IS NOT NULL)
        OR (status IN ('PENDING', 'RENDERING') AND file_id IS NULL AND output_sha256 IS NULL AND completed_at IS NULL AND failure_code IS NULL)
    )
);
CREATE INDEX idx_document_generation_business ON biz_document_generation (tenant_id, business_type, business_id, requested_at);
CREATE INDEX idx_document_generation_status ON biz_document_generation (tenant_id, status, requested_at);
CREATE INDEX idx_document_generation_version ON biz_document_generation (tenant_id, template_version_id, template_id);
CREATE INDEX idx_document_generation_file ON biz_document_generation (file_id);
CREATE INDEX idx_document_generation_retry ON biz_document_generation (retry_of_generation_id);

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
SELECT 2120, 0, 5, '业务单据模板', 'MENU', 'document-template', NULL, 'document:template:query', 'document', 20, 'ENABLE', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 2120);
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
SELECT 2121, 0, 2120, '维护业务单据模板', 'BUTTON', NULL, NULL, 'document:template:edit', NULL, 1, 'ENABLE', 0 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 2121);
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
SELECT 2122, 0, 2120, '发布业务单据模板', 'BUTTON', NULL, NULL, 'document:template:publish', NULL, 2, 'ENABLE', 0 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 2122);
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
SELECT 2123, 0, 2120, '生成业务单据PDF', 'BUTTON', NULL, NULL, 'document:generate', NULL, 3, 'ENABLE', 0 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 2123);
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
SELECT 2124, 0, 2120, '查询业务单据历史', 'BUTTON', NULL, NULL, 'document:history:query', NULL, 4, 'ENABLE', 0 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 2124);
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
SELECT 2125, 0, 2120, '下载业务单据PDF', 'BUTTON', NULL, NULL, 'document:download', NULL, 5, 'ENABLE', 0 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 2125);
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
SELECT 2126, 0, 2120, '审计下载业务单据PDF', 'BUTTON', NULL, NULL, 'document:audit:download', NULL, 6, 'ENABLE', 0 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 2126);

INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT 212000000 + r.id * 10000 + m.id, r.tenant_id, r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.tenant_id = r.tenant_id AND m.id IN (2120, 2121, 2122) AND m.deleted_flag = 0
WHERE r.role_code IN ('SUPER_ADMIN', 'ADMIN') AND r.deleted_flag = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.tenant_id = r.tenant_id AND rm.role_id = r.id AND rm.menu_id = m.id);

INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT 212100000 + r.id * 10000 + m.id, r.tenant_id, r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.tenant_id = r.tenant_id AND m.id IN (2123, 2124, 2125) AND m.deleted_flag = 0
WHERE r.role_code IN ('SUPER_ADMIN', 'ADMIN', 'PROJECT_MANAGER', 'COST_MANAGER', 'FINANCE', 'AUDITOR') AND r.deleted_flag = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.tenant_id = r.tenant_id AND rm.role_id = r.id AND rm.menu_id = m.id);

INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT 212200000 + r.id * 10000 + 2126, r.tenant_id, r.id, 2126
FROM sys_role r
JOIN sys_menu m ON m.tenant_id = r.tenant_id AND m.id = 2126 AND m.deleted_flag = 0
WHERE r.role_code = 'SUPER_ADMIN' AND r.deleted_flag = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.tenant_id = r.tenant_id AND rm.role_id = r.id AND rm.menu_id = 2126);
