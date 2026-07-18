CREATE TABLE expense_application (
    id BIGINT NOT NULL PRIMARY KEY,
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
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    CONSTRAINT fk_expense_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON DELETE RESTRICT,
    CONSTRAINT fk_expense_contract FOREIGN KEY (contract_id) REFERENCES ct_contract(id) ON DELETE RESTRICT,
    CONSTRAINT fk_expense_subject FOREIGN KEY (cost_subject_id) REFERENCES cost_subject(id) ON DELETE RESTRICT,
    CONSTRAINT fk_expense_budget_line FOREIGN KEY (budget_line_id) REFERENCES project_budget_line(id) ON DELETE RESTRICT,
    CONSTRAINT fk_expense_partner FOREIGN KEY (payee_partner_id) REFERENCES md_partner(id) ON DELETE RESTRICT,
    CONSTRAINT uk_expense_code UNIQUE (tenant_id, expense_code, deleted_flag)
);
CREATE INDEX idx_expense_project_status ON expense_application(tenant_id, project_id, approval_status);
CREATE INDEX idx_expense_contract ON expense_application(tenant_id, contract_id);

ALTER TABLE pay_application ADD COLUMN cost_subject_id BIGINT NULL;
ALTER TABLE pay_application ADD COLUMN budget_line_id BIGINT NULL;
ALTER TABLE pay_application ADD COLUMN expense_category VARCHAR(64) NULL;
ALTER TABLE pay_application ADD COLUMN approval_instance_id BIGINT NULL;
ALTER TABLE pay_application ADD COLUMN version INT NOT NULL DEFAULT 0;

CREATE TABLE payment_application_source (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    pay_application_id BIGINT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_ref_id BIGINT NOT NULL,
    expense_id BIGINT NULL,
    settlement_id BIGINT NULL,
    source_amount DECIMAL(18,2) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    CONSTRAINT fk_payment_source_application FOREIGN KEY (pay_application_id) REFERENCES pay_application(id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_source_expense FOREIGN KEY (expense_id) REFERENCES expense_application(id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_source_settlement FOREIGN KEY (settlement_id) REFERENCES stl_settlement(id) ON DELETE RESTRICT,
    CONSTRAINT ck_payment_source_reference CHECK (
        (source_type = 'EXPENSE' AND expense_id IS NOT NULL AND settlement_id IS NULL AND source_ref_id = expense_id)
        OR (source_type = 'SETTLEMENT' AND settlement_id IS NOT NULL AND expense_id IS NULL AND source_ref_id = settlement_id)
        OR (source_type = 'DIRECT' AND expense_id IS NULL AND settlement_id IS NULL AND source_ref_id = pay_application_id)
    ),
    CONSTRAINT uk_payment_source UNIQUE (tenant_id, pay_application_id, source_type, source_ref_id, deleted_flag)
);
CREATE INDEX idx_payment_source_ref ON payment_application_source(tenant_id, source_type, source_ref_id);
