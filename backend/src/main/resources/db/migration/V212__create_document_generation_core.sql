-- Versioned business-document templates and immutable PDF generation facts.
SET NAMES utf8mb4;

CREATE TABLE biz_document_template (
    id BIGINT NOT NULL COMMENT '模板ID，雪花ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    template_code VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租户内稳定模板编码',
    template_name VARCHAR(200) NOT NULL COMMENT '模板名称',
    business_type VARCHAR(50) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'PAYMENT或SETTLEMENT',
    engine_type VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'HTML_PDF' COMMENT '受限HTML/CSS转PDF',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否允许继续维护和发布',
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    active_unique_token BIGINT GENERATED ALWAYS AS (CASE WHEN deleted_flag = 0 THEN 0 ELSE id END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_document_template_tenant_id (tenant_id, id),
    UNIQUE KEY uk_document_template_code (tenant_id, template_code, active_unique_token),
    KEY idx_document_template_business (tenant_id, business_type, enabled, deleted_flag),
    CONSTRAINT ck_document_template_business CHECK (business_type IN ('PAYMENT', 'SETTLEMENT')),
    CONSTRAINT ck_document_template_engine CHECK (engine_type = 'HTML_PDF'),
    CONSTRAINT ck_document_template_enabled CHECK (enabled IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务单据模板定义';

CREATE TABLE biz_document_template_version (
    id BIGINT NOT NULL COMMENT '模板版本ID，雪花ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    template_id BIGINT NOT NULL COMMENT '模板ID',
    version_no INT NOT NULL COMMENT '从1递增的版本号',
    status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/DISABLED',
    schema_version VARCHAR(30) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '数据契约版本',
    template_content MEDIUMTEXT NOT NULL COMMENT '受限HTML/CSS模板正文',
    content_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '模板正文SHA-256',
    field_manifest JSON NOT NULL COMMENT '允许字段清单快照',
    published_by BIGINT NULL,
    published_at DATETIME NULL,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_document_template_version_tenant_id (tenant_id, id),
    UNIQUE KEY uk_document_template_version_owner (tenant_id, id, template_id),
    UNIQUE KEY uk_document_template_version_no (tenant_id, template_id, version_no),
    KEY idx_document_template_version_status (tenant_id, template_id, status, version_no),
    CONSTRAINT fk_document_template_version_template FOREIGN KEY (tenant_id, template_id)
        REFERENCES biz_document_template (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_document_template_version_no CHECK (version_no > 0),
    CONSTRAINT ck_document_template_version_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'DISABLED')),
    CONSTRAINT ck_document_template_publish_metadata CHECK (
        (status = 'DRAFT' AND published_by IS NULL AND published_at IS NULL)
        OR (status IN ('PUBLISHED', 'DISABLED') AND published_by IS NOT NULL AND published_at IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不可变发布的业务单据模板版本';

CREATE TABLE biz_document_default_binding (
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    business_type VARCHAR(50) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'PAYMENT或SETTLEMENT',
    template_id BIGINT NOT NULL COMMENT '默认模板ID',
    template_version_id BIGINT NOT NULL COMMENT '默认已发布版本ID',
    lock_version INT NOT NULL DEFAULT 0 COMMENT '默认绑定CAS版本',
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, business_type),
    KEY idx_document_default_version (tenant_id, template_version_id, template_id),
    CONSTRAINT fk_document_default_template FOREIGN KEY (tenant_id, template_id)
        REFERENCES biz_document_template (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_document_default_version FOREIGN KEY (tenant_id, template_version_id, template_id)
        REFERENCES biz_document_template_version (tenant_id, id, template_id) ON DELETE RESTRICT,
    CONSTRAINT ck_document_default_business CHECK (business_type IN ('PAYMENT', 'SETTLEMENT')),
    CONSTRAINT ck_document_default_lock_version CHECK (lock_version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='租户业务类型默认模板版本';

CREATE TABLE biz_document_generation (
    id BIGINT NOT NULL COMMENT '生成事实ID，雪花ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    generation_no VARCHAR(50) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '人类可读生成编号',
    business_type VARCHAR(50) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'PAYMENT或SETTLEMENT',
    business_id BIGINT NOT NULL COMMENT '源业务ID',
    template_id BIGINT NOT NULL COMMENT '实际模板ID',
    template_version_id BIGINT NOT NULL COMMENT '实际不可变版本ID',
    schema_version VARCHAR(30) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '数据契约版本快照',
    source_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '规范化源数据SHA-256',
    output_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '归档PDF SHA-256',
    renderer_id VARCHAR(50) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '渲染器标识',
    renderer_version VARCHAR(50) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '渲染器版本',
    status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RENDERING/SUCCEEDED/FAILED',
    file_id BIGINT NULL COMMENT '成功归档的sys_file.id',
    idempotency_key VARCHAR(120) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租户内生成幂等键',
    retry_of_generation_id BIGINT NULL COMMENT '失败重试来源生成事实ID',
    failure_code VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '稳定失败码，不存堆栈',
    requested_by BIGINT NOT NULL COMMENT '请求用户',
    requested_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME NULL,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_document_generation_no (tenant_id, generation_no),
    UNIQUE KEY uk_document_generation_idempotency (tenant_id, idempotency_key),
    KEY idx_document_generation_business (tenant_id, business_type, business_id, requested_at),
    KEY idx_document_generation_status (tenant_id, status, requested_at),
    KEY idx_document_generation_version (tenant_id, template_version_id, template_id),
    KEY idx_document_generation_file (file_id),
    KEY idx_document_generation_retry (retry_of_generation_id),
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='可审计PDF生成事实';

INSERT IGNORE INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
VALUES
    (2120, 0, 5, '业务单据模板', 'MENU', 'document-template', NULL, 'document:template:query', 'document', 20, 'ENABLE', 0),
    (2121, 0, 2120, '维护业务单据模板', 'BUTTON', NULL, NULL, 'document:template:edit', NULL, 1, 'ENABLE', 0),
    (2122, 0, 2120, '发布业务单据模板', 'BUTTON', NULL, NULL, 'document:template:publish', NULL, 2, 'ENABLE', 0),
    (2123, 0, 2120, '生成业务单据PDF', 'BUTTON', NULL, NULL, 'document:generate', NULL, 3, 'ENABLE', 0),
    (2124, 0, 2120, '查询业务单据历史', 'BUTTON', NULL, NULL, 'document:history:query', NULL, 4, 'ENABLE', 0),
    (2125, 0, 2120, '下载业务单据PDF', 'BUTTON', NULL, NULL, 'document:download', NULL, 5, 'ENABLE', 0),
    (2126, 0, 2120, '审计下载业务单据PDF', 'BUTTON', NULL, NULL, 'document:audit:download', NULL, 6, 'ENABLE', 0);

INSERT IGNORE INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT 212000000 + r.id * 10000 + m.id, r.tenant_id, r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.tenant_id = r.tenant_id AND m.id IN (2120, 2121, 2122) AND m.deleted_flag = 0
WHERE r.role_code IN ('SUPER_ADMIN', 'ADMIN') AND r.deleted_flag = 0;

INSERT IGNORE INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT 212100000 + r.id * 10000 + m.id, r.tenant_id, r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.tenant_id = r.tenant_id AND m.id IN (2123, 2124, 2125) AND m.deleted_flag = 0
WHERE r.role_code IN ('SUPER_ADMIN', 'ADMIN', 'PROJECT_MANAGER', 'COST_MANAGER', 'FINANCE', 'AUDITOR')
  AND r.deleted_flag = 0;

INSERT IGNORE INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT 212200000 + r.id * 10000 + 2126, r.tenant_id, r.id, 2126
FROM sys_role r
JOIN sys_menu m ON m.tenant_id = r.tenant_id AND m.id = 2126 AND m.deleted_flag = 0
WHERE r.role_code = 'SUPER_ADMIN' AND r.deleted_flag = 0;
