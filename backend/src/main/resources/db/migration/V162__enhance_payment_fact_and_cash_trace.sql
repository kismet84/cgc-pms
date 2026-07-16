ALTER TABLE pay_record
    ADD COLUMN fund_account_id BIGINT NULL,
    ADD COLUMN paid_at DATETIME NULL,
    ADD COLUMN failure_reason VARCHAR(500) NULL,
    ADD COLUMN reversed_record_id BIGINT NULL,
    ADD COLUMN reversed_at DATETIME NULL,
    ADD COLUMN version INT NOT NULL DEFAULT 0;

ALTER TABLE pay_record
    ADD CONSTRAINT fk_pay_record_fund_account FOREIGN KEY (fund_account_id) REFERENCES fund_account(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_pay_record_reversed_record FOREIGN KEY (reversed_record_id) REFERENCES pay_record(id) ON DELETE RESTRICT;

ALTER TABLE payment_application_source
    ADD COLUMN paid_amount DECIMAL(18,2) NOT NULL DEFAULT 0;

ALTER TABLE cash_journal_entry
    ADD COLUMN pay_application_id BIGINT NULL,
    ADD COLUMN approval_instance_id BIGINT NULL,
    ADD COLUMN pay_record_id BIGINT NULL;

UPDATE cash_journal_entry
SET pay_record_id = source_id
WHERE source_type = 'PAY_RECORD' AND pay_record_id IS NULL;

UPDATE cash_journal_entry c
JOIN pay_record r ON r.id = c.pay_record_id
JOIN pay_application p ON p.id = r.pay_application_id
SET c.pay_application_id = r.pay_application_id,
    c.approval_instance_id = p.approval_instance_id
WHERE c.source_type = 'PAY_RECORD';

ALTER TABLE cash_journal_entry
    ADD CONSTRAINT fk_cash_journal_pay_application FOREIGN KEY (pay_application_id) REFERENCES pay_application(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_cash_journal_approval_instance FOREIGN KEY (approval_instance_id) REFERENCES wf_instance(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_cash_journal_pay_record FOREIGN KEY (pay_record_id) REFERENCES pay_record(id) ON DELETE RESTRICT;

CREATE UNIQUE INDEX uk_cash_journal_pay_record
    ON cash_journal_entry(tenant_id, pay_record_id, deleted_flag);

CREATE TABLE payment_record_source_allocation (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    pay_record_id BIGINT NOT NULL,
    payment_source_id BIGINT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_ref_id BIGINT NOT NULL,
    allocated_amount DECIMAL(18,2) NOT NULL,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_pay_alloc_record FOREIGN KEY (pay_record_id) REFERENCES pay_record(id) ON DELETE RESTRICT,
    CONSTRAINT fk_pay_alloc_source FOREIGN KEY (payment_source_id) REFERENCES payment_application_source(id) ON DELETE RESTRICT,
    UNIQUE KEY uk_pay_alloc_record_source (tenant_id, pay_record_id, payment_source_id),
    KEY idx_pay_alloc_source (tenant_id, payment_source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='付款记录来源金额分配';
