ALTER TABLE pay_invoice ADD COLUMN project_id BIGINT NULL;
ALTER TABLE pay_invoice ADD COLUMN contract_id BIGINT NULL;
ALTER TABLE pay_invoice ADD COLUMN partner_id BIGINT NULL;
ALTER TABLE pay_invoice ADD COLUMN document_type VARCHAR(32) NOT NULL DEFAULT 'ELECTRONIC_INVOICE';
ALTER TABLE pay_invoice ADD COLUMN integrity_version VARCHAR(32) NOT NULL DEFAULT 'LEGACY_UNVERIFIED';
ALTER TABLE pay_invoice ADD COLUMN version INT NOT NULL DEFAULT 0;
UPDATE pay_invoice i SET project_id = (SELECT r.project_id FROM pay_record r WHERE r.id = i.pay_record_id) WHERE i.pay_record_id IS NOT NULL;
UPDATE pay_invoice i SET contract_id = (SELECT r.contract_id FROM pay_record r WHERE r.id = i.pay_record_id) WHERE i.pay_record_id IS NOT NULL;
UPDATE pay_invoice i SET partner_id = (SELECT r.partner_id FROM pay_record r WHERE r.id = i.pay_record_id) WHERE i.pay_record_id IS NOT NULL;
UPDATE pay_invoice i SET pay_application_id = (SELECT r.pay_application_id FROM pay_record r WHERE r.id = i.pay_record_id)
WHERE i.pay_application_id IS NULL AND i.pay_record_id IS NOT NULL;
ALTER TABLE pay_invoice ADD CONSTRAINT fk_invoice_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON DELETE RESTRICT;
ALTER TABLE pay_invoice ADD CONSTRAINT fk_invoice_contract FOREIGN KEY (contract_id) REFERENCES ct_contract(id) ON DELETE RESTRICT;
ALTER TABLE pay_invoice ADD CONSTRAINT fk_invoice_partner FOREIGN KEY (partner_id) REFERENCES md_partner(id) ON DELETE RESTRICT;
ALTER TABLE pay_invoice ADD CONSTRAINT fk_invoice_pay_application FOREIGN KEY (pay_application_id) REFERENCES pay_application(id) ON DELETE RESTRICT;
ALTER TABLE pay_invoice ADD CONSTRAINT fk_invoice_pay_record FOREIGN KEY (pay_record_id) REFERENCES pay_record(id) ON DELETE RESTRICT;
ALTER TABLE sys_file ADD COLUMN document_type VARCHAR(32) NOT NULL DEFAULT 'OTHER';

CREATE TABLE invoice_payment_allocation (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    invoice_id BIGINT NOT NULL,
    pay_record_id BIGINT NOT NULL,
    pay_application_id BIGINT NOT NULL,
    allocated_amount DECIMAL(18,2) NOT NULL,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_invoice_alloc_invoice FOREIGN KEY (invoice_id) REFERENCES pay_invoice(id) ON DELETE RESTRICT,
    CONSTRAINT fk_invoice_alloc_record FOREIGN KEY (pay_record_id) REFERENCES pay_record(id) ON DELETE RESTRICT,
    CONSTRAINT fk_invoice_alloc_application FOREIGN KEY (pay_application_id) REFERENCES pay_application(id) ON DELETE RESTRICT,
    CONSTRAINT uk_invoice_alloc_record UNIQUE (tenant_id, invoice_id, pay_record_id)
);
CREATE INDEX idx_invoice_alloc_record ON invoice_payment_allocation(tenant_id, pay_record_id);
