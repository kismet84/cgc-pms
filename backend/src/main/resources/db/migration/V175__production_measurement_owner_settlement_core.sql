CREATE TABLE measurement_period (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL, contract_id BIGINT NOT NULL,
    period_code VARCHAR(32) NOT NULL, period_name VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL, end_date DATE NOT NULL, cutoff_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN', version INT NOT NULL DEFAULT 0,
    created_by BIGINT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0, remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_measurement_period (tenant_id,contract_id,period_code,deleted_flag),
    KEY idx_measurement_period_project (tenant_id,project_id,start_date,end_date),
    CONSTRAINT fk_measurement_period_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON DELETE RESTRICT,
    CONSTRAINT fk_measurement_period_contract FOREIGN KEY (contract_id) REFERENCES ct_contract(id) ON DELETE RESTRICT,
    CONSTRAINT ck_measurement_period_dates CHECK (start_date<=end_date AND cutoff_date>=start_date),
    CONSTRAINT ck_measurement_period_status CHECK (status IN ('OPEN','CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE production_measurement (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL, contract_id BIGINT NOT NULL, period_id BIGINT NOT NULL,
    measure_code VARCHAR(64) NOT NULL, measure_date DATE NOT NULL,
    current_reported_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    cumulative_reported_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT', approval_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    approval_instance_id BIGINT NULL, attachment_count INT NOT NULL DEFAULT 0,
    formula_version VARCHAR(64) NOT NULL DEFAULT 'PRODUCTION_MEASUREMENT_V1', version INT NOT NULL DEFAULT 0,
    created_by BIGINT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0, remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_production_measure_code (tenant_id,measure_code,deleted_flag),
    UNIQUE KEY uk_production_measure_period (tenant_id,contract_id,period_id,deleted_flag),
    KEY idx_production_measure_project (tenant_id,project_id,measure_date,status),
    CONSTRAINT fk_production_measure_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON DELETE RESTRICT,
    CONSTRAINT fk_production_measure_contract FOREIGN KEY (contract_id) REFERENCES ct_contract(id) ON DELETE RESTRICT,
    CONSTRAINT fk_production_measure_period FOREIGN KEY (period_id) REFERENCES measurement_period(id) ON DELETE RESTRICT,
    CONSTRAINT fk_production_measure_approval FOREIGN KEY (approval_instance_id) REFERENCES wf_instance(id) ON DELETE RESTRICT,
    CONSTRAINT ck_production_measure_amount CHECK (current_reported_amount>=0 AND cumulative_reported_amount>=current_reported_amount)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE production_measurement_line (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, measurement_id BIGINT NOT NULL,
    source_type VARCHAR(32) NOT NULL, contract_item_id BIGINT NULL, contract_change_id BIGINT NULL,
    item_code VARCHAR(64) NULL, item_name VARCHAR(200) NOT NULL, item_spec VARCHAR(300) NULL, unit VARCHAR(50) NULL,
    contract_quantity DECIMAL(18,4) NOT NULL, prior_approved_quantity DECIMAL(18,4) NOT NULL DEFAULT 0,
    current_reported_quantity DECIMAL(18,4) NOT NULL, cumulative_reported_quantity DECIMAL(18,4) NOT NULL,
    unit_price DECIMAL(18,4) NOT NULL, current_reported_amount DECIMAL(18,2) NOT NULL,
    cumulative_reported_amount DECIMAL(18,2) NOT NULL, evidence_count INT NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0, created_by BIGINT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_production_measure_item (tenant_id,measurement_id,contract_item_id),
    UNIQUE KEY uk_production_measure_change (tenant_id,measurement_id,contract_change_id),
    KEY idx_production_measure_line_item (tenant_id,contract_item_id),
    KEY idx_production_measure_line_change (tenant_id,contract_change_id),
    CONSTRAINT fk_production_measure_line_header FOREIGN KEY (measurement_id) REFERENCES production_measurement(id) ON DELETE RESTRICT,
    CONSTRAINT fk_production_measure_line_item FOREIGN KEY (contract_item_id) REFERENCES ct_contract_item(id) ON DELETE RESTRICT,
    CONSTRAINT fk_production_measure_line_change FOREIGN KEY (contract_change_id) REFERENCES ct_contract_change(id) ON DELETE RESTRICT,
    CONSTRAINT ck_production_measure_line_source CHECK ((contract_item_id IS NOT NULL AND contract_change_id IS NULL) OR (contract_item_id IS NULL AND contract_change_id IS NOT NULL)),
    CONSTRAINT ck_production_measure_line_quantity CHECK (contract_quantity>0 AND prior_approved_quantity>=0 AND current_reported_quantity>0 AND cumulative_reported_quantity=prior_approved_quantity+current_reported_quantity AND cumulative_reported_quantity<=contract_quantity),
    CONSTRAINT ck_production_measure_line_amount CHECK (unit_price>=0 AND current_reported_amount>=0 AND cumulative_reported_amount>=current_reported_amount)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE owner_measurement_submission (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL, contract_id BIGINT NOT NULL, measurement_id BIGINT NOT NULL,
    submission_code VARCHAR(64) NOT NULL, revision_no INT NOT NULL,
    submitted_at DATETIME NOT NULL, external_document_no VARCHAR(128) NULL,
    submitted_amount DECIMAL(18,2) NOT NULL, confirmed_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    deducted_amount DECIMAL(18,2) NOT NULL DEFAULT 0, status VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED',
    reviewer_name VARCHAR(100) NULL, review_comment VARCHAR(500) NULL, reviewed_at DATETIME NULL,
    attachment_count INT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
    created_by BIGINT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0, remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_owner_measure_submission_code (tenant_id,submission_code,deleted_flag),
    UNIQUE KEY uk_owner_measure_revision (tenant_id,measurement_id,revision_no,deleted_flag),
    KEY idx_owner_measure_submission (tenant_id,project_id,status,submitted_at),
    CONSTRAINT fk_owner_measure_submission_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON DELETE RESTRICT,
    CONSTRAINT fk_owner_measure_submission_contract FOREIGN KEY (contract_id) REFERENCES ct_contract(id) ON DELETE RESTRICT,
    CONSTRAINT fk_owner_measure_submission_measure FOREIGN KEY (measurement_id) REFERENCES production_measurement(id) ON DELETE RESTRICT,
    CONSTRAINT ck_owner_measure_submission_amount CHECK (submitted_amount>0 AND confirmed_amount>=0 AND deducted_amount>=0 AND confirmed_amount+deducted_amount<=submitted_amount)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE owner_measurement_review_line (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0,
    submission_id BIGINT NOT NULL, measurement_line_id BIGINT NOT NULL,
    submitted_quantity DECIMAL(18,4) NOT NULL, submitted_amount DECIMAL(18,2) NOT NULL,
    confirmed_quantity DECIMAL(18,4) NULL, confirmed_amount DECIMAL(18,2) NULL,
    deducted_amount DECIMAL(18,2) NULL, deduction_reason VARCHAR(500) NULL,
    created_by BIGINT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_owner_review_measure_line (tenant_id,submission_id,measurement_line_id),
    CONSTRAINT fk_owner_review_submission FOREIGN KEY (submission_id) REFERENCES owner_measurement_submission(id) ON DELETE RESTRICT,
    CONSTRAINT fk_owner_review_measure_line FOREIGN KEY (measurement_line_id) REFERENCES production_measurement_line(id) ON DELETE RESTRICT,
    CONSTRAINT ck_owner_review_quantity CHECK (submitted_quantity>0 AND (confirmed_quantity IS NULL OR (confirmed_quantity>=0 AND confirmed_quantity<=submitted_quantity)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE owner_settlement
    ADD COLUMN production_measurement_id BIGINT NULL,
    ADD COLUMN owner_submission_id BIGINT NULL,
    ADD COLUMN reported_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    ADD COLUMN deducted_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    ADD CONSTRAINT fk_owner_settlement_measurement FOREIGN KEY (production_measurement_id) REFERENCES production_measurement(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_owner_settlement_submission FOREIGN KEY (owner_submission_id) REFERENCES owner_measurement_submission(id) ON DELETE RESTRICT;
CREATE UNIQUE INDEX uk_owner_settlement_submission ON owner_settlement(tenant_id,owner_submission_id,deleted_flag);

INSERT IGNORE INTO wf_template(id,tenant_id,template_code,template_name,business_type,enabled,amount_min,amount_max,created_by,remark)
VALUES(50032,0,'TPL-PRODUCTION-MEASUREMENT-001','产值计量审批流程','PRODUCTION_MEASUREMENT',1,0.01,999999999999.99,1,'产值计量闭环：项目、商务、分管领导三级审批');
INSERT IGNORE INTO wf_template_node(id,tenant_id,template_id,node_code,node_name,node_order,node_type,approve_mode,approver_config,allow_transfer,allow_add_sign,timeout_hours)
VALUES
(53201,0,50032,'N1','项目经理审批',1,'APPROVAL','SEQUENTIAL',JSON_OBJECT('type','USER','userId',1),1,1,48),
(53202,0,50032,'N2','商务经理审批',2,'APPROVAL','SEQUENTIAL',JSON_OBJECT('type','USER','userId',1),1,1,48),
(53203,0,50032,'N3','分管领导审批',3,'APPROVAL','SEQUENTIAL',JSON_OBJECT('type','USER','userId',1),1,1,48);

INSERT IGNORE INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
VALUES
(1080,0,2,'产值计量','MENU','production-measurement','production-measurement/index','measurement:query','calculator',9,'ENABLE',1),
(1081,0,1080,'维护计量','BUTTON',NULL,NULL,'measurement:maintain',NULL,1,'ENABLE',1),
(1082,0,1080,'提交内部审批','BUTTON',NULL,NULL,'measurement:submit',NULL,2,'ENABLE',1),
(1083,0,1080,'提交业主报量','BUTTON',NULL,NULL,'measurement:owner:submit',NULL,3,'ENABLE',1),
(1084,0,1080,'登记业主核定','BUTTON',NULL,NULL,'measurement:owner:review',NULL,4,'ENABLE',1);
INSERT IGNORE INTO sys_role_menu(id,role_id,menu_id)
SELECT 175000000+r.id*10000+m.id,r.id,m.id FROM sys_role r JOIN sys_menu m ON m.id BETWEEN 1080 AND 1084
WHERE r.role_code IN ('SUPER_ADMIN','ADMIN','PROJECT_MANAGER','COST_MANAGER','FINANCE','AUDITOR') AND r.deleted_flag=0;
