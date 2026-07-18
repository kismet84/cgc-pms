ALTER TABLE accounting_entry ADD COLUMN external_sync_status VARCHAR(32);
ALTER TABLE accounting_entry ADD COLUMN external_sync_at TIMESTAMP;

CREATE TABLE finance_integration_endpoint (
    id BIGINT PRIMARY KEY, tenant_id BIGINT DEFAULT 0 NOT NULL, endpoint_type VARCHAR(32) NOT NULL,
    endpoint_code VARCHAR(64) NOT NULL, endpoint_name VARCHAR(200) NOT NULL, base_url VARCHAR(500),
    credential_ref VARCHAR(200), callback_secret_hash VARCHAR(64), enabled_flag SMALLINT DEFAULT 1 NOT NULL,
    config_json CLOB, version INT DEFAULT 0 NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uk_finance_endpoint_code UNIQUE (tenant_id, endpoint_code)
);
CREATE TABLE finance_integration_message (
    id BIGINT PRIMARY KEY, tenant_id BIGINT DEFAULT 0 NOT NULL, endpoint_id BIGINT NOT NULL,
    direction VARCHAR(16) NOT NULL, message_type VARCHAR(64) NOT NULL, business_type VARCHAR(64) NOT NULL,
    business_id BIGINT, idempotency_key VARCHAR(128) NOT NULL, status VARCHAR(32) NOT NULL,
    payload_json CLOB NOT NULL, response_json CLOB, retry_count INT DEFAULT 0 NOT NULL,
    next_retry_at TIMESTAMP, processed_at TIMESTAMP, error_message VARCHAR(1000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uk_finance_integration_key UNIQUE (tenant_id, endpoint_id, direction, idempotency_key),
    CONSTRAINT fk_finance_message_endpoint FOREIGN KEY (endpoint_id) REFERENCES finance_integration_endpoint(id)
);
CREATE INDEX idx_finance_integration_dispatch ON finance_integration_message(tenant_id, status, next_retry_at);

CREATE TABLE bank_receipt (
    id BIGINT PRIMARY KEY, tenant_id BIGINT DEFAULT 0 NOT NULL, endpoint_id BIGINT NOT NULL,
    bank_txn_no VARCHAR(128) NOT NULL, account_no_masked VARCHAR(64), transaction_time TIMESTAMP NOT NULL,
    direction VARCHAR(8) NOT NULL, amount DECIMAL(18,2) NOT NULL, counterparty_name VARCHAR(200),
    purpose_text VARCHAR(500), match_status VARCHAR(32) DEFAULT 'UNMATCHED' NOT NULL,
    pay_record_id BIGINT, cash_journal_id BIGINT, confidence DECIMAL(5,4), raw_payload_json CLOB NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL, matched_at TIMESTAMP,
    CONSTRAINT uk_bank_receipt_txn UNIQUE (tenant_id, endpoint_id, bank_txn_no),
    CONSTRAINT fk_bank_receipt_endpoint FOREIGN KEY (endpoint_id) REFERENCES finance_integration_endpoint(id),
    CONSTRAINT fk_bank_receipt_pay FOREIGN KEY (pay_record_id) REFERENCES pay_record(id),
    CONSTRAINT fk_bank_receipt_journal FOREIGN KEY (cash_journal_id) REFERENCES cash_journal_entry(id)
);
CREATE INDEX idx_bank_receipt_match ON bank_receipt(tenant_id, match_status, transaction_time);

CREATE TABLE cash_forecast (
    id BIGINT PRIMARY KEY, tenant_id BIGINT DEFAULT 0 NOT NULL, project_id BIGINT,
    forecast_date DATE NOT NULL, scenario VARCHAR(32) NOT NULL, inflow_amount DECIMAL(18,2) NOT NULL,
    outflow_amount DECIMAL(18,2) NOT NULL, financing_amount DECIMAL(18,2) DEFAULT 0 NOT NULL,
    source_type VARCHAR(32) NOT NULL, source_id BIGINT, confidence DECIMAL(5,4) DEFAULT 1 NOT NULL,
    status VARCHAR(32) DEFAULT 'ACTIVE' NOT NULL, version INT DEFAULT 0 NOT NULL,
    created_by BIGINT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_cash_forecast_project FOREIGN KEY (project_id) REFERENCES pm_project(id)
);
CREATE INDEX idx_cash_forecast_date ON cash_forecast(tenant_id, scenario, forecast_date);

CREATE TABLE fund_pool (
    id BIGINT PRIMARY KEY, tenant_id BIGINT DEFAULT 0 NOT NULL, pool_code VARCHAR(64) NOT NULL,
    pool_name VARCHAR(200) NOT NULL, currency_code VARCHAR(8) DEFAULT 'CNY' NOT NULL,
    status VARCHAR(32) DEFAULT 'ACTIVE' NOT NULL, control_mode VARCHAR(32) DEFAULT 'QUOTA' NOT NULL,
    version INT DEFAULT 0 NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uk_fund_pool_code UNIQUE (tenant_id, pool_code)
);
CREATE TABLE fund_pool_member (
    id BIGINT PRIMARY KEY, tenant_id BIGINT DEFAULT 0 NOT NULL, pool_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL, fund_account_id BIGINT NOT NULL, quota_amount DECIMAL(18,2) NOT NULL,
    occupied_amount DECIMAL(18,2) DEFAULT 0 NOT NULL, status VARCHAR(32) DEFAULT 'ACTIVE' NOT NULL,
    version INT DEFAULT 0 NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uk_fund_pool_member UNIQUE (tenant_id, pool_id, company_id, fund_account_id),
    CONSTRAINT fk_fund_pool_member_pool FOREIGN KEY (pool_id) REFERENCES fund_pool(id),
    CONSTRAINT fk_fund_pool_member_account FOREIGN KEY (fund_account_id) REFERENCES fund_account(id)
);
CREATE TABLE fund_pool_transaction (
    id BIGINT PRIMARY KEY, tenant_id BIGINT DEFAULT 0 NOT NULL, pool_id BIGINT NOT NULL,
    from_member_id BIGINT, to_member_id BIGINT, transaction_type VARCHAR(32) NOT NULL,
    amount DECIMAL(18,2) NOT NULL, status VARCHAR(32) NOT NULL, idempotency_key VARCHAR(128) NOT NULL,
    external_txn_no VARCHAR(128), occurred_at TIMESTAMP NOT NULL, created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uk_fund_pool_txn_key UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT fk_fund_pool_txn_pool FOREIGN KEY (pool_id) REFERENCES fund_pool(id),
    CONSTRAINT fk_fund_pool_txn_from FOREIGN KEY (from_member_id) REFERENCES fund_pool_member(id),
    CONSTRAINT fk_fund_pool_txn_to FOREIGN KEY (to_member_id) REFERENCES fund_pool_member(id)
);
CREATE INDEX idx_fund_pool_txn ON fund_pool_transaction(tenant_id, pool_id, occurred_at);

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
VALUES
 (1066,0,1060,'外部财务集成','BUTTON',NULL,NULL,'finance:integration:maintain',NULL,5,'ENABLE',1),
 (1067,0,1060,'资金池维护','BUTTON',NULL,NULL,'finance:pool:maintain',NULL,6,'ENABLE',1);

INSERT INTO sys_role_menu(id,role_id,menu_id)
SELECT 170000000+r.id*10000+1060,r.id,1060 FROM sys_role r
WHERE r.role_code IN ('SUPER_ADMIN','ADMIN','FINANCE','PROJECT_MANAGER','COST_MANAGER','AUDITOR') AND r.deleted_flag=0
  AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.id AND rm.menu_id=1060);
INSERT INTO sys_role_menu(id,role_id,menu_id)
SELECT 171000000+r.id*10000+m.id,r.id,m.id FROM sys_role r CROSS JOIN sys_menu m
WHERE m.id IN (1061,1062,1063,1064,1066,1067) AND r.role_code IN ('SUPER_ADMIN','ADMIN','FINANCE') AND r.deleted_flag=0
  AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.id AND rm.menu_id=m.id);
INSERT INTO sys_role_menu(id,role_id,menu_id)
SELECT 172000000+r.id*10000+1065,r.id,1065 FROM sys_role r
WHERE r.role_code IN ('SUPER_ADMIN','ADMIN','FINANCE','AUDITOR') AND r.deleted_flag=0
  AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.id AND rm.menu_id=1065);
