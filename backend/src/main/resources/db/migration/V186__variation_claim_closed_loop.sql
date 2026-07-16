ALTER TABLE var_order
    ADD COLUMN event_date DATE NULL COMMENT '变更事件日期',
    ADD COLUMN claim_deadline DATE NULL COMMENT '合同约定索赔申报截止日',
    ADD COLUMN event_description VARCHAR(1000) NULL COMMENT '现场事件及影响说明',
    ADD COLUMN cause_category VARCHAR(64) NULL COMMENT '原因分类',
    ADD COLUMN responsible_party VARCHAR(200) NULL COMMENT '责任方',
    ADD COLUMN estimated_cost_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '内部成本测算金额',
    ADD COLUMN owner_status VARCHAR(32) NOT NULL DEFAULT 'NOT_READY' COMMENT '业主申报生命周期',
    ADD COLUMN internal_approval_instance_id BIGINT NULL COMMENT '内部审批实例',
    ADD COLUMN generated_contract_change_id BIGINT NULL COMMENT '业主核定后生成的正式合同变更',
    ADD COLUMN version INT NOT NULL DEFAULT 0;

UPDATE var_order
SET event_date = COALESCE(event_date, DATE(created_at)),
    event_description = COALESCE(NULLIF(event_description,''), var_name),
    cause_category = COALESCE(NULLIF(cause_category,''), var_type),
    estimated_cost_amount = COALESCE(estimated_cost_amount, reported_amount, 0),
    owner_status = CASE
        WHEN owner_confirm_flag = 1 THEN 'CONFIRMED_LEGACY'
        WHEN approval_status = 'APPROVED' THEN 'INTERNAL_APPROVED'
        ELSE 'NOT_READY'
    END;

ALTER TABLE var_order_item
    ADD COLUMN claim_unit_price DECIMAL(18,4) NULL COMMENT '对业主申报单价',
    ADD COLUMN claim_amount DECIMAL(18,2) NULL COMMENT '对业主申报金额';

UPDATE var_order_item
SET claim_unit_price = COALESCE(claim_unit_price, unit_price, 0),
    claim_amount = COALESCE(claim_amount, amount, 0);

ALTER TABLE ct_contract_change
    ADD COLUMN source_var_order_id BIGINT NULL COMMENT '来源变更签证';

CREATE TABLE variation_owner_submission (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL,
    contract_id BIGINT NOT NULL,
    var_order_id BIGINT NOT NULL,
    revision_no INT NOT NULL,
    submission_code VARCHAR(64) NOT NULL,
    external_document_no VARCHAR(128) NOT NULL,
    submitted_amount DECIMAL(18,2) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED',
    submitted_at DATETIME NOT NULL,
    submitted_by BIGINT NOT NULL,
    response_document_no VARCHAR(128) NULL,
    response_comment VARCHAR(500) NULL,
    confirmed_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    reviewed_at DATETIME NULL,
    reviewed_by BIGINT NULL,
    generated_contract_change_id BIGINT NULL,
    version INT NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_variation_owner_submission_code (tenant_id, submission_code, deleted_flag),
    UNIQUE KEY uk_variation_owner_submission_revision (tenant_id, var_order_id, revision_no, deleted_flag),
    KEY idx_variation_owner_submission_status (tenant_id, project_id, status, submitted_at),
    CONSTRAINT fk_variation_owner_submission_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON DELETE RESTRICT,
    CONSTRAINT fk_variation_owner_submission_contract FOREIGN KEY (contract_id) REFERENCES ct_contract(id) ON DELETE RESTRICT,
    CONSTRAINT fk_variation_owner_submission_order FOREIGN KEY (var_order_id) REFERENCES var_order(id) ON DELETE RESTRICT,
    CONSTRAINT ck_variation_owner_submission_amount CHECK (submitted_amount > 0 AND confirmed_amount >= 0 AND confirmed_amount <= submitted_amount),
    CONSTRAINT ck_variation_owner_submission_status CHECK (status IN ('SUBMITTED','RETURNED','CONFIRMED','CHANGE_PENDING','CHANGE_EFFECTIVE','CHANGE_REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='变更签证对业主申报版本';

CREATE TABLE variation_owner_submission_item (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    submission_id BIGINT NOT NULL,
    var_order_item_id BIGINT NOT NULL,
    item_name VARCHAR(200) NOT NULL,
    unit VARCHAR(20) NULL,
    quantity DECIMAL(18,4) NOT NULL,
    claimed_unit_price DECIMAL(18,4) NOT NULL,
    claimed_amount DECIMAL(18,2) NOT NULL,
    confirmed_amount DECIMAL(18,2) NULL,
    reduction_reason VARCHAR(500) NULL,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_variation_owner_submission_item (tenant_id, submission_id, var_order_item_id),
    CONSTRAINT fk_variation_owner_item_submission FOREIGN KEY (submission_id) REFERENCES variation_owner_submission(id) ON DELETE RESTRICT,
    CONSTRAINT fk_variation_owner_item_source FOREIGN KEY (var_order_item_id) REFERENCES var_order_item(id) ON DELETE RESTRICT,
    CONSTRAINT ck_variation_owner_item_amount CHECK (quantity > 0 AND claimed_unit_price >= 0 AND claimed_amount > 0 AND (confirmed_amount IS NULL OR (confirmed_amount >= 0 AND confirmed_amount <= claimed_amount)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='变更签证业主申报及核定明细快照';

ALTER TABLE ct_contract_change
    ADD CONSTRAINT fk_ct_change_source_var_order FOREIGN KEY (source_var_order_id) REFERENCES var_order(id) ON DELETE RESTRICT,
    ADD UNIQUE KEY uk_ct_change_source_var_order (tenant_id, source_var_order_id);

ALTER TABLE variation_owner_submission
    ADD CONSTRAINT fk_variation_owner_submission_change FOREIGN KEY (generated_contract_change_id) REFERENCES ct_contract_change(id) ON DELETE RESTRICT;

ALTER TABLE var_order
    ADD CONSTRAINT fk_var_order_internal_approval FOREIGN KEY (internal_approval_instance_id) REFERENCES wf_instance(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_var_order_generated_change FOREIGN KEY (generated_contract_change_id) REFERENCES ct_contract_change(id) ON DELETE RESTRICT,
    ADD KEY idx_var_order_owner_status (tenant_id, project_id, owner_status, event_date),
    ADD CONSTRAINT ck_var_order_estimated_cost CHECK (estimated_cost_amount >= 0);

INSERT IGNORE INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
VALUES
(1090,0,921,'提交业主申报','BUTTON',NULL,NULL,'variation:owner:submit',NULL,10,'ENABLE',1),
(1091,0,921,'登记业主核定','BUTTON',NULL,NULL,'variation:owner:review',NULL,11,'ENABLE',1),
(1092,0,921,'变更全链追溯','BUTTON',NULL,NULL,'variation:trace',NULL,12,'ENABLE',1);

INSERT IGNORE INTO sys_role_menu(id,role_id,menu_id)
SELECT 186000000+r.id*10000+m.id,r.id,m.id
FROM sys_role r JOIN sys_menu m ON m.id BETWEEN 1090 AND 1092
WHERE r.role_code IN ('SUPER_ADMIN','ADMIN','PROJECT_MANAGER','COST_MANAGER','FINANCE','AUDITOR') AND r.deleted_flag=0;
