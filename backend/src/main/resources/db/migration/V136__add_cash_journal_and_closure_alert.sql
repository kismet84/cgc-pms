-- V136: cash/bank fund accounts, cash journal entries, immutable change log, and permissions.

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE fund_account (
    id BIGINT NOT NULL COMMENT '资金账户ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    account_code VARCHAR(64) NOT NULL COMMENT '租户内账户编码',
    account_name VARCHAR(128) NOT NULL COMMENT '账户名称',
    account_type VARCHAR(16) NOT NULL COMMENT 'CASH/BANK',
    bank_name VARCHAR(128) NULL COMMENT '开户行',
    bank_account_no VARCHAR(128) NULL COMMENT '银行账号',
    opening_date DATE NOT NULL COMMENT '期初日期',
    opening_balance DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '期初余额',
    enabled_flag TINYINT NOT NULL DEFAULT 1 COMMENT '1启用，0停用',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_fund_account_code (tenant_id, account_code, deleted_flag),
    KEY idx_fund_account_tenant_enabled (tenant_id, enabled_flag, deleted_flag),
    CONSTRAINT ck_fund_account_type CHECK (account_type IN ('CASH', 'BANK')),
    CONSTRAINT ck_fund_account_opening_balance CHECK (opening_balance >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='企业资金账户';

CREATE TABLE cash_journal_entry (
    id BIGINT NOT NULL COMMENT '日记账流水ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    entry_no VARCHAR(64) NOT NULL COMMENT '流水号',
    account_id BIGINT NULL COMMENT '资金账户ID',
    direction VARCHAR(8) NOT NULL COMMENT 'IN/OUT',
    amount DECIMAL(18,2) NOT NULL COMMENT '金额',
    business_date DATE NOT NULL COMMENT '业务日期',
    counterparty_name VARCHAR(200) NULL COMMENT '往来单位',
    summary VARCHAR(500) NOT NULL COMMENT '摘要',
    project_id BIGINT NULL,
    contract_id BIGINT NULL,
    source_type VARCHAR(32) NOT NULL COMMENT 'MANUAL/PAY_RECORD/REVERSAL',
    source_id BIGINT NULL,
    status VARCHAR(32) NOT NULL COMMENT 'DRAFT/PENDING_ARCHIVE/ARCHIVED/REVERSED',
    closure_due_at DATETIME NOT NULL,
    archived_by BIGINT NULL,
    archived_at DATETIME NULL,
    reverse_of_entry_id BIGINT NULL,
    reversal_entry_id BIGINT NULL,
    version INT NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cash_journal_entry_no (tenant_id, entry_no, deleted_flag),
    UNIQUE KEY uk_cash_journal_source (tenant_id, source_type, source_id, deleted_flag),
    KEY idx_cash_journal_account_date (tenant_id, account_id, business_date, id),
    KEY idx_cash_journal_closure (tenant_id, status, closure_due_at),
    KEY idx_cash_journal_project_contract (tenant_id, project_id, contract_id),
    CONSTRAINT ck_cash_journal_direction CHECK (direction IN ('IN', 'OUT')),
    CONSTRAINT ck_cash_journal_amount CHECK (amount > 0),
    CONSTRAINT ck_cash_journal_status CHECK (status IN ('DRAFT', 'PENDING_ARCHIVE', 'ARCHIVED', 'REVERSED')),
    CONSTRAINT ck_cash_journal_source_type CHECK (source_type IN ('MANUAL', 'PAY_RECORD', 'REVERSAL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资金日记账流水';

CREATE TABLE cash_journal_change_log (
    id BIGINT NOT NULL COMMENT '变更日志ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    journal_entry_id BIGINT NOT NULL COMMENT '日记账流水ID',
    action VARCHAR(32) NOT NULL COMMENT 'REOPEN/UPDATE_AFTER_REOPEN/REARCHIVE/REVERSE',
    reason VARCHAR(500) NULL COMMENT '变更原因',
    before_snapshot JSON NULL COMMENT '变更前快照',
    after_snapshot JSON NULL COMMENT '变更后快照',
    operator_id BIGINT NOT NULL COMMENT '操作人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_cash_journal_change_entry (tenant_id, journal_entry_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资金日记账不可变变更日志';

INSERT IGNORE INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
VALUES
    (952, 0, 906, '资金日记账', 'MENU', '/cash-journal', 'cash-journal/index', 'cashbook:journal:query', 'account-book', 4, 'ENABLE', 1),
    (953, 0, 952, '维护资金流水', 'BUTTON', NULL, NULL, 'cashbook:journal:maintain', NULL, 1, 'ENABLE', 0),
    (954, 0, 952, '导出资金流水', 'BUTTON', NULL, NULL, 'cashbook:journal:export', NULL, 2, 'ENABLE', 0),
    (955, 0, 952, '管理资金账户', 'BUTTON', NULL, NULL, 'cashbook:account:manage', NULL, 3, 'ENABLE', 0);

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id)
SELECT 136000 + id, 1, id FROM sys_menu WHERE id BETWEEN 952 AND 955 AND deleted_flag = 0;

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id)
SELECT 137000 + id, 6, id FROM sys_menu
WHERE id IN (952, 953, 954) AND deleted_flag = 0;

SET FOREIGN_KEY_CHECKS = 1;
