ALTER TABLE pay_application
    ADD COLUMN contract_budget_allocation_id BIGINT NULL AFTER budget_line_id,
    ADD KEY idx_pay_application_contract_budget_allocation
        (tenant_id, contract_budget_allocation_id, deleted_flag),
    ADD CONSTRAINT fk_pay_application_contract_budget_allocation
        FOREIGN KEY (contract_budget_allocation_id)
        REFERENCES contract_budget_allocation (id) ON DELETE RESTRICT;
