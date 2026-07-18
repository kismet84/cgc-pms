ALTER TABLE pay_invoice
    ADD COLUMN project_id BIGINT NULL,
    ADD COLUMN contract_id BIGINT NULL,
    ADD COLUMN partner_id BIGINT NULL,
    ADD COLUMN document_type VARCHAR(32) NOT NULL DEFAULT 'ELECTRONIC_INVOICE',
    ADD COLUMN integrity_version VARCHAR(32) NOT NULL DEFAULT 'LEGACY_UNVERIFIED',
    ADD COLUMN version INT NOT NULL DEFAULT 0;

UPDATE pay_invoice i
JOIN pay_record r ON r.id = i.pay_record_id
SET i.project_id = r.project_id,
    i.contract_id = r.contract_id,
    i.partner_id = r.partner_id,
    i.pay_application_id = COALESCE(i.pay_application_id, r.pay_application_id);

ALTER TABLE pay_invoice
    ADD CONSTRAINT fk_invoice_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_invoice_contract FOREIGN KEY (contract_id) REFERENCES ct_contract(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_invoice_partner FOREIGN KEY (partner_id) REFERENCES md_partner(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_invoice_pay_application FOREIGN KEY (pay_application_id) REFERENCES pay_application(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_invoice_pay_record FOREIGN KEY (pay_record_id) REFERENCES pay_record(id) ON DELETE RESTRICT;

ALTER TABLE sys_file
    ADD COLUMN document_type VARCHAR(32) NOT NULL DEFAULT 'OTHER';

CREATE TABLE invoice_payment_allocation (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    invoice_id BIGINT NOT NULL,
    pay_record_id BIGINT NOT NULL,
    pay_application_id BIGINT NOT NULL,
    allocated_amount DECIMAL(18,2) NOT NULL,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_invoice_alloc_invoice FOREIGN KEY (invoice_id) REFERENCES pay_invoice(id) ON DELETE RESTRICT,
    CONSTRAINT fk_invoice_alloc_record FOREIGN KEY (pay_record_id) REFERENCES pay_record(id) ON DELETE RESTRICT,
    CONSTRAINT fk_invoice_alloc_application FOREIGN KEY (pay_application_id) REFERENCES pay_application(id) ON DELETE RESTRICT,
    UNIQUE KEY uk_invoice_alloc_record (tenant_id, invoice_id, pay_record_id),
    KEY idx_invoice_alloc_record (tenant_id, pay_record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='发票付款金额分配';
