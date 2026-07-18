-- 银行入账回单的业务上下文及租户感知回款关联。
ALTER TABLE collection_record ADD UNIQUE KEY uk_collection_tenant_id (tenant_id,id);

ALTER TABLE bank_receipt
    ADD COLUMN project_id BIGINT NULL COMMENT '入账所属项目ID',
    ADD COLUMN contract_id BIGINT NULL COMMENT '入账所属合同ID',
    ADD COLUMN customer_id BIGINT NULL COMMENT '付款客户ID',
    ADD COLUMN fund_account_id BIGINT NULL COMMENT '收款资金账户ID',
    ADD COLUMN allocation_json LONGTEXT NULL COMMENT '银行回单对应应收分配(JSON数组)',
    ADD KEY idx_bank_receipt_collection_context (tenant_id,project_id,contract_id,customer_id),
    ADD CONSTRAINT fk_bank_receipt_project FOREIGN KEY(project_id) REFERENCES pm_project(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_bank_receipt_contract FOREIGN KEY(contract_id) REFERENCES ct_contract(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_bank_receipt_customer FOREIGN KEY(customer_id) REFERENCES md_partner(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_bank_receipt_fund_account FOREIGN KEY(fund_account_id) REFERENCES fund_account(id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_bank_receipt_direction_194 CHECK(direction IN ('IN','OUT')),
    ADD CONSTRAINT ck_bank_receipt_status_194 CHECK(match_status IN ('UNMATCHED','MATCHED','MANUAL_REVIEW','IGNORED')),
    ADD CONSTRAINT ck_bank_receipt_link_194 CHECK(
        (collection_record_id IS NULL OR direction='IN') AND (pay_record_id IS NULL OR direction='OUT')
    ),
    ADD CONSTRAINT ck_bank_receipt_context_194 CHECK(
        direction='OUT' OR
        (project_id IS NULL AND contract_id IS NULL AND customer_id IS NULL AND fund_account_id IS NULL) OR
        (project_id IS NOT NULL AND contract_id IS NOT NULL AND customer_id IS NOT NULL AND fund_account_id IS NOT NULL)
    ),
    ADD CONSTRAINT ck_bank_receipt_allocation_json_194 CHECK(allocation_json IS NULL OR JSON_VALID(allocation_json));

UPDATE bank_receipt b
JOIN collection_record c ON c.id=b.collection_record_id AND c.tenant_id=b.tenant_id
SET b.project_id=c.project_id,b.contract_id=c.contract_id,b.customer_id=c.customer_id,b.fund_account_id=c.fund_account_id
WHERE b.collection_record_id IS NOT NULL;

ALTER TABLE bank_receipt DROP FOREIGN KEY fk_bank_receipt_collection;
ALTER TABLE bank_receipt ADD CONSTRAINT fk_bank_receipt_collection_194
    FOREIGN KEY(tenant_id,collection_record_id) REFERENCES collection_record(tenant_id,id) ON DELETE RESTRICT;
