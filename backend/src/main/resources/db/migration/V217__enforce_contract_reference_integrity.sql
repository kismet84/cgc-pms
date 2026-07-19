-- Enforce tenant-consistent contract references and distinct parties.

ALTER TABLE md_partner
    ADD UNIQUE KEY uk_md_partner_tenant_id (tenant_id, id);

ALTER TABLE ct_contract
    MODIFY COLUMN party_a_id BIGINT NOT NULL COMMENT '甲方合作方ID',
    MODIFY COLUMN party_b_id BIGINT NOT NULL COMMENT '乙方合作方ID',
    ADD CONSTRAINT chk_contract_distinct_parties CHECK (party_a_id <> party_b_id),
    ADD KEY idx_contract_tenant_party_a (tenant_id, party_a_id),
    ADD KEY idx_contract_tenant_party_b (tenant_id, party_b_id),
    ADD CONSTRAINT fk_contract_project_tenant
        FOREIGN KEY (tenant_id, project_id) REFERENCES pm_project (tenant_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_contract_party_a_tenant
        FOREIGN KEY (tenant_id, party_a_id) REFERENCES md_partner (tenant_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_contract_party_b_tenant
        FOREIGN KEY (tenant_id, party_b_id) REFERENCES md_partner (tenant_id, id) ON DELETE RESTRICT;
