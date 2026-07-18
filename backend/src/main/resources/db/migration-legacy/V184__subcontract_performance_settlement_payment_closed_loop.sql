ALTER TABLE payment_application_source
    ADD COLUMN sub_measure_id BIGINT NULL AFTER settlement_id;

ALTER TABLE payment_application_source
    DROP CHECK ck_payment_source_reference;

ALTER TABLE payment_application_source
    ADD CONSTRAINT fk_payment_source_sub_measure
        FOREIGN KEY (sub_measure_id) REFERENCES sub_measure(id) ON DELETE RESTRICT,
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
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    settlement_id BIGINT NOT NULL,
    sub_measure_id BIGINT NOT NULL,
    reported_amount_snapshot DECIMAL(18,2) NOT NULL,
    approved_amount_snapshot DECIMAL(18,2) NOT NULL,
    deduction_amount_snapshot DECIMAL(18,2) NOT NULL,
    net_amount_snapshot DECIMAL(18,2) NOT NULL,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_settlement_measure_settlement
        FOREIGN KEY (settlement_id) REFERENCES stl_settlement(id) ON DELETE RESTRICT,
    CONSTRAINT fk_settlement_measure_measure
        FOREIGN KEY (sub_measure_id) REFERENCES sub_measure(id) ON DELETE RESTRICT,
    UNIQUE KEY uk_settlement_measure (tenant_id, settlement_id, sub_measure_id),
    UNIQUE KEY uk_measure_final_settlement (tenant_id, sub_measure_id),
    KEY idx_settlement_measure_trace (tenant_id, sub_measure_id, settlement_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='终期结算分包计量快照关系';

CREATE INDEX idx_sub_measure_payment_context
    ON sub_measure(tenant_id, project_id, contract_id, partner_id, approval_status, deleted_flag);

CREATE INDEX idx_sub_measure_item_contract
    ON sub_measure_item(tenant_id, measure_id, contract_item_id, deleted_flag);
