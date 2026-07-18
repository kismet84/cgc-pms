SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE cost_subject_mapping_version (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    version_code VARCHAR(64) NOT NULL,
    version_name VARCHAR(200) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    effective_date DATE NULL,
    approval_instance_id BIGINT NULL,
    activated_by BIGINT NULL,
    activated_at DATETIME NULL,
    version INT NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cost_subject_mapping_version (tenant_id, version_code),
    KEY idx_cost_subject_mapping_status (tenant_id, status, effective_date),
    CONSTRAINT fk_cost_subject_mapping_approval FOREIGN KEY (approval_instance_id) REFERENCES wf_instance(id) ON DELETE RESTRICT,
    CONSTRAINT ck_cost_subject_mapping_status CHECK (status IN ('DRAFT','ACTIVE','RETIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='成本科目V2映射版本';

CREATE TABLE cost_subject_mapping_item (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    mapping_version_id BIGINT NOT NULL,
    source_subject_id BIGINT NOT NULL,
    target_group_code VARCHAR(64) NOT NULL,
    target_subject_id BIGINT NULL,
    historical_display_name VARCHAR(200) NOT NULL,
    mapping_reason VARCHAR(500) NULL,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cost_subject_mapping_item (tenant_id, mapping_version_id, source_subject_id),
    KEY idx_cost_subject_mapping_target (tenant_id, target_group_code, target_subject_id),
    CONSTRAINT fk_cost_subject_mapping_version FOREIGN KEY (mapping_version_id) REFERENCES cost_subject_mapping_version(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cost_subject_mapping_source FOREIGN KEY (source_subject_id) REFERENCES cost_subject(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cost_subject_mapping_target FOREIGN KEY (target_subject_id) REFERENCES cost_subject(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='历史成本科目到V2口径映射';

CREATE TABLE cost_subject_assignment_rule (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    mapping_version_id BIGINT NOT NULL,
    rule_code VARCHAR(64) NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    business_category VARCHAR(64) NOT NULL DEFAULT '*',
    project_id BIGINT NULL,
    cost_subject_id BIGINT NOT NULL,
    priority INT NOT NULL DEFAULT 100,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    effective_from DATE NOT NULL,
    effective_to DATE NULL,
    version INT NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cost_subject_rule_code (tenant_id, rule_code),
    KEY idx_cost_subject_rule_match (tenant_id, source_type, business_category, project_id, status, priority),
    CONSTRAINT fk_cost_subject_rule_version FOREIGN KEY (mapping_version_id) REFERENCES cost_subject_mapping_version(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cost_subject_rule_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cost_subject_rule_subject FOREIGN KEY (cost_subject_id) REFERENCES cost_subject(id) ON DELETE RESTRICT,
    CONSTRAINT ck_cost_subject_rule_status CHECK (status IN ('DRAFT','ACTIVE','RETIRED')),
    CONSTRAINT ck_cost_subject_rule_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='显式成本归集规则';

CREATE TABLE project_cost_subject_scope (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL,
    cost_subject_id BIGINT NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    effective_from DATE NOT NULL,
    effective_to DATE NULL,
    version INT NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_cost_subject_scope (tenant_id, project_id, cost_subject_id),
    KEY idx_project_cost_subject_enabled (tenant_id, project_id, enabled),
    CONSTRAINT fk_project_cost_subject_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON DELETE RESTRICT,
    CONSTRAINT fk_project_cost_subject_subject FOREIGN KEY (cost_subject_id) REFERENCES cost_subject(id) ON DELETE RESTRICT,
    CONSTRAINT ck_project_cost_subject_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目可用成本科目范围';

CREATE TABLE bid_cost_target_transfer (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    bid_cost_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    target_id BIGINT NOT NULL,
    mapping_version_id BIGINT NOT NULL,
    transfer_code VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    total_amount DECIMAL(18,2) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'POSTED',
    approval_instance_id BIGINT NOT NULL,
    reversal_of_id BIGINT NULL,
    posted_by BIGINT NOT NULL,
    posted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bid_target_transfer_code (tenant_id, transfer_code),
    UNIQUE KEY uk_bid_target_transfer_idempotency (tenant_id, idempotency_key),
    UNIQUE KEY uk_bid_target_transfer_reversal (tenant_id, reversal_of_id),
    KEY idx_bid_target_transfer_trace (tenant_id, bid_cost_id, project_id, target_id, status),
    CONSTRAINT fk_bid_target_transfer_bid FOREIGN KEY (bid_cost_id) REFERENCES bid_cost(id) ON DELETE RESTRICT,
    CONSTRAINT fk_bid_target_transfer_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON DELETE RESTRICT,
    CONSTRAINT fk_bid_target_transfer_target FOREIGN KEY (target_id) REFERENCES cost_target(id) ON DELETE RESTRICT,
    CONSTRAINT fk_bid_target_transfer_mapping FOREIGN KEY (mapping_version_id) REFERENCES cost_subject_mapping_version(id) ON DELETE RESTRICT,
    CONSTRAINT fk_bid_target_transfer_approval FOREIGN KEY (approval_instance_id) REFERENCES wf_instance(id) ON DELETE RESTRICT,
    CONSTRAINT fk_bid_target_transfer_reversal FOREIGN KEY (reversal_of_id) REFERENCES bid_cost_target_transfer(id) ON DELETE RESTRICT,
    CONSTRAINT ck_bid_target_transfer_amount CHECK (total_amount <> 0),
    CONSTRAINT ck_bid_target_transfer_status CHECK (status IN ('POSTED','REVERSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='投标成本转入目标成本不可变事实';

CREATE TABLE bid_cost_target_transfer_line (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    transfer_id BIGINT NOT NULL,
    source_cost_item_id BIGINT NOT NULL,
    source_subject_id BIGINT NOT NULL,
    target_subject_id BIGINT NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bid_target_transfer_source (tenant_id, transfer_id, source_cost_item_id),
    KEY idx_bid_target_transfer_line_target (tenant_id, target_subject_id),
    CONSTRAINT fk_bid_target_transfer_line_header FOREIGN KEY (transfer_id) REFERENCES bid_cost_target_transfer(id) ON DELETE RESTRICT,
    CONSTRAINT fk_bid_target_transfer_line_cost FOREIGN KEY (source_cost_item_id) REFERENCES cost_item(id) ON DELETE RESTRICT,
    CONSTRAINT fk_bid_target_transfer_line_source_subject FOREIGN KEY (source_subject_id) REFERENCES cost_subject(id) ON DELETE RESTRICT,
    CONSTRAINT fk_bid_target_transfer_line_target_subject FOREIGN KEY (target_subject_id) REFERENCES cost_subject(id) ON DELETE RESTRICT,
    CONSTRAINT ck_bid_target_transfer_line_amount CHECK (amount <> 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='投标成本转入明细';

CREATE TABLE finance_cost_allocation_batch (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    batch_code VARCHAR(64) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_id BIGINT NOT NULL,
    source_amount DECIMAL(18,2) NOT NULL,
    allocation_basis VARCHAR(32) NOT NULL,
    accounting_period CHAR(7) NOT NULL,
    cost_subject_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'POSTED',
    approval_instance_id BIGINT NOT NULL,
    reversal_of_id BIGINT NULL,
    posted_by BIGINT NOT NULL,
    posted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_finance_cost_batch_code (tenant_id, batch_code),
    UNIQUE KEY uk_finance_cost_idempotency (tenant_id, idempotency_key),
    UNIQUE KEY uk_finance_cost_reversal (tenant_id, reversal_of_id),
    KEY idx_finance_cost_source (tenant_id, source_type, source_id, status),
    CONSTRAINT fk_finance_cost_subject FOREIGN KEY (cost_subject_id) REFERENCES cost_subject(id) ON DELETE RESTRICT,
    CONSTRAINT fk_finance_cost_approval FOREIGN KEY (approval_instance_id) REFERENCES wf_instance(id) ON DELETE RESTRICT,
    CONSTRAINT fk_finance_cost_reversal FOREIGN KEY (reversal_of_id) REFERENCES finance_cost_allocation_batch(id) ON DELETE RESTRICT,
    CONSTRAINT ck_finance_cost_source CHECK (source_type IN ('ACCOUNTING_ENTRY_LINE','EXPENSE_APPLICATION')),
    CONSTRAINT ck_finance_cost_basis CHECK (allocation_basis IN ('DIRECT_PROJECT','BENEFIT_AMOUNT','OCCUPIED_DAYS','CONTRACT_AMOUNT_EXCEPTION')),
    CONSTRAINT ck_finance_cost_amount CHECK (source_amount <> 0),
    CONSTRAINT ck_finance_cost_status CHECK (status IN ('POSTED','REVERSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目财务费用分摊批次';

CREATE TABLE finance_cost_allocation_line (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    batch_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    basis_value DECIMAL(18,6) NOT NULL,
    allocated_amount DECIMAL(18,2) NOT NULL,
    cost_item_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_finance_cost_project (tenant_id, batch_id, project_id),
    KEY idx_finance_cost_line_project (tenant_id, project_id),
    CONSTRAINT fk_finance_cost_line_batch FOREIGN KEY (batch_id) REFERENCES finance_cost_allocation_batch(id) ON DELETE RESTRICT,
    CONSTRAINT fk_finance_cost_line_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON DELETE RESTRICT,
    CONSTRAINT fk_finance_cost_line_cost FOREIGN KEY (cost_item_id) REFERENCES cost_item(id) ON DELETE RESTRICT,
    CONSTRAINT ck_finance_cost_line_values CHECK (basis_value > 0 AND allocated_amount <> 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目财务费用分摊明细';

ALTER TABLE qs_consequence
    ADD COLUMN cost_subject_id BIGINT NULL COMMENT 'V2质量安全成本末级科目',
    ADD CONSTRAINT fk_qs_consequence_subject FOREIGN KEY (cost_subject_id) REFERENCES cost_subject(id) ON DELETE RESTRICT;

INSERT IGNORE INTO wf_template
    (id,tenant_id,template_code,template_name,business_type,enabled,created_by,remark)
VALUES
    (50050,0,'TPL-COST-SUBJECT-MAPPING-V2','成本科目映射版本审批','COST_SUBJECT_MAPPING',1,1,'第51条主线：映射版本启用门禁'),
    (50051,0,'TPL-BID-TARGET-TRANSFER-V2','投标成本转入目标成本审批','BID_COST_TARGET_TRANSFER',1,1,'第51条主线：投标成本转入门禁'),
    (50052,0,'TPL-BID-TARGET-REVERSAL-V2','投标成本转入冲销审批','BID_COST_TARGET_TRANSFER_REVERSAL',1,1,'第51条主线：投标成本冲销门禁'),
    (50053,0,'TPL-FINANCE-COST-ALLOCATION-V2','项目财务费用分摊审批','FINANCE_COST_ALLOCATION',1,1,'第51条主线：项目财务费用分摊门禁'),
    (50054,0,'TPL-FINANCE-COST-REVERSAL-V2','项目财务费用分摊冲销审批','FINANCE_COST_ALLOCATION_REVERSAL',1,1,'第51条主线：项目财务费用冲销门禁');

INSERT IGNORE INTO wf_template_node
    (id,tenant_id,template_id,node_code,node_name,node_order,node_type,approve_mode,approver_config,allow_transfer,allow_add_sign,timeout_hours)
VALUES
    (55001,0,50050,'COST_MANAGER','成本经理审批',1,'APPROVAL','SEQUENTIAL',JSON_OBJECT('type','ROLE','roleCode','COST_MANAGER'),1,1,48),
    (55002,0,50051,'PROJECT_MANAGER','项目经理审批',1,'APPROVAL','SEQUENTIAL',JSON_OBJECT('type','ROLE','roleCode','PROJECT_MANAGER'),1,1,48),
    (55003,0,50051,'COST_MANAGER','成本经理审批',2,'APPROVAL','SEQUENTIAL',JSON_OBJECT('type','ROLE','roleCode','COST_MANAGER'),1,1,48),
    (55004,0,50052,'COST_MANAGER','成本经理审批',1,'APPROVAL','SEQUENTIAL',JSON_OBJECT('type','ROLE','roleCode','COST_MANAGER'),1,1,48),
    (55005,0,50053,'FINANCE','财务审批',1,'APPROVAL','SEQUENTIAL',JSON_OBJECT('type','ROLE','roleCode','FINANCE'),1,1,48),
    (55006,0,50053,'COST_MANAGER','成本经理审批',2,'APPROVAL','SEQUENTIAL',JSON_OBJECT('type','ROLE','roleCode','COST_MANAGER'),1,1,48),
    (55007,0,50054,'FINANCE','财务审批',1,'APPROVAL','SEQUENTIAL',JSON_OBJECT('type','ROLE','roleCode','FINANCE'),1,1,48),
    (55008,0,50054,'COST_MANAGER','成本经理审批',2,'APPROVAL','SEQUENTIAL',JSON_OBJECT('type','ROLE','roleCode','COST_MANAGER'),1,1,48);

INSERT IGNORE INTO sys_menu
    (id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
VALUES
    (2130,0,930,'维护成本科目映射','BUTTON',NULL,NULL,'cost:subject:mapping:edit',NULL,10,'ENABLE',1),
    (2131,0,930,'启用成本科目映射','BUTTON',NULL,NULL,'cost:subject:mapping:activate',NULL,11,'ENABLE',1),
    (2132,0,930,'维护归集规则','BUTTON',NULL,NULL,'cost:subject:rule:edit',NULL,12,'ENABLE',1),
    (2133,0,930,'维护项目适用范围','BUTTON',NULL,NULL,'cost:subject:scope:edit',NULL,13,'ENABLE',1),
    (2134,0,930,'查询影响与对账','BUTTON',NULL,NULL,'cost:subject:audit:query',NULL,14,'ENABLE',1),
    (2135,0,930,'执行投标成本转入','BUTTON',NULL,NULL,'cost:subject:bid-transfer',NULL,15,'ENABLE',1),
    (2136,0,930,'执行财务费用分摊','BUTTON',NULL,NULL,'cost:subject:finance-allocate',NULL,16,'ENABLE',1),
    (2137,0,930,'查询成本科目映射','BUTTON',NULL,NULL,'cost:subject:mapping:query',NULL,17,'ENABLE',1),
    (2138,0,930,'查询归集规则','BUTTON',NULL,NULL,'cost:subject:rule:query',NULL,18,'ENABLE',1),
    (2139,0,930,'查询项目适用范围','BUTTON',NULL,NULL,'cost:subject:scope:query',NULL,19,'ENABLE',1);

INSERT IGNORE INTO sys_role_menu (id,tenant_id,role_id,menu_id)
SELECT 213000000+r.id*10000+m.id,r.tenant_id,r.id,m.id
FROM sys_role r JOIN sys_menu m ON m.tenant_id=r.tenant_id AND m.id IN (2130,2131,2132,2133,2134,2137,2138,2139) AND m.deleted_flag=0
WHERE r.role_code IN ('SUPER_ADMIN','ADMIN','COST_MANAGER') AND r.deleted_flag=0;

INSERT IGNORE INTO sys_role_menu (id,tenant_id,role_id,menu_id)
SELECT 213100000+r.id*10000+m.id,r.tenant_id,r.id,m.id
FROM sys_role r JOIN sys_menu m ON m.tenant_id=r.tenant_id AND m.id=2135 AND m.deleted_flag=0
WHERE r.role_code IN ('SUPER_ADMIN','ADMIN','COST_MANAGER','PROJECT_MANAGER') AND r.deleted_flag=0;

INSERT IGNORE INTO sys_role_menu (id,tenant_id,role_id,menu_id)
SELECT 213200000+r.id*10000+m.id,r.tenant_id,r.id,m.id
FROM sys_role r JOIN sys_menu m ON m.tenant_id=r.tenant_id AND m.id=2136 AND m.deleted_flag=0
WHERE r.role_code IN ('SUPER_ADMIN','ADMIN','FINANCE') AND r.deleted_flag=0;

SET FOREIGN_KEY_CHECKS = 1;
