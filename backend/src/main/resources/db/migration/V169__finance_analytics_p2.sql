CREATE TABLE dashboard_finance_snapshot (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, project_id BIGINT NOT NULL,
    snapshot_date DATE NOT NULL, formula_version VARCHAR(64) NOT NULL,
    contract_amount DECIMAL(18,2) NOT NULL, approved_unpaid_amount DECIMAL(18,2) NOT NULL,
    paid_amount DECIMAL(18,2) NOT NULL, budget_amount DECIMAL(18,2) NOT NULL,
    budget_reserved DECIMAL(18,2) NOT NULL, budget_consumed DECIMAL(18,2) NOT NULL,
    cash_inflow DECIMAL(18,2) NOT NULL, cash_outflow DECIMAL(18,2) NOT NULL,
    actual_cost DECIMAL(18,2) NOT NULL, profit_amount DECIMAL(18,2) NOT NULL,
    refreshed_at DATETIME NOT NULL, refresh_mode VARCHAR(32) NOT NULL,
    PRIMARY KEY (id), UNIQUE KEY uk_dashboard_snapshot (tenant_id, project_id, snapshot_date),
    KEY idx_dashboard_snapshot_date (tenant_id, snapshot_date),
    CONSTRAINT fk_dashboard_snapshot_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE invoice_ocr_review (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, invoice_id BIGINT NOT NULL,
    raw_result_json LONGTEXT NOT NULL, confidence DECIMAL(5,4) NOT NULL,
    comparison_json LONGTEXT NULL, review_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    reviewer_id BIGINT NULL, reviewed_at DATETIME NULL, review_note VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), KEY idx_invoice_ocr_review (tenant_id, review_status, confidence),
    CONSTRAINT fk_invoice_ocr_review_invoice FOREIGN KEY (invoice_id) REFERENCES pay_invoice(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE finance_import_batch (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, import_type VARCHAR(32) NOT NULL,
    project_id BIGINT NOT NULL, file_name VARCHAR(255) NOT NULL, file_hash VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL, total_rows INT NOT NULL DEFAULT 0, valid_rows INT NOT NULL DEFAULT 0,
    invalid_rows INT NOT NULL DEFAULT 0, diff_summary_json LONGTEXT NULL,
    created_by BIGINT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, applied_at DATETIME NULL,
    PRIMARY KEY (id), UNIQUE KEY uk_finance_import_hash (tenant_id, import_type, project_id, file_hash),
    CONSTRAINT fk_finance_import_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE finance_import_row (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, batch_id BIGINT NOT NULL,
    row_no INT NOT NULL, business_key VARCHAR(128) NULL, input_json LONGTEXT NOT NULL,
    diff_json LONGTEXT NULL, validation_status VARCHAR(32) NOT NULL, validation_message VARCHAR(1000) NULL,
    PRIMARY KEY (id), UNIQUE KEY uk_finance_import_row (batch_id, row_no),
    KEY idx_finance_import_row_status (tenant_id, batch_id, validation_status),
    CONSTRAINT fk_finance_import_row_batch FOREIGN KEY (batch_id) REFERENCES finance_import_batch(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE approval_routing_rule (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, rule_name VARCHAR(200) NOT NULL,
    business_type VARCHAR(64) NOT NULL, min_amount DECIMAL(18,2) NULL, max_amount DECIMAL(18,2) NULL,
    contract_type VARCHAR(64) NULL, expense_category VARCHAR(64) NULL, workflow_template_id BIGINT NOT NULL,
    priority INT NOT NULL DEFAULT 100, enabled_flag TINYINT NOT NULL DEFAULT 1, version INT NOT NULL DEFAULT 0,
    created_by BIGINT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id), KEY idx_approval_routing_match (tenant_id, business_type, enabled_flag, priority),
    CONSTRAINT fk_approval_routing_template FOREIGN KEY (workflow_template_id) REFERENCES wf_template(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE finance_audit_event (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, event_type VARCHAR(64) NOT NULL,
    business_type VARCHAR(64) NOT NULL, business_id BIGINT NOT NULL, project_id BIGINT NULL,
    operator_id BIGINT NULL, event_at DATETIME NOT NULL, archive_bucket VARCHAR(32) NOT NULL DEFAULT 'HOT',
    payload_json LONGTEXT NOT NULL, payload_hash VARCHAR(64) NOT NULL,
    PRIMARY KEY (id), KEY idx_finance_audit_search (tenant_id, business_type, business_id, event_at),
    KEY idx_finance_audit_archive (tenant_id, archive_bucket, event_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_pay_record_finance_recon ON pay_record (tenant_id, project_id, pay_status, paid_at);
CREATE INDEX idx_cash_journal_finance_recon ON cash_journal_entry (tenant_id, project_id, status, business_date);
CREATE INDEX idx_invoice_exception ON pay_invoice (tenant_id, exception_status, invoice_date);

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
VALUES
 (1064,0,1060,'资金分析维护','BUTTON',NULL,NULL,'finance:analytics:maintain',NULL,3,'ENABLE',1),
 (1065,0,1060,'财务审计导出','BUTTON',NULL,NULL,'finance:audit:export',NULL,4,'ENABLE',1);
