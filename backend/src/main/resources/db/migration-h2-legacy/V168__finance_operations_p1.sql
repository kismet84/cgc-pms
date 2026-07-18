ALTER TABLE pay_record ADD COLUMN reversal_type VARCHAR(32);
ALTER TABLE pay_invoice ADD COLUMN exception_status VARCHAR(32) DEFAULT 'NORMAL' NOT NULL;
ALTER TABLE pay_invoice ADD COLUMN exception_reason VARCHAR(500);

CREATE TABLE budget_operation (
    id BIGINT PRIMARY KEY, tenant_id BIGINT DEFAULT 0 NOT NULL,
    operation_type VARCHAR(32) NOT NULL, project_id BIGINT NOT NULL,
    from_budget_line_id BIGINT, to_budget_line_id BIGINT, contract_allocation_id BIGINT,
    amount DECIMAL(18,2) NOT NULL, status VARCHAR(32) NOT NULL, reason VARCHAR(500) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL, operator_id BIGINT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uk_budget_operation_key UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT fk_budget_operation_project FOREIGN KEY (project_id) REFERENCES pm_project(id),
    CONSTRAINT fk_budget_operation_from_line FOREIGN KEY (from_budget_line_id) REFERENCES project_budget_line(id),
    CONSTRAINT fk_budget_operation_to_line FOREIGN KEY (to_budget_line_id) REFERENCES project_budget_line(id),
    CONSTRAINT fk_budget_operation_allocation FOREIGN KEY (contract_allocation_id) REFERENCES contract_budget_allocation(id)
);
CREATE INDEX idx_budget_operation_project ON budget_operation(tenant_id, project_id, created_at);

CREATE TABLE payment_schedule (
    id BIGINT PRIMARY KEY, tenant_id BIGINT DEFAULT 0 NOT NULL, project_id BIGINT NOT NULL,
    contract_id BIGINT NOT NULL, pay_application_id BIGINT, schedule_name VARCHAR(200) NOT NULL,
    planned_date DATE NOT NULL, planned_amount DECIMAL(18,2) NOT NULL,
    paid_amount DECIMAL(18,2) DEFAULT 0 NOT NULL, reminder_days INT DEFAULT 7 NOT NULL,
    status VARCHAR(32) DEFAULT 'PLANNED' NOT NULL, version INT DEFAULT 0 NOT NULL,
    created_by BIGINT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by BIGINT, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_payment_schedule_project FOREIGN KEY (project_id) REFERENCES pm_project(id),
    CONSTRAINT fk_payment_schedule_contract FOREIGN KEY (contract_id) REFERENCES ct_contract(id),
    CONSTRAINT fk_payment_schedule_application FOREIGN KEY (pay_application_id) REFERENCES pay_application(id)
);
CREATE INDEX idx_payment_schedule_due ON payment_schedule(tenant_id, status, planned_date);

CREATE TABLE finance_alert (
    id BIGINT PRIMARY KEY, tenant_id BIGINT DEFAULT 0 NOT NULL, alert_type VARCHAR(64) NOT NULL,
    business_type VARCHAR(64) NOT NULL, business_id BIGINT NOT NULL, severity VARCHAR(16) NOT NULL,
    due_at TIMESTAMP, status VARCHAR(32) DEFAULT 'OPEN' NOT NULL, message VARCHAR(1000) NOT NULL,
    alert_key VARCHAR(200) NOT NULL, handled_by BIGINT, handled_at TIMESTAMP, handle_note VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uk_finance_alert_key UNIQUE (tenant_id, alert_key)
);
CREATE INDEX idx_finance_alert_status ON finance_alert(tenant_id, status, severity, due_at);

CREATE TABLE finance_reconciliation_run (
    id BIGINT PRIMARY KEY, tenant_id BIGINT DEFAULT 0 NOT NULL, business_date DATE NOT NULL,
    run_type VARCHAR(32) DEFAULT 'DAILY' NOT NULL, status VARCHAR(32) NOT NULL,
    issue_count INT DEFAULT 0 NOT NULL, summary_json CLOB, started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP, created_by BIGINT,
    CONSTRAINT uk_finance_recon_day UNIQUE (tenant_id, business_date, run_type)
);
CREATE INDEX idx_finance_recon_status ON finance_reconciliation_run(tenant_id, status, business_date);

CREATE TABLE finance_reconciliation_issue (
    id BIGINT PRIMARY KEY, tenant_id BIGINT DEFAULT 0 NOT NULL, run_id BIGINT NOT NULL,
    dimension_type VARCHAR(64) NOT NULL, business_id BIGINT, issue_code VARCHAR(64) NOT NULL,
    expected_amount DECIMAL(18,2), actual_amount DECIMAL(18,2), status VARCHAR(32) DEFAULT 'OPEN' NOT NULL,
    detail VARCHAR(1000) NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_finance_recon_issue_run FOREIGN KEY (run_id) REFERENCES finance_reconciliation_run(id)
);
CREATE INDEX idx_finance_recon_issue ON finance_reconciliation_issue(tenant_id, run_id, status);

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
VALUES
 (1060,0,2,'资金运营','MENU','finance-operations','finance-operations/index','finance:operations:query','fund',9,'ENABLE',1),
 (1061,0,1060,'资金运营维护','BUTTON',NULL,NULL,'finance:operations:maintain',NULL,1,'ENABLE',1),
 (1062,0,1060,'执行财务对账','BUTTON',NULL,NULL,'finance:reconciliation:run',NULL,2,'ENABLE',1),
 (1063,0,906,'付款冲销','BUTTON',NULL,NULL,'payment:record:reverse',NULL,3,'ENABLE',1);
