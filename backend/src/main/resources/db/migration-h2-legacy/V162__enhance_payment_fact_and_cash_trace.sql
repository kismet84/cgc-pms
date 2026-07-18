ALTER TABLE pay_record ADD COLUMN fund_account_id BIGINT NULL;
ALTER TABLE pay_record ADD COLUMN paid_at TIMESTAMP NULL;
ALTER TABLE pay_record ADD COLUMN failure_reason VARCHAR(500) NULL;
ALTER TABLE pay_record ADD COLUMN reversed_record_id BIGINT NULL;
ALTER TABLE pay_record ADD COLUMN reversed_at TIMESTAMP NULL;
ALTER TABLE pay_record ADD COLUMN version INT NOT NULL DEFAULT 0;
ALTER TABLE pay_record ADD CONSTRAINT fk_pay_record_fund_account FOREIGN KEY (fund_account_id) REFERENCES fund_account(id) ON DELETE RESTRICT;
ALTER TABLE pay_record ADD CONSTRAINT fk_pay_record_reversed_record FOREIGN KEY (reversed_record_id) REFERENCES pay_record(id) ON DELETE RESTRICT;

ALTER TABLE payment_application_source ADD COLUMN paid_amount DECIMAL(18,2) NOT NULL DEFAULT 0;

ALTER TABLE cash_journal_entry ADD COLUMN pay_application_id BIGINT NULL;
ALTER TABLE cash_journal_entry ADD COLUMN approval_instance_id BIGINT NULL;
ALTER TABLE cash_journal_entry ADD COLUMN pay_record_id BIGINT NULL;
UPDATE cash_journal_entry SET pay_record_id = source_id WHERE source_type = 'PAY_RECORD' AND pay_record_id IS NULL;
UPDATE cash_journal_entry c SET pay_application_id = (SELECT r.pay_application_id FROM pay_record r WHERE r.id = c.pay_record_id)
WHERE c.source_type = 'PAY_RECORD';
UPDATE cash_journal_entry c SET approval_instance_id = (SELECT p.approval_instance_id FROM pay_application p WHERE p.id = c.pay_application_id)
WHERE c.source_type = 'PAY_RECORD';
ALTER TABLE cash_journal_entry ADD CONSTRAINT fk_cash_journal_pay_application FOREIGN KEY (pay_application_id) REFERENCES pay_application(id) ON DELETE RESTRICT;
ALTER TABLE cash_journal_entry ADD CONSTRAINT fk_cash_journal_approval_instance FOREIGN KEY (approval_instance_id) REFERENCES wf_instance(id) ON DELETE RESTRICT;
ALTER TABLE cash_journal_entry ADD CONSTRAINT fk_cash_journal_pay_record FOREIGN KEY (pay_record_id) REFERENCES pay_record(id) ON DELETE RESTRICT;
CREATE UNIQUE INDEX uk_cash_journal_pay_record ON cash_journal_entry(tenant_id, pay_record_id, deleted_flag);

CREATE TABLE payment_record_source_allocation (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    pay_record_id BIGINT NOT NULL,
    payment_source_id BIGINT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_ref_id BIGINT NOT NULL,
    allocated_amount DECIMAL(18,2) NOT NULL,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pay_alloc_record FOREIGN KEY (pay_record_id) REFERENCES pay_record(id) ON DELETE RESTRICT,
    CONSTRAINT fk_pay_alloc_source FOREIGN KEY (payment_source_id) REFERENCES payment_application_source(id) ON DELETE RESTRICT,
    CONSTRAINT uk_pay_alloc_record_source UNIQUE (tenant_id, pay_record_id, payment_source_id)
);
CREATE INDEX idx_pay_alloc_source ON payment_record_source_allocation(tenant_id, payment_source_id);
