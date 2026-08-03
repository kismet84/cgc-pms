-- Mainline 67: engineering tender records, immutable document versions,
-- bid-award project origin, and explicit cash-journal links.

ALTER TABLE bid_cost
    ADD COLUMN bid_section_name VARCHAR(200) NULL,
    ADD COLUMN tenderee_name VARCHAR(200) NULL,
    ADD COLUMN agency_name VARCHAR(200) NULL,
    ADD COLUMN project_location VARCHAR(300) NULL,
    ADD COLUMN tender_method VARCHAR(64) NULL,
    ADD COLUMN source_platform VARCHAR(200) NULL,
    ADD COLUMN external_bid_no VARCHAR(100) NULL,
    ADD COLUMN source_url VARCHAR(1000) NULL,
    ADD COLUMN owner_id BIGINT NULL,
    ADD COLUMN document_received_date DATE NULL,
    ADD COLUMN bid_deadline_at DATETIME NULL,
    ADD COLUMN opening_at DATETIME NULL,
    ADD COLUMN bid_valid_until DATE NULL,
    ADD COLUMN ceiling_price DECIMAL(18,2) NULL,
    ADD COLUMN final_bid_price DECIMAL(18,2) NULL,
    ADD COLUMN result_at DATETIME NULL,
    ADD COLUMN result_reason VARCHAR(1000) NULL,
    ADD COLUMN version INT NOT NULL DEFAULT 0;

UPDATE bid_cost SET bid_status = 'PREPARING' WHERE bid_status = 'BIDDING';
ALTER TABLE bid_cost ALTER COLUMN bid_status SET DEFAULT 'PREPARING';
ALTER TABLE bid_cost
    ADD UNIQUE KEY uk_bid_cost_tenant_id (tenant_id, id),
    ADD KEY idx_bid_record_filter (tenant_id, bid_status, owner_id, bid_deadline_at, deleted_flag),
    ADD CONSTRAINT ck_bid_cost_amounts CHECK (
        (ceiling_price IS NULL OR ceiling_price >= 0)
        AND (final_bid_price IS NULL OR final_bid_price >= 0)
    );

CREATE TABLE bid_document_version (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    bid_cost_id BIGINT NOT NULL,
    document_group VARCHAR(32) NOT NULL,
    document_type VARCHAR(64) NOT NULL,
    logical_name VARCHAR(200) NOT NULL,
    version_no INT NOT NULL,
    supersedes_id BIGINT NULL,
    sys_file_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    current_token BIGINT GENERATED ALWAYS AS (
        CASE WHEN deleted_flag = 0 AND status IN ('DRAFT', 'FINAL') THEN 0 ELSE id END
    ) STORED,
    content_sha256 CHAR(64) NOT NULL,
    source_name VARCHAR(200) NULL,
    source_url VARCHAR(1000) NULL,
    published_at DATETIME NULL,
    received_at DATETIME NULL,
    submitted_at DATETIME NULL,
    external_receipt_no VARCHAR(100) NULL,
    version INT NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bid_document_version (tenant_id, bid_cost_id, logical_name, version_no),
    UNIQUE KEY uk_bid_document_current (tenant_id, bid_cost_id, logical_name, current_token),
    KEY idx_bid_document_list (tenant_id, bid_cost_id, document_group, document_type, status),
    KEY idx_bid_document_file (tenant_id, sys_file_id),
    KEY idx_bid_document_supersedes (supersedes_id),
    CONSTRAINT fk_bid_document_bid FOREIGN KEY (tenant_id, bid_cost_id)
        REFERENCES bid_cost (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_bid_document_file FOREIGN KEY (tenant_id, sys_file_id)
        REFERENCES sys_file (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_bid_document_supersedes FOREIGN KEY (supersedes_id)
        REFERENCES bid_document_version (id) ON DELETE RESTRICT,
    CONSTRAINT ck_bid_document_group CHECK (document_group IN ('TENDER', 'SUBMISSION', 'RESULT')),
    CONSTRAINT ck_bid_document_status CHECK (status IN ('DRAFT', 'FINAL', 'SUPERSEDED', 'VOID')),
    CONSTRAINT ck_bid_document_version_no CHECK (version_no > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='投标资料不可覆盖版本事实';

ALTER TABLE pm_project
    ADD COLUMN source_bid_cost_id BIGINT NULL,
    ADD COLUMN initiation_basis VARCHAR(32) NULL,
    ADD UNIQUE KEY uk_pm_project_source_bid (tenant_id, source_bid_cost_id),
    ADD KEY idx_pm_project_initiation_basis (tenant_id, initiation_basis),
    ADD CONSTRAINT fk_pm_project_source_bid FOREIGN KEY (tenant_id, source_bid_cost_id)
        REFERENCES bid_cost (tenant_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_pm_project_initiation_basis CHECK (
        initiation_basis IS NULL OR initiation_basis = 'BID_AWARD'
    );

ALTER TABLE cost_subject
    ADD UNIQUE KEY uk_cost_subject_tenant_id (tenant_id, id);

ALTER TABLE cash_journal_entry
    ADD COLUMN bid_cost_id BIGINT NULL,
    ADD COLUMN cost_subject_id BIGINT NULL,
    ADD COLUMN bid_deposit_id BIGINT NULL,
    ADD COLUMN cost_subject_code_snapshot VARCHAR(64) NULL,
    ADD COLUMN cost_subject_name_snapshot VARCHAR(200) NULL,
    ADD KEY idx_cash_journal_bid (tenant_id, bid_cost_id, status, business_date),
    ADD KEY idx_cash_journal_cost_subject (tenant_id, cost_subject_id, status),
    ADD KEY idx_cash_journal_bid_deposit (tenant_id, bid_deposit_id, status),
    ADD CONSTRAINT fk_cash_journal_bid FOREIGN KEY (tenant_id, bid_cost_id)
        REFERENCES bid_cost (tenant_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_cash_journal_cost_subject FOREIGN KEY (tenant_id, cost_subject_id)
        REFERENCES cost_subject (tenant_id, id) ON DELETE RESTRICT;

-- Existing bid_deposit data predates tenant-safe foreign keys. Keep the new
-- link service-validated and indexed; do not guess or delete historical rows.
CREATE INDEX idx_bid_deposit_tenant_id ON bid_deposit (tenant_id, id);

INSERT INTO sys_role
    (id, tenant_id, role_code, role_name, role_type, status, data_scope,
     created_by, created_at, updated_by, updated_at, deleted_flag, remark, role_level)
SELECT 2690001, 0, 'MANAGEMENT', '管理层', 'BUSINESS', 'ENABLE', 'ALL',
       1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 0, '工程投标管理层角色', 1
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role WHERE tenant_id = 0 AND role_code = 'MANAGEMENT' AND deleted_flag = 0
);

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon,
     order_num, status, visible, created_by, updated_by, remark, created_at, updated_at, deleted_flag)
SELECT 2690100, 0, 0, '工程投标', 'DIR', '/engineering-tender', NULL, 'bid:query', 'fund',
       9, 'ENABLE', 1, 1, 1, '第67条主线工程投标目录', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 0 AND id = 2690100);

UPDATE sys_menu
SET parent_id = 2690100,
    menu_name = '投标记录',
    path = '/engineering-tender/records',
    component = 'engineering-tender/records',
    order_num = 1,
    updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = 0 AND id = 962 AND deleted_flag = 0;

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon,
     order_num, status, visible, created_by, updated_by, remark, created_at, updated_at, deleted_flag)
VALUES
    (2690101, 0, 2690100, '投标成本', 'MENU', '/engineering-tender/costs',
     'engineering-tender/costs', 'bid:cost:query', 'wallet', 2, 'ENABLE', 1, 1, 1,
     '现金日记账投标业务视图', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (2690111, 0, 962, '管理投标资料', 'BUTTON', NULL, NULL, 'bid:file:manage', NULL,
     10, 'ENABLE', 0, 1, 1, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (2690112, 0, 2690101, '维护投标成本', 'BUTTON', NULL, NULL, 'bid:cost:maintain', NULL,
     1, 'ENABLE', 0, 1, 1, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (2690113, 0, 2690101, '导出投标成本', 'BUTTON', NULL, NULL, 'bid:cost:export', NULL,
     2, 'ENABLE', 0, 1, 1, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT 269100000000000 + ROW_NUMBER() OVER (ORDER BY r.id, m.id), r.tenant_id, r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.tenant_id = r.tenant_id
 AND m.id IN (962, 963, 964, 965, 966, 2690100, 2690101, 2690111, 2690112, 2690113)
 AND m.deleted_flag = 0
WHERE r.tenant_id = 0
  AND r.role_code IN ('SUPER_ADMIN', 'MANAGEMENT')
  AND r.deleted_flag = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm
      WHERE rm.tenant_id = r.tenant_id AND rm.role_id = r.id AND rm.menu_id = m.id
  );
