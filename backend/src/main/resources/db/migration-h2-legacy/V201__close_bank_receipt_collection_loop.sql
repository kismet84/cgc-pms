CREATE UNIQUE INDEX uk_collection_tenant_id ON collection_record(tenant_id,id);
ALTER TABLE bank_receipt ADD COLUMN project_id BIGINT NULL;
ALTER TABLE bank_receipt ADD COLUMN contract_id BIGINT NULL;
ALTER TABLE bank_receipt ADD COLUMN customer_id BIGINT NULL;
ALTER TABLE bank_receipt ADD COLUMN fund_account_id BIGINT NULL;
ALTER TABLE bank_receipt ADD COLUMN allocation_json CLOB NULL;
CREATE INDEX idx_bank_receipt_collection_context ON bank_receipt(tenant_id,project_id,contract_id,customer_id);
ALTER TABLE bank_receipt ADD CONSTRAINT fk_bank_receipt_project FOREIGN KEY(project_id) REFERENCES pm_project(id);
ALTER TABLE bank_receipt ADD CONSTRAINT fk_bank_receipt_contract FOREIGN KEY(contract_id) REFERENCES ct_contract(id);
ALTER TABLE bank_receipt ADD CONSTRAINT fk_bank_receipt_customer FOREIGN KEY(customer_id) REFERENCES md_partner(id);
ALTER TABLE bank_receipt ADD CONSTRAINT fk_bank_receipt_fund_account FOREIGN KEY(fund_account_id) REFERENCES fund_account(id);
ALTER TABLE bank_receipt ADD CONSTRAINT ck_bank_receipt_direction_194 CHECK(direction IN ('IN','OUT'));
ALTER TABLE bank_receipt ADD CONSTRAINT ck_bank_receipt_status_194 CHECK(match_status IN ('UNMATCHED','MATCHED','MANUAL_REVIEW','IGNORED'));
ALTER TABLE bank_receipt ADD CONSTRAINT ck_bank_receipt_link_194 CHECK((collection_record_id IS NULL OR direction='IN') AND (pay_record_id IS NULL OR direction='OUT'));
ALTER TABLE bank_receipt ADD CONSTRAINT ck_bank_receipt_context_194 CHECK(direction='OUT' OR
 (project_id IS NULL AND contract_id IS NULL AND customer_id IS NULL AND fund_account_id IS NULL) OR
 (project_id IS NOT NULL AND contract_id IS NOT NULL AND customer_id IS NOT NULL AND fund_account_id IS NOT NULL));
UPDATE bank_receipt b SET project_id=(SELECT c.project_id FROM collection_record c WHERE c.id=b.collection_record_id AND c.tenant_id=b.tenant_id) WHERE b.collection_record_id IS NOT NULL;
UPDATE bank_receipt b SET contract_id=(SELECT c.contract_id FROM collection_record c WHERE c.id=b.collection_record_id AND c.tenant_id=b.tenant_id) WHERE b.collection_record_id IS NOT NULL;
UPDATE bank_receipt b SET customer_id=(SELECT c.customer_id FROM collection_record c WHERE c.id=b.collection_record_id AND c.tenant_id=b.tenant_id) WHERE b.collection_record_id IS NOT NULL;
UPDATE bank_receipt b SET fund_account_id=(SELECT c.fund_account_id FROM collection_record c WHERE c.id=b.collection_record_id AND c.tenant_id=b.tenant_id) WHERE b.collection_record_id IS NOT NULL;
ALTER TABLE bank_receipt ADD CONSTRAINT fk_bank_receipt_collection_194 FOREIGN KEY(tenant_id,collection_record_id) REFERENCES collection_record(tenant_id,id);
