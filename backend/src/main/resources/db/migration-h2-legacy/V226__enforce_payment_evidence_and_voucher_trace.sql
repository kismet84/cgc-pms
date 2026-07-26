CREATE TABLE payment_document_link (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    cash_journal_id BIGINT NOT NULL,
    file_id BIGINT NOT NULL,
    document_type VARCHAR(32) NOT NULL,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_payment_document_file UNIQUE (tenant_id, file_id),
    CONSTRAINT fk_payment_document_journal FOREIGN KEY (cash_journal_id)
        REFERENCES cash_journal_entry(id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_document_file FOREIGN KEY (file_id)
        REFERENCES sys_file(id) ON DELETE RESTRICT,
    CONSTRAINT ck_payment_document_type CHECK (document_type IN ('BANK_RECEIPT','PAYMENT_PROOF'))
);
CREATE INDEX idx_payment_document_journal ON payment_document_link(tenant_id, cash_journal_id);

ALTER TABLE accounting_entry ADD COLUMN cash_journal_id BIGINT NULL;
CREATE INDEX idx_entry_cash_journal ON accounting_entry(tenant_id, cash_journal_id);
ALTER TABLE accounting_entry ADD CONSTRAINT fk_entry_cash_journal
    FOREIGN KEY (cash_journal_id) REFERENCES cash_journal_entry(id) ON DELETE RESTRICT;

ALTER TABLE payment_application_source ADD CONSTRAINT ck_payment_source_direct_ref
    CHECK (source_type <> 'DIRECT' OR source_ref_id = pay_application_id);

CREATE UNIQUE INDEX uk_cash_journal_tenant_id ON cash_journal_entry(tenant_id, id);
CREATE UNIQUE INDEX uk_sys_file_tenant_id ON sys_file(tenant_id, id);
CREATE UNIQUE INDEX uk_pay_invoice_tenant_id ON pay_invoice(tenant_id, id);
CREATE UNIQUE INDEX uk_pay_record_tenant_id ON pay_record(tenant_id, id);
CREATE UNIQUE INDEX uk_pay_application_tenant_id ON pay_application(tenant_id, id);

ALTER TABLE payment_document_link ADD CONSTRAINT fk_payment_document_journal_tenant
    FOREIGN KEY (tenant_id, cash_journal_id) REFERENCES cash_journal_entry(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE payment_document_link ADD CONSTRAINT fk_payment_document_file_tenant
    FOREIGN KEY (tenant_id, file_id) REFERENCES sys_file(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE invoice_payment_allocation ADD CONSTRAINT fk_invoice_alloc_invoice_tenant
    FOREIGN KEY (tenant_id, invoice_id) REFERENCES pay_invoice(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE invoice_payment_allocation ADD CONSTRAINT fk_invoice_alloc_record_tenant
    FOREIGN KEY (tenant_id, pay_record_id) REFERENCES pay_record(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE invoice_payment_allocation ADD CONSTRAINT fk_invoice_alloc_application_tenant
    FOREIGN KEY (tenant_id, pay_application_id) REFERENCES pay_application(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE accounting_entry ADD CONSTRAINT fk_entry_cash_journal_tenant
    FOREIGN KEY (tenant_id, cash_journal_id) REFERENCES cash_journal_entry(tenant_id, id) ON DELETE RESTRICT;
