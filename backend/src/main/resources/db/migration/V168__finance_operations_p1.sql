ALTER TABLE pay_record ADD COLUMN reversal_type VARCHAR(32) NULL COMMENT 'REVERSAL/REFUND';
ALTER TABLE pay_invoice ADD COLUMN exception_status VARCHAR(32) NOT NULL DEFAULT 'NORMAL';
ALTER TABLE pay_invoice ADD COLUMN exception_reason VARCHAR(500) NULL;

CREATE TABLE budget_operation (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0,
    operation_type VARCHAR(32) NOT NULL, project_id BIGINT NOT NULL,
    from_budget_line_id BIGINT NULL, to_budget_line_id BIGINT NULL,
    contract_allocation_id BIGINT NULL, amount DECIMAL(18,2) NOT NULL,
    status VARCHAR(32) NOT NULL, reason VARCHAR(500) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL, operator_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE KEY uk_budget_operation_key (tenant_id, idempotency_key),
    KEY idx_budget_operation_project (tenant_id, project_id, created_at),
    CONSTRAINT fk_budget_operation_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON DELETE RESTRICT,
    CONSTRAINT fk_budget_operation_from_line FOREIGN KEY (from_budget_line_id) REFERENCES project_budget_line(id) ON DELETE RESTRICT,
    CONSTRAINT fk_budget_operation_to_line FOREIGN KEY (to_budget_line_id) REFERENCES project_budget_line(id) ON DELETE RESTRICT,
    CONSTRAINT fk_budget_operation_allocation FOREIGN KEY (contract_allocation_id) REFERENCES contract_budget_allocation(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE payment_schedule (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL, contract_id BIGINT NOT NULL,
    pay_application_id BIGINT NULL, schedule_name VARCHAR(200) NOT NULL,
    planned_date DATE NOT NULL, planned_amount DECIMAL(18,2) NOT NULL,
    paid_amount DECIMAL(18,2) NOT NULL DEFAULT 0, reminder_days INT NOT NULL DEFAULT 7,
    status VARCHAR(32) NOT NULL DEFAULT 'PLANNED', version INT NOT NULL DEFAULT 0,
    created_by BIGINT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id), KEY idx_payment_schedule_due (tenant_id, status, planned_date),
    CONSTRAINT fk_payment_schedule_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_schedule_contract FOREIGN KEY (contract_id) REFERENCES ct_contract(id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_schedule_application FOREIGN KEY (pay_application_id) REFERENCES pay_application(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE finance_alert (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0,
    alert_type VARCHAR(64) NOT NULL, business_type VARCHAR(64) NOT NULL,
    business_id BIGINT NOT NULL, severity VARCHAR(16) NOT NULL,
    due_at DATETIME NULL, status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    message VARCHAR(1000) NOT NULL, alert_key VARCHAR(200) NOT NULL,
    handled_by BIGINT NULL, handled_at DATETIME NULL, handle_note VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE KEY uk_finance_alert_key (tenant_id, alert_key),
    KEY idx_finance_alert_status (tenant_id, status, severity, due_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE finance_reconciliation_run (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, business_date DATE NOT NULL,
    run_type VARCHAR(32) NOT NULL DEFAULT 'DAILY', status VARCHAR(32) NOT NULL,
    issue_count INT NOT NULL DEFAULT 0, summary_json LONGTEXT NULL,
    started_at DATETIME NOT NULL, finished_at DATETIME NULL, created_by BIGINT NULL,
    PRIMARY KEY (id), UNIQUE KEY uk_finance_recon_day (tenant_id, business_date, run_type),
    KEY idx_finance_recon_status (tenant_id, status, business_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE finance_reconciliation_issue (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, run_id BIGINT NOT NULL,
    dimension_type VARCHAR(64) NOT NULL, business_id BIGINT NULL,
    issue_code VARCHAR(64) NOT NULL, expected_amount DECIMAL(18,2) NULL,
    actual_amount DECIMAL(18,2) NULL, status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    detail VARCHAR(1000) NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), KEY idx_finance_recon_issue (tenant_id, run_id, status),
    CONSTRAINT fk_finance_recon_issue_run FOREIGN KEY (run_id) REFERENCES finance_reconciliation_run(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
VALUES
 (1060,0,2,'资金运营','MENU','finance-operations','finance-operations/index','finance:operations:query','fund',9,'ENABLE',1),
 (1061,0,1060,'资金运营维护','BUTTON',NULL,NULL,'finance:operations:maintain',NULL,1,'ENABLE',1),
 (1062,0,1060,'执行财务对账','BUTTON',NULL,NULL,'finance:reconciliation:run',NULL,2,'ENABLE',1),
 (1063,0,906,'付款冲销','BUTTON',NULL,NULL,'payment:record:reverse',NULL,3,'ENABLE',1);
