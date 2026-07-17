ALTER TABLE accounting_entry
    ADD COLUMN review_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
    ADD COLUMN reviewed_by BIGINT NULL,
    ADD COLUMN reviewed_at DATETIME NULL,
    ADD COLUMN review_comment VARCHAR(500) NULL,
    ADD COLUMN posted_by BIGINT NULL,
    ADD COLUMN period_id BIGINT NULL,
    ADD COLUMN adjustment_flag TINYINT NOT NULL DEFAULT 0,
    ADD COLUMN original_entry_id BIGINT NULL;

UPDATE accounting_entry SET review_status='APPROVED' WHERE entry_status IN ('POSTED','REVERSED');
CREATE INDEX idx_accounting_entry_period ON accounting_entry(tenant_id,entry_date,entry_status,review_status);

CREATE TABLE finance_period (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, period_code VARCHAR(16) NOT NULL,
    fiscal_year INT NOT NULL, fiscal_month INT NOT NULL, start_date DATE NOT NULL, end_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN', last_check_at DATETIME NULL, issue_count INT NOT NULL DEFAULT 0,
    closed_by BIGINT NULL, closed_at DATETIME NULL, close_comment VARCHAR(500) NULL,
    reopened_by BIGINT NULL, reopened_at DATETIME NULL, reopen_reason VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0, created_by BIGINT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY(id), UNIQUE KEY uk_finance_period(tenant_id,fiscal_year,fiscal_month),
    CONSTRAINT ck_finance_period_month CHECK(fiscal_month BETWEEN 1 AND 12),
    CONSTRAINT ck_finance_period_status CHECK(status IN ('OPEN','CHECKING','CLOSED','REOPENED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE finance_period_check (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, period_id BIGINT NOT NULL,
    check_type VARCHAR(64) NOT NULL, check_status VARCHAR(16) NOT NULL, issue_count INT NOT NULL DEFAULT 0,
    detail_json LONGTEXT NULL, checked_by BIGINT NULL, checked_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(id), UNIQUE KEY uk_finance_period_check(tenant_id,period_id,check_type),
    CONSTRAINT fk_finance_period_check_period FOREIGN KEY(period_id) REFERENCES finance_period(id) ON DELETE RESTRICT,
    CONSTRAINT ck_finance_period_check_status CHECK(check_status IN ('PASS','FAIL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE finance_account_reconciliation (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, period_id BIGINT NOT NULL,
    account_type VARCHAR(16) NOT NULL, expected_amount DECIMAL(18,2) NOT NULL,
    ledger_amount DECIMAL(18,2) NOT NULL, difference_amount DECIMAL(18,2) NOT NULL,
    status VARCHAR(16) NOT NULL, detail_json LONGTEXT NULL, reconciled_by BIGINT NULL,
    reconciled_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(id), UNIQUE KEY uk_finance_account_recon(tenant_id,period_id,account_type),
    CONSTRAINT fk_finance_account_recon_period FOREIGN KEY(period_id) REFERENCES finance_period(id) ON DELETE RESTRICT,
    CONSTRAINT ck_finance_account_recon_type CHECK(account_type IN ('AR','AP')),
    CONSTRAINT ck_finance_account_recon_status CHECK(status IN ('MATCHED','EXCEPTION'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE finance_bank_reconciliation (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, period_id BIGINT NOT NULL,
    bank_receipt_id BIGINT NOT NULL, direction VARCHAR(8) NOT NULL, business_type VARCHAR(32) NULL,
    business_id BIGINT NULL, cash_journal_id BIGINT NULL, bank_amount DECIMAL(18,2) NOT NULL,
    business_amount DECIMAL(18,2) NOT NULL DEFAULT 0, difference_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL, match_method VARCHAR(16) NOT NULL DEFAULT 'AUTO',
    resolved_by BIGINT NULL, resolved_at DATETIME NULL, resolution_note VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(id), UNIQUE KEY uk_finance_bank_recon(tenant_id,period_id,bank_receipt_id),
    CONSTRAINT fk_finance_bank_recon_period FOREIGN KEY(period_id) REFERENCES finance_period(id) ON DELETE RESTRICT,
    CONSTRAINT fk_finance_bank_recon_receipt FOREIGN KEY(bank_receipt_id) REFERENCES bank_receipt(id) ON DELETE RESTRICT,
    CONSTRAINT fk_finance_bank_recon_journal FOREIGN KEY(cash_journal_id) REFERENCES cash_journal_entry(id) ON DELETE RESTRICT,
    CONSTRAINT ck_finance_bank_recon_status CHECK(status IN ('MATCHED','EXCEPTION','RESOLVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE accounting_entry
    ADD CONSTRAINT fk_accounting_entry_period FOREIGN KEY(period_id) REFERENCES finance_period(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_accounting_entry_original FOREIGN KEY(original_entry_id) REFERENCES accounting_entry(id) ON DELETE RESTRICT;

INSERT IGNORE INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible) VALUES
(1132,0,906,'财务月结','MENU','/financial-close','financial-close/index','finance:close:query','account-book',6,'ENABLE',1),
(1133,0,1132,'复核会计凭证','BUTTON',NULL,NULL,'accounting:review',NULL,1,'ENABLE',0),
(1134,0,1132,'凭证过账','BUTTON',NULL,NULL,'accounting:post',NULL,2,'ENABLE',0),
(1135,0,1132,'运行月结检查','BUTTON',NULL,NULL,'finance:close:check',NULL,3,'ENABLE',0),
(1136,0,1132,'执行月结','BUTTON',NULL,NULL,'finance:close:close',NULL,4,'ENABLE',0),
(1137,0,1132,'反结账','BUTTON',NULL,NULL,'finance:close:reopen',NULL,5,'ENABLE',0),
(1138,0,1132,'处理对账差异','BUTTON',NULL,NULL,'finance:close:reconcile',NULL,6,'ENABLE',0),
(1139,0,1132,'创建调整凭证','BUTTON',NULL,NULL,'accounting:adjustment:add',NULL,7,'ENABLE',0);

INSERT IGNORE INTO sys_role_menu(id,role_id,menu_id)
SELECT 192000000+r.id*10000+m.id,r.id,m.id FROM sys_role r JOIN sys_menu m ON m.id BETWEEN 1132 AND 1139
WHERE r.role_code IN ('SUPER_ADMIN','ADMIN','FINANCE') AND r.deleted_flag=0;
INSERT IGNORE INTO sys_role_menu(id,role_id,menu_id)
SELECT 192900000+r.id*10000+1132,r.id,1132 FROM sys_role r
WHERE r.role_code='AUDITOR' AND r.deleted_flag=0;
