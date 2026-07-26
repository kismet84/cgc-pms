ALTER TABLE pay_application
    ADD COLUMN contract_budget_allocation_id BIGINT NULL;

CREATE INDEX idx_pay_application_contract_budget_allocation
    ON pay_application (tenant_id, contract_budget_allocation_id, deleted_flag);

ALTER TABLE pay_application
    ADD CONSTRAINT fk_pay_application_contract_budget_allocation
    FOREIGN KEY (contract_budget_allocation_id)
    REFERENCES contract_budget_allocation (id);
