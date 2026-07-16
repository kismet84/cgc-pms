CREATE TABLE expense_application (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL,
    contract_id BIGINT NOT NULL,
    cost_subject_id BIGINT NOT NULL,
    budget_line_id BIGINT NOT NULL,
    payee_partner_id BIGINT NOT NULL,
    expense_code VARCHAR(64) NOT NULL,
    expense_category VARCHAR(64) NOT NULL,
    expense_date DATE NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    converted_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    paid_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    description VARCHAR(500) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    approval_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    version INT NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_expense_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON DELETE RESTRICT,
    CONSTRAINT fk_expense_contract FOREIGN KEY (contract_id) REFERENCES ct_contract(id) ON DELETE RESTRICT,
    CONSTRAINT fk_expense_subject FOREIGN KEY (cost_subject_id) REFERENCES cost_subject(id) ON DELETE RESTRICT,
    CONSTRAINT fk_expense_budget_line FOREIGN KEY (budget_line_id) REFERENCES project_budget_line(id) ON DELETE RESTRICT,
    CONSTRAINT fk_expense_partner FOREIGN KEY (payee_partner_id) REFERENCES md_partner(id) ON DELETE RESTRICT,
    UNIQUE KEY uk_expense_code (tenant_id, expense_code, deleted_flag),
    KEY idx_expense_project_status (tenant_id, project_id, approval_status),
    KEY idx_expense_contract (tenant_id, contract_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='费用申请';

ALTER TABLE pay_application
    ADD COLUMN cost_subject_id BIGINT NULL,
    ADD COLUMN budget_line_id BIGINT NULL,
    ADD COLUMN expense_category VARCHAR(64) NULL,
    ADD COLUMN approval_instance_id BIGINT NULL,
    ADD COLUMN version INT NOT NULL DEFAULT 0;

CREATE TABLE payment_application_source (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    pay_application_id BIGINT NOT NULL,
    source_type VARCHAR(32) NOT NULL COMMENT 'EXPENSE/SETTLEMENT/DIRECT',
    source_ref_id BIGINT NOT NULL COMMENT '来源ID；DIRECT时等于付款申请ID',
    expense_id BIGINT NULL,
    settlement_id BIGINT NULL,
    source_amount DECIMAL(18,2) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_payment_source_application FOREIGN KEY (pay_application_id) REFERENCES pay_application(id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_source_expense FOREIGN KEY (expense_id) REFERENCES expense_application(id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_source_settlement FOREIGN KEY (settlement_id) REFERENCES stl_settlement(id) ON DELETE RESTRICT,
    CONSTRAINT ck_payment_source_reference CHECK (
        (source_type = 'EXPENSE' AND expense_id IS NOT NULL AND settlement_id IS NULL AND source_ref_id = expense_id)
        OR (source_type = 'SETTLEMENT' AND settlement_id IS NOT NULL AND expense_id IS NULL AND source_ref_id = settlement_id)
        OR (source_type = 'DIRECT' AND expense_id IS NULL AND settlement_id IS NULL AND source_ref_id = pay_application_id)
    ),
    UNIQUE KEY uk_payment_source (tenant_id, pay_application_id, source_type, source_ref_id, deleted_flag),
    KEY idx_payment_source_ref (tenant_id, source_type, source_ref_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='付款申请统一来源';
