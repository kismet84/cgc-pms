-- V136 H2: cash/bank fund accounts, cash journal entries, immutable change log, and permissions.

CREATE TABLE fund_account (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    account_code VARCHAR(64) NOT NULL,
    account_name VARCHAR(128) NOT NULL,
    account_type VARCHAR(16) NOT NULL,
    bank_name VARCHAR(128) NULL,
    bank_account_no VARCHAR(128) NULL,
    opening_date DATE NOT NULL,
    opening_balance DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    enabled_flag TINYINT NOT NULL DEFAULT 1,
    version INT NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_fund_account_code UNIQUE (tenant_id, account_code, deleted_flag),
    CONSTRAINT ck_fund_account_type CHECK (account_type IN ('CASH', 'BANK')),
    CONSTRAINT ck_fund_account_opening_balance CHECK (opening_balance >= 0)
);
CREATE INDEX idx_fund_account_tenant_enabled ON fund_account(tenant_id, enabled_flag, deleted_flag);

CREATE TABLE cash_journal_entry (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    entry_no VARCHAR(64) NOT NULL,
    account_id BIGINT NULL,
    direction VARCHAR(8) NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    business_date DATE NOT NULL,
    counterparty_name VARCHAR(200) NULL,
    summary VARCHAR(500) NOT NULL,
    project_id BIGINT NULL,
    contract_id BIGINT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_id BIGINT NULL,
    status VARCHAR(32) NOT NULL,
    closure_due_at TIMESTAMP NOT NULL,
    archived_by BIGINT NULL,
    archived_at TIMESTAMP NULL,
    reverse_of_entry_id BIGINT NULL,
    reversal_entry_id BIGINT NULL,
    version INT NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_cash_journal_entry_no UNIQUE (tenant_id, entry_no, deleted_flag),
    CONSTRAINT uk_cash_journal_source UNIQUE (tenant_id, source_type, source_id, deleted_flag),
    CONSTRAINT ck_cash_journal_direction CHECK (direction IN ('IN', 'OUT')),
    CONSTRAINT ck_cash_journal_amount CHECK (amount > 0),
    CONSTRAINT ck_cash_journal_status CHECK (status IN ('DRAFT', 'PENDING_ARCHIVE', 'ARCHIVED', 'REVERSED')),
    CONSTRAINT ck_cash_journal_source_type CHECK (source_type IN ('MANUAL', 'PAY_RECORD', 'REVERSAL'))
);
CREATE INDEX idx_cash_journal_account_date ON cash_journal_entry(tenant_id, account_id, business_date, id);
CREATE INDEX idx_cash_journal_closure ON cash_journal_entry(tenant_id, status, closure_due_at);
CREATE INDEX idx_cash_journal_project_contract ON cash_journal_entry(tenant_id, project_id, contract_id);

CREATE TABLE cash_journal_change_log (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    journal_entry_id BIGINT NOT NULL,
    action VARCHAR(32) NOT NULL,
    reason VARCHAR(500) NULL,
    before_snapshot CLOB NULL,
    after_snapshot CLOB NULL,
    operator_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX idx_cash_journal_change_entry ON cash_journal_change_log(tenant_id, journal_entry_id, created_at);

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
SELECT 952, 0, 906, '资金日记账', 'MENU', '/cash-journal', 'cash-journal/index', 'cashbook:journal:query', 'account-book', 4, 'ENABLE', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 952);
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
SELECT 953, 0, 952, '维护资金流水', 'BUTTON', NULL, NULL, 'cashbook:journal:maintain', NULL, 1, 'ENABLE', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 953);
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
SELECT 954, 0, 952, '导出资金流水', 'BUTTON', NULL, NULL, 'cashbook:journal:export', NULL, 2, 'ENABLE', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 954);
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
SELECT 955, 0, 952, '管理资金账户', 'BUTTON', NULL, NULL, 'cashbook:account:manage', NULL, 3, 'ENABLE', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 955);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 136000 + m.id, 1, m.id FROM sys_menu m
WHERE m.id BETWEEN 952 AND 955 AND m.deleted_flag = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 137000 + m.id, 6, m.id FROM sys_menu m
WHERE m.id IN (952, 953, 954) AND m.deleted_flag = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 6 AND rm.menu_id = m.id);
