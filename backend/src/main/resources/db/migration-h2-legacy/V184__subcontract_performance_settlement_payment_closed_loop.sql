ALTER TABLE payment_application_source ADD COLUMN sub_measure_id BIGINT NULL;

ALTER TABLE payment_application_source DROP CONSTRAINT ck_payment_source_reference;

ALTER TABLE payment_application_source
    ADD CONSTRAINT fk_payment_source_sub_measure
        FOREIGN KEY (sub_measure_id) REFERENCES sub_measure(id) ON DELETE RESTRICT;

ALTER TABLE payment_application_source
    ADD CONSTRAINT ck_payment_source_reference CHECK (
        (source_type = 'EXPENSE' AND expense_id IS NOT NULL AND settlement_id IS NULL
            AND sub_measure_id IS NULL AND source_ref_id = expense_id)
        OR (source_type = 'SETTLEMENT' AND settlement_id IS NOT NULL AND expense_id IS NULL
            AND sub_measure_id IS NULL AND source_ref_id = settlement_id)
        OR (source_type = 'SUB_MEASURE' AND sub_measure_id IS NOT NULL AND expense_id IS NULL
            AND settlement_id IS NULL AND source_ref_id = sub_measure_id)
        OR (source_type = 'DIRECT' AND expense_id IS NULL AND settlement_id IS NULL
            AND sub_measure_id IS NULL AND source_ref_id = pay_application_id)
    );

CREATE INDEX idx_payment_source_sub_measure
    ON payment_application_source(tenant_id, sub_measure_id, deleted_flag);

CREATE TABLE settlement_sub_measure (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    settlement_id BIGINT NOT NULL,
    sub_measure_id BIGINT NOT NULL,
    reported_amount_snapshot DECIMAL(18,2) NOT NULL,
    approved_amount_snapshot DECIMAL(18,2) NOT NULL,
    deduction_amount_snapshot DECIMAL(18,2) NOT NULL,
    net_amount_snapshot DECIMAL(18,2) NOT NULL,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_settlement_measure_settlement
        FOREIGN KEY (settlement_id) REFERENCES stl_settlement(id) ON DELETE RESTRICT,
    CONSTRAINT fk_settlement_measure_measure
        FOREIGN KEY (sub_measure_id) REFERENCES sub_measure(id) ON DELETE RESTRICT,
    CONSTRAINT uk_settlement_measure UNIQUE (tenant_id, settlement_id, sub_measure_id),
    CONSTRAINT uk_measure_final_settlement UNIQUE (tenant_id, sub_measure_id)
);

CREATE INDEX idx_settlement_measure_trace
    ON settlement_sub_measure(tenant_id, sub_measure_id, settlement_id);

CREATE INDEX idx_sub_measure_payment_context
    ON sub_measure(tenant_id, project_id, contract_id, partner_id, approval_status, deleted_flag);

CREATE INDEX idx_sub_measure_item_contract
    ON sub_measure_item(tenant_id, measure_id, contract_item_id, deleted_flag);
