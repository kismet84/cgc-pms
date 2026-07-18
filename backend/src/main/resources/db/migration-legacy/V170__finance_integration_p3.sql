ALTER TABLE accounting_entry ADD COLUMN external_sync_status VARCHAR(32) NULL;
ALTER TABLE accounting_entry ADD COLUMN external_sync_at DATETIME NULL;

CREATE TABLE finance_integration_endpoint (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, endpoint_type VARCHAR(32) NOT NULL,
    endpoint_code VARCHAR(64) NOT NULL, endpoint_name VARCHAR(200) NOT NULL,
    base_url VARCHAR(500) NULL, credential_ref VARCHAR(200) NULL,
    callback_secret_hash VARCHAR(64) NULL, enabled_flag TINYINT NOT NULL DEFAULT 1,
    config_json LONGTEXT NULL, version INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE KEY uk_finance_endpoint_code (tenant_id, endpoint_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE finance_integration_message (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, endpoint_id BIGINT NOT NULL,
    direction VARCHAR(16) NOT NULL, message_type VARCHAR(64) NOT NULL,
    business_type VARCHAR(64) NOT NULL, business_id BIGINT NULL,
    idempotency_key VARCHAR(128) NOT NULL, status VARCHAR(32) NOT NULL,
    payload_json LONGTEXT NOT NULL, response_json LONGTEXT NULL, retry_count INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME NULL, processed_at DATETIME NULL, error_message VARCHAR(1000) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE KEY uk_finance_integration_key (tenant_id, endpoint_id, direction, idempotency_key),
    KEY idx_finance_integration_dispatch (tenant_id, status, next_retry_at),
    CONSTRAINT fk_finance_message_endpoint FOREIGN KEY (endpoint_id) REFERENCES finance_integration_endpoint(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE bank_receipt (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, endpoint_id BIGINT NOT NULL,
    bank_txn_no VARCHAR(128) NOT NULL, account_no_masked VARCHAR(64) NULL,
    transaction_time DATETIME NOT NULL, direction VARCHAR(8) NOT NULL, amount DECIMAL(18,2) NOT NULL,
    counterparty_name VARCHAR(200) NULL, purpose_text VARCHAR(500) NULL,
    match_status VARCHAR(32) NOT NULL DEFAULT 'UNMATCHED', pay_record_id BIGINT NULL,
    cash_journal_id BIGINT NULL, confidence DECIMAL(5,4) NULL, raw_payload_json LONGTEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, matched_at DATETIME NULL,
    PRIMARY KEY (id), UNIQUE KEY uk_bank_receipt_txn (tenant_id, endpoint_id, bank_txn_no),
    KEY idx_bank_receipt_match (tenant_id, match_status, transaction_time),
    CONSTRAINT fk_bank_receipt_endpoint FOREIGN KEY (endpoint_id) REFERENCES finance_integration_endpoint(id) ON DELETE RESTRICT,
    CONSTRAINT fk_bank_receipt_pay FOREIGN KEY (pay_record_id) REFERENCES pay_record(id) ON DELETE RESTRICT,
    CONSTRAINT fk_bank_receipt_journal FOREIGN KEY (cash_journal_id) REFERENCES cash_journal_entry(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE cash_forecast (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, project_id BIGINT NULL,
    forecast_date DATE NOT NULL, scenario VARCHAR(32) NOT NULL, inflow_amount DECIMAL(18,2) NOT NULL,
    outflow_amount DECIMAL(18,2) NOT NULL, financing_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    source_type VARCHAR(32) NOT NULL, source_id BIGINT NULL, confidence DECIMAL(5,4) NOT NULL DEFAULT 1,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', version INT NOT NULL DEFAULT 0,
    created_by BIGINT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), KEY idx_cash_forecast_date (tenant_id, scenario, forecast_date),
    CONSTRAINT fk_cash_forecast_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE fund_pool (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, pool_code VARCHAR(64) NOT NULL,
    pool_name VARCHAR(200) NOT NULL, currency_code VARCHAR(8) NOT NULL DEFAULT 'CNY',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', control_mode VARCHAR(32) NOT NULL DEFAULT 'QUOTA',
    version INT NOT NULL DEFAULT 0, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE KEY uk_fund_pool_code (tenant_id, pool_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE fund_pool_member (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, pool_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL, fund_account_id BIGINT NOT NULL, quota_amount DECIMAL(18,2) NOT NULL,
    occupied_amount DECIMAL(18,2) NOT NULL DEFAULT 0, status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    version INT NOT NULL DEFAULT 0, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE KEY uk_fund_pool_member (tenant_id, pool_id, company_id, fund_account_id),
    CONSTRAINT fk_fund_pool_member_pool FOREIGN KEY (pool_id) REFERENCES fund_pool(id) ON DELETE RESTRICT,
    CONSTRAINT fk_fund_pool_member_account FOREIGN KEY (fund_account_id) REFERENCES fund_account(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE fund_pool_transaction (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, pool_id BIGINT NOT NULL,
    from_member_id BIGINT NULL, to_member_id BIGINT NULL, transaction_type VARCHAR(32) NOT NULL,
    amount DECIMAL(18,2) NOT NULL, status VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL, external_txn_no VARCHAR(128) NULL,
    occurred_at DATETIME NOT NULL, created_by BIGINT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE KEY uk_fund_pool_txn_key (tenant_id, idempotency_key),
    KEY idx_fund_pool_txn (tenant_id, pool_id, occurred_at),
    CONSTRAINT fk_fund_pool_txn_pool FOREIGN KEY (pool_id) REFERENCES fund_pool(id) ON DELETE RESTRICT,
    CONSTRAINT fk_fund_pool_txn_from FOREIGN KEY (from_member_id) REFERENCES fund_pool_member(id) ON DELETE RESTRICT,
    CONSTRAINT fk_fund_pool_txn_to FOREIGN KEY (to_member_id) REFERENCES fund_pool_member(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
VALUES
 (1066,0,1060,'外部财务集成','BUTTON',NULL,NULL,'finance:integration:maintain',NULL,5,'ENABLE',1),
 (1067,0,1060,'资金池维护','BUTTON',NULL,NULL,'finance:pool:maintain',NULL,6,'ENABLE',1);

INSERT IGNORE INTO sys_role_menu(id,role_id,menu_id)
SELECT 170000000+r.id*10000+m.id,r.id,m.id FROM sys_role r JOIN sys_menu m ON m.id=1060
WHERE r.role_code IN ('SUPER_ADMIN','ADMIN','FINANCE','PROJECT_MANAGER','COST_MANAGER','AUDITOR') AND r.deleted_flag=0;
INSERT IGNORE INTO sys_role_menu(id,role_id,menu_id)
SELECT 171000000+r.id*10000+m.id,r.id,m.id FROM sys_role r JOIN sys_menu m ON m.id IN (1061,1062,1063,1064,1066,1067)
WHERE r.role_code IN ('SUPER_ADMIN','ADMIN','FINANCE') AND r.deleted_flag=0;
INSERT IGNORE INTO sys_role_menu(id,role_id,menu_id)
SELECT 172000000+r.id*10000+m.id,r.id,m.id FROM sys_role r JOIN sys_menu m ON m.id=1065
WHERE r.role_code IN ('SUPER_ADMIN','ADMIN','FINANCE','AUDITOR') AND r.deleted_flag=0;
