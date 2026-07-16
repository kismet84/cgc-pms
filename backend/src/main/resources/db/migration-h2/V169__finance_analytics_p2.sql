CREATE TABLE dashboard_finance_snapshot (
    id BIGINT PRIMARY KEY, tenant_id BIGINT DEFAULT 0 NOT NULL, project_id BIGINT NOT NULL,
    snapshot_date DATE NOT NULL, formula_version VARCHAR(64) NOT NULL,
    contract_amount DECIMAL(18,2) NOT NULL, approved_unpaid_amount DECIMAL(18,2) NOT NULL,
    paid_amount DECIMAL(18,2) NOT NULL, budget_amount DECIMAL(18,2) NOT NULL,
    budget_reserved DECIMAL(18,2) NOT NULL, budget_consumed DECIMAL(18,2) NOT NULL,
    cash_inflow DECIMAL(18,2) NOT NULL, cash_outflow DECIMAL(18,2) NOT NULL,
    actual_cost DECIMAL(18,2) NOT NULL, profit_amount DECIMAL(18,2) NOT NULL,
    refreshed_at TIMESTAMP NOT NULL, refresh_mode VARCHAR(32) NOT NULL,
    CONSTRAINT uk_dashboard_snapshot UNIQUE (tenant_id, project_id, snapshot_date),
    CONSTRAINT fk_dashboard_snapshot_project FOREIGN KEY (project_id) REFERENCES pm_project(id)
);
CREATE INDEX idx_dashboard_snapshot_date ON dashboard_finance_snapshot(tenant_id, snapshot_date);

CREATE TABLE invoice_ocr_review (
    id BIGINT PRIMARY KEY, tenant_id BIGINT DEFAULT 0 NOT NULL, invoice_id BIGINT NOT NULL,
    raw_result_json CLOB NOT NULL, confidence DECIMAL(5,4) NOT NULL, comparison_json CLOB,
    review_status VARCHAR(32) DEFAULT 'PENDING' NOT NULL, reviewer_id BIGINT,
    reviewed_at TIMESTAMP, review_note VARCHAR(500), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_invoice_ocr_review_invoice FOREIGN KEY (invoice_id) REFERENCES pay_invoice(id)
);
CREATE INDEX idx_invoice_ocr_review ON invoice_ocr_review(tenant_id, review_status, confidence);

CREATE TABLE finance_import_batch (
    id BIGINT PRIMARY KEY, tenant_id BIGINT DEFAULT 0 NOT NULL, import_type VARCHAR(32) NOT NULL,
    project_id BIGINT NOT NULL, file_name VARCHAR(255) NOT NULL, file_hash VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL, total_rows INT DEFAULT 0 NOT NULL, valid_rows INT DEFAULT 0 NOT NULL,
    invalid_rows INT DEFAULT 0 NOT NULL, diff_summary_json CLOB, created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL, applied_at TIMESTAMP,
    CONSTRAINT uk_finance_import_hash UNIQUE (tenant_id, import_type, project_id, file_hash),
    CONSTRAINT fk_finance_import_project FOREIGN KEY (project_id) REFERENCES pm_project(id)
);
CREATE TABLE finance_import_row (
    id BIGINT PRIMARY KEY, tenant_id BIGINT DEFAULT 0 NOT NULL, batch_id BIGINT NOT NULL,
    row_no INT NOT NULL, business_key VARCHAR(128), input_json CLOB NOT NULL, diff_json CLOB,
    validation_status VARCHAR(32) NOT NULL, validation_message VARCHAR(1000),
    CONSTRAINT uk_finance_import_row UNIQUE (batch_id, row_no),
    CONSTRAINT fk_finance_import_row_batch FOREIGN KEY (batch_id) REFERENCES finance_import_batch(id)
);
CREATE INDEX idx_finance_import_row_status ON finance_import_row(tenant_id, batch_id, validation_status);

CREATE TABLE approval_routing_rule (
    id BIGINT PRIMARY KEY, tenant_id BIGINT DEFAULT 0 NOT NULL, rule_name VARCHAR(200) NOT NULL,
    business_type VARCHAR(64) NOT NULL, min_amount DECIMAL(18,2), max_amount DECIMAL(18,2),
    contract_type VARCHAR(64), expense_category VARCHAR(64), workflow_template_id BIGINT NOT NULL,
    priority INT DEFAULT 100 NOT NULL, enabled_flag SMALLINT DEFAULT 1 NOT NULL, version INT DEFAULT 0 NOT NULL,
    created_by BIGINT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by BIGINT, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_approval_routing_template FOREIGN KEY (workflow_template_id) REFERENCES wf_template(id)
);
CREATE INDEX idx_approval_routing_match ON approval_routing_rule(tenant_id, business_type, enabled_flag, priority);

CREATE TABLE finance_audit_event (
    id BIGINT PRIMARY KEY, tenant_id BIGINT DEFAULT 0 NOT NULL, event_type VARCHAR(64) NOT NULL,
    business_type VARCHAR(64) NOT NULL, business_id BIGINT NOT NULL, project_id BIGINT,
    operator_id BIGINT, event_at TIMESTAMP NOT NULL, archive_bucket VARCHAR(32) DEFAULT 'HOT' NOT NULL,
    payload_json CLOB NOT NULL, payload_hash VARCHAR(64) NOT NULL
);
CREATE INDEX idx_finance_audit_search ON finance_audit_event(tenant_id, business_type, business_id, event_at);
CREATE INDEX idx_finance_audit_archive ON finance_audit_event(tenant_id, archive_bucket, event_at);
CREATE INDEX idx_pay_record_finance_recon ON pay_record(tenant_id, project_id, pay_status, paid_at);
CREATE INDEX idx_cash_journal_finance_recon ON cash_journal_entry(tenant_id, project_id, status, business_date);
CREATE INDEX idx_invoice_exception ON pay_invoice(tenant_id, exception_status, invoice_date);

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
VALUES
 (1064,0,1060,'资金分析维护','BUTTON',NULL,NULL,'finance:analytics:maintain',NULL,3,'ENABLE',1),
 (1065,0,1060,'财务审计导出','BUTTON',NULL,NULL,'finance:audit:export',NULL,4,'ENABLE',1);
