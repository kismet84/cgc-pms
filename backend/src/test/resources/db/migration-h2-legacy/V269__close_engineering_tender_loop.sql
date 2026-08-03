-- Legacy H2 counterpart of V269__close_engineering_tender_loop.sql.
ALTER TABLE bid_cost ADD COLUMN IF NOT EXISTS bid_section_name VARCHAR(200);
ALTER TABLE bid_cost ADD COLUMN IF NOT EXISTS tenderee_name VARCHAR(200);
ALTER TABLE bid_cost ADD COLUMN IF NOT EXISTS agency_name VARCHAR(200);
ALTER TABLE bid_cost ADD COLUMN IF NOT EXISTS project_location VARCHAR(300);
ALTER TABLE bid_cost ADD COLUMN IF NOT EXISTS tender_method VARCHAR(64);
ALTER TABLE bid_cost ADD COLUMN IF NOT EXISTS source_platform VARCHAR(200);
ALTER TABLE bid_cost ADD COLUMN IF NOT EXISTS external_bid_no VARCHAR(100);
ALTER TABLE bid_cost ADD COLUMN IF NOT EXISTS source_url VARCHAR(1000);
ALTER TABLE bid_cost ADD COLUMN IF NOT EXISTS owner_id BIGINT;
ALTER TABLE bid_cost ADD COLUMN IF NOT EXISTS document_received_date DATE;
ALTER TABLE bid_cost ADD COLUMN IF NOT EXISTS bid_deadline_at TIMESTAMP;
ALTER TABLE bid_cost ADD COLUMN IF NOT EXISTS opening_at TIMESTAMP;
ALTER TABLE bid_cost ADD COLUMN IF NOT EXISTS bid_valid_until DATE;
ALTER TABLE bid_cost ADD COLUMN IF NOT EXISTS ceiling_price DECIMAL(18,2);
ALTER TABLE bid_cost ADD COLUMN IF NOT EXISTS final_bid_price DECIMAL(18,2);
ALTER TABLE bid_cost ADD COLUMN IF NOT EXISTS result_at TIMESTAMP;
ALTER TABLE bid_cost ADD COLUMN IF NOT EXISTS result_reason VARCHAR(1000);
ALTER TABLE bid_cost ADD COLUMN IF NOT EXISTS version INT DEFAULT 0 NOT NULL;
UPDATE bid_cost SET bid_status = 'PREPARING' WHERE bid_status = 'BIDDING';
ALTER TABLE bid_cost ALTER COLUMN bid_status SET DEFAULT 'PREPARING';
CREATE UNIQUE INDEX IF NOT EXISTS uk_bid_cost_tenant_id ON bid_cost (tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_bid_record_filter ON bid_cost (tenant_id, bid_status, owner_id, bid_deadline_at, deleted_flag);

CREATE TABLE IF NOT EXISTS bid_document_version (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    bid_cost_id BIGINT NOT NULL,
    document_group VARCHAR(32) NOT NULL,
    document_type VARCHAR(64) NOT NULL,
    logical_name VARCHAR(200) NOT NULL,
    version_no INT NOT NULL,
    supersedes_id BIGINT,
    sys_file_id BIGINT NOT NULL,
    status VARCHAR(20) DEFAULT 'DRAFT' NOT NULL,
    current_token BIGINT AS (CASE WHEN deleted_flag = 0 AND status IN ('DRAFT', 'FINAL') THEN 0 ELSE id END),
    content_sha256 CHAR(64) NOT NULL,
    source_name VARCHAR(200),
    source_url VARCHAR(1000),
    published_at TIMESTAMP,
    received_at TIMESTAMP,
    submitted_at TIMESTAMP,
    external_receipt_no VARCHAR(100),
    version INT DEFAULT 0 NOT NULL,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by BIGINT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted_flag TINYINT DEFAULT 0 NOT NULL,
    remark VARCHAR(500),
    CONSTRAINT fk_bid_document_bid FOREIGN KEY (tenant_id, bid_cost_id) REFERENCES bid_cost (tenant_id, id),
    CONSTRAINT fk_bid_document_file FOREIGN KEY (tenant_id, sys_file_id) REFERENCES sys_file (tenant_id, id),
    CONSTRAINT fk_bid_document_supersedes FOREIGN KEY (supersedes_id) REFERENCES bid_document_version (id),
    CONSTRAINT ck_bid_document_group CHECK (document_group IN ('TENDER', 'SUBMISSION', 'RESULT')),
    CONSTRAINT ck_bid_document_status CHECK (status IN ('DRAFT', 'FINAL', 'SUPERSEDED', 'VOID')),
    CONSTRAINT ck_bid_document_version_no CHECK (version_no > 0)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_bid_document_version ON bid_document_version (tenant_id, bid_cost_id, logical_name, version_no);
CREATE UNIQUE INDEX IF NOT EXISTS uk_bid_document_current ON bid_document_version (tenant_id, bid_cost_id, logical_name, current_token);
CREATE INDEX IF NOT EXISTS idx_bid_document_list ON bid_document_version (tenant_id, bid_cost_id, document_group, document_type, status);

ALTER TABLE pm_project ADD COLUMN IF NOT EXISTS source_bid_cost_id BIGINT;
ALTER TABLE pm_project ADD COLUMN IF NOT EXISTS initiation_basis VARCHAR(32);
CREATE UNIQUE INDEX IF NOT EXISTS uk_pm_project_source_bid ON pm_project (tenant_id, source_bid_cost_id);
CREATE INDEX IF NOT EXISTS idx_pm_project_initiation_basis ON pm_project (tenant_id, initiation_basis);
ALTER TABLE pm_project ADD CONSTRAINT fk_pm_project_source_bid FOREIGN KEY (tenant_id, source_bid_cost_id) REFERENCES bid_cost (tenant_id, id);

ALTER TABLE cost_subject ADD CONSTRAINT uk_cost_subject_tenant_id UNIQUE (tenant_id, id);

ALTER TABLE cash_journal_entry ADD COLUMN IF NOT EXISTS bid_cost_id BIGINT;
ALTER TABLE cash_journal_entry ADD COLUMN IF NOT EXISTS cost_subject_id BIGINT;
ALTER TABLE cash_journal_entry ADD COLUMN IF NOT EXISTS bid_deposit_id BIGINT;
ALTER TABLE cash_journal_entry ADD COLUMN IF NOT EXISTS cost_subject_code_snapshot VARCHAR(64);
ALTER TABLE cash_journal_entry ADD COLUMN IF NOT EXISTS cost_subject_name_snapshot VARCHAR(200);
CREATE INDEX IF NOT EXISTS idx_cash_journal_bid ON cash_journal_entry (tenant_id, bid_cost_id, status, business_date);
CREATE INDEX IF NOT EXISTS idx_cash_journal_cost_subject ON cash_journal_entry (tenant_id, cost_subject_id, status);
CREATE INDEX IF NOT EXISTS idx_cash_journal_bid_deposit ON cash_journal_entry (tenant_id, bid_deposit_id, status);
ALTER TABLE cash_journal_entry ADD CONSTRAINT fk_cash_journal_bid FOREIGN KEY (tenant_id, bid_cost_id) REFERENCES bid_cost (tenant_id, id);
ALTER TABLE cash_journal_entry ADD CONSTRAINT fk_cash_journal_cost_subject FOREIGN KEY (tenant_id, cost_subject_id) REFERENCES cost_subject (tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_bid_deposit_tenant_id ON bid_deposit (tenant_id, id);

INSERT INTO sys_role
    (id, tenant_id, role_code, role_name, role_type, status, data_scope,
     created_by, created_at, updated_by, updated_at, deleted_flag, remark, role_level)
SELECT 2690001, 0, 'MANAGEMENT', '管理层', 'BUSINESS', 'ENABLE', 'ALL',
       1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 0, '工程投标管理层角色', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE tenant_id = 0 AND role_code = 'MANAGEMENT' AND deleted_flag = 0);

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon,
     order_num, status, visible, created_by, updated_by, remark, created_at, updated_at, deleted_flag)
SELECT 2690100, 0, 0, '工程投标', 'DIR', '/engineering-tender', NULL, 'bid:query', 'fund',
       9, 'ENABLE', 1, 1, 1, '第67条主线工程投标目录', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 0 AND id = 2690100);

UPDATE sys_menu SET parent_id=2690100, menu_name='投标记录', path='/engineering-tender/records',
    component='engineering-tender/records', order_num=1, updated_at=CURRENT_TIMESTAMP
WHERE tenant_id=0 AND id=962 AND deleted_flag=0;

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon,
     order_num, status, visible, created_by, updated_by, remark, created_at, updated_at, deleted_flag)
SELECT * FROM (VALUES
    (2690101, 0, 2690100, '投标成本', 'MENU', '/engineering-tender/costs', 'engineering-tender/costs', 'bid:cost:query', 'wallet', 2, 'ENABLE', 1, 1, 1, '现金日记账投标业务视图', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (2690111, 0, 962, '管理投标资料', 'BUTTON', NULL, NULL, 'bid:file:manage', NULL, 10, 'ENABLE', 0, 1, 1, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (2690112, 0, 2690101, '维护投标成本', 'BUTTON', NULL, NULL, 'bid:cost:maintain', NULL, 1, 'ENABLE', 0, 1, 1, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (2690113, 0, 2690101, '导出投标成本', 'BUTTON', NULL, NULL, 'bid:cost:export', NULL, 2, 'ENABLE', 0, 1, 1, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
) AS seed(id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible, created_by, updated_by, remark, created_at, updated_at, deleted_flag)
WHERE NOT EXISTS (SELECT 1 FROM sys_menu existing WHERE existing.tenant_id=seed.tenant_id AND existing.id=seed.id);

INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT 269100000000000 + ROW_NUMBER() OVER (ORDER BY r.id, m.id), r.tenant_id, r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.tenant_id=r.tenant_id
 AND m.id IN (962,963,964,965,966,2690100,2690101,2690111,2690112,2690113)
 AND m.deleted_flag=0
WHERE r.tenant_id=0 AND r.role_code IN ('SUPER_ADMIN','MANAGEMENT') AND r.deleted_flag=0
 AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.tenant_id=r.tenant_id AND rm.role_id=r.id AND rm.menu_id=m.id);
