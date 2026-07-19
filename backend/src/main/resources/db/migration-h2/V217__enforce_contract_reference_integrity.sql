-- H2 counterpart of V217__enforce_contract_reference_integrity.sql.

ALTER TABLE md_partner
    ADD CONSTRAINT uk_md_partner_tenant_id UNIQUE (tenant_id, id);

ALTER TABLE ct_contract ALTER COLUMN party_a_id BIGINT NOT NULL;
ALTER TABLE ct_contract ALTER COLUMN party_b_id BIGINT NOT NULL;
ALTER TABLE ct_contract
    ADD CONSTRAINT chk_contract_distinct_parties CHECK (party_a_id <> party_b_id);
CREATE INDEX idx_contract_tenant_party_a ON ct_contract (tenant_id, party_a_id);
CREATE INDEX idx_contract_tenant_party_b ON ct_contract (tenant_id, party_b_id);
ALTER TABLE ct_contract
    ADD CONSTRAINT fk_contract_project_tenant
        FOREIGN KEY (tenant_id, project_id) REFERENCES pm_project (tenant_id, id);
ALTER TABLE ct_contract
    ADD CONSTRAINT fk_contract_party_a_tenant
        FOREIGN KEY (tenant_id, party_a_id) REFERENCES md_partner (tenant_id, id);
ALTER TABLE ct_contract
    ADD CONSTRAINT fk_contract_party_b_tenant
        FOREIGN KEY (tenant_id, party_b_id) REFERENCES md_partner (tenant_id, id);
