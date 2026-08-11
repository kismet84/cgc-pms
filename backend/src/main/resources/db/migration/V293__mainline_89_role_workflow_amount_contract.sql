-- Mainline 89: fixed role catalog, amount permissions, workflow snapshots and 21 default flows.

-- Runtime workflow snapshots must be populated before template replacement.
ALTER TABLE wf_instance
    ADD COLUMN security_policy_json JSON NULL AFTER variables;

ALTER TABLE wf_node_instance
    ADD COLUMN node_type VARCHAR(50) NULL AFTER approve_mode,
    ADD COLUMN approver_config JSON NULL AFTER node_type,
    ADD COLUMN allow_transfer TINYINT NULL AFTER approver_config,
    ADD COLUMN allow_add_sign TINYINT NULL AFTER allow_transfer,
    ADD COLUMN timeout_hours INT NULL AFTER allow_add_sign;

UPDATE wf_instance
SET security_policy_json = '{"preventInitiatorApproval":false,"maxApprovalsPerUser":100,"requireProjectMembership":false,"allowAdminFallback":true}'
WHERE security_policy_json IS NULL;

UPDATE wf_node_instance ni
LEFT JOIN wf_template_node tn ON tn.id = ni.template_node_id
SET ni.node_type = COALESCE(tn.node_type, 'APPROVAL'),
    ni.approver_config = tn.approver_config,
    ni.allow_transfer = COALESCE(tn.allow_transfer, 0),
    ni.allow_add_sign = COALESCE(tn.allow_add_sign, 0),
    ni.timeout_hours = tn.timeout_hours
WHERE ni.approver_config IS NULL;

ALTER TABLE sys_operation_audit_log
    ADD COLUMN before_snapshot JSON NULL AFTER error_code,
    ADD COLUMN after_snapshot JSON NULL AFTER before_snapshot;

-- Approval command snapshots preserve immutable posted facts until final approval.
CREATE TABLE bid_cost_target_transfer_request (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    request_code VARCHAR(64) NOT NULL,
    bid_cost_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    target_id BIGINT NOT NULL,
    mapping_version_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    total_amount DECIMAL(18,2) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    approval_instance_id BIGINT NULL,
    final_transfer_id BIGINT NULL,
    version INT NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    active_guard TINYINT GENERATED ALWAYS AS (
        CASE WHEN deleted_flag=0 AND status IN ('DRAFT','SUBMITTED') THEN 1 ELSE NULL END
    ) STORED,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bid_transfer_request_code (tenant_id, request_code, deleted_flag),
    UNIQUE KEY uk_bid_transfer_request_idem (tenant_id, idempotency_key, deleted_flag),
    UNIQUE KEY uk_bid_transfer_request_active (tenant_id, bid_cost_id, target_id, active_guard),
    KEY idx_bid_transfer_request_project (tenant_id, project_id, status),
    KEY idx_bid_transfer_request_source (tenant_id, bid_cost_id, target_id, status),
    CONSTRAINT fk_bid_transfer_request_bid FOREIGN KEY (bid_cost_id) REFERENCES bid_cost(id) ON DELETE RESTRICT,
    CONSTRAINT fk_bid_transfer_request_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON DELETE RESTRICT,
    CONSTRAINT fk_bid_transfer_request_target FOREIGN KEY (target_id) REFERENCES cost_target(id) ON DELETE RESTRICT,
    CONSTRAINT fk_bid_transfer_request_mapping FOREIGN KEY (mapping_version_id) REFERENCES cost_subject_mapping_version(id) ON DELETE RESTRICT,
    CONSTRAINT fk_bid_transfer_request_instance FOREIGN KEY (approval_instance_id) REFERENCES wf_instance(id) ON DELETE RESTRICT,
    CONSTRAINT fk_bid_transfer_request_final FOREIGN KEY (final_transfer_id) REFERENCES bid_cost_target_transfer(id) ON DELETE RESTRICT,
    CONSTRAINT ck_bid_transfer_request_status CHECK (status IN ('DRAFT','SUBMITTED','REJECTED','WITHDRAWN','POSTED')),
    CONSTRAINT ck_bid_transfer_request_amount CHECK (total_amount <> 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE bid_cost_target_transfer_request_line (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    request_id BIGINT NOT NULL,
    source_cost_item_id BIGINT NOT NULL,
    source_subject_id BIGINT NOT NULL,
    target_subject_id BIGINT NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bid_transfer_request_line (tenant_id, request_id, source_cost_item_id, target_subject_id),
    CONSTRAINT fk_bid_transfer_request_line_request FOREIGN KEY (request_id) REFERENCES bid_cost_target_transfer_request(id) ON DELETE CASCADE,
    CONSTRAINT fk_bid_transfer_request_line_source_item FOREIGN KEY (source_cost_item_id) REFERENCES cost_item(id) ON DELETE RESTRICT,
    CONSTRAINT fk_bid_transfer_request_line_source_subject FOREIGN KEY (source_subject_id) REFERENCES cost_subject(id) ON DELETE RESTRICT,
    CONSTRAINT fk_bid_transfer_request_line_target_subject FOREIGN KEY (target_subject_id) REFERENCES cost_subject(id) ON DELETE RESTRICT,
    CONSTRAINT ck_bid_transfer_request_line_amount CHECK (amount <> 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE finance_cost_allocation_request (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    request_code VARCHAR(64) NOT NULL,
    project_id BIGINT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_id BIGINT NOT NULL,
    source_amount DECIMAL(18,2) NOT NULL,
    allocation_basis VARCHAR(32) NOT NULL,
    accounting_period CHAR(7) NOT NULL,
    cost_subject_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    approval_instance_id BIGINT NULL,
    final_batch_id BIGINT NULL,
    version INT NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    active_guard TINYINT GENERATED ALWAYS AS (
        CASE WHEN deleted_flag=0 AND status IN ('DRAFT','SUBMITTED') THEN 1 ELSE NULL END
    ) STORED,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_finance_allocation_request_code (tenant_id, request_code, deleted_flag),
    UNIQUE KEY uk_finance_allocation_request_idem (tenant_id, idempotency_key, deleted_flag),
    UNIQUE KEY uk_finance_allocation_request_active (tenant_id, source_type, source_id, active_guard),
    KEY idx_finance_allocation_request_project (tenant_id, project_id, status),
    KEY idx_finance_allocation_request_source (tenant_id, source_type, source_id, status),
    CONSTRAINT fk_finance_allocation_request_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON DELETE RESTRICT,
    CONSTRAINT fk_finance_allocation_request_subject FOREIGN KEY (cost_subject_id) REFERENCES cost_subject(id) ON DELETE RESTRICT,
    CONSTRAINT fk_finance_allocation_request_instance FOREIGN KEY (approval_instance_id) REFERENCES wf_instance(id) ON DELETE RESTRICT,
    CONSTRAINT fk_finance_allocation_request_final FOREIGN KEY (final_batch_id) REFERENCES finance_cost_allocation_batch(id) ON DELETE RESTRICT,
    CONSTRAINT ck_finance_allocation_request_status CHECK (status IN ('DRAFT','SUBMITTED','REJECTED','WITHDRAWN','POSTED')),
    CONSTRAINT ck_finance_allocation_request_amount CHECK (source_amount <> 0),
    CONSTRAINT ck_finance_allocation_request_source CHECK (source_type IN ('ACCOUNTING_ENTRY_LINE','EXPENSE_APPLICATION')),
    CONSTRAINT ck_finance_allocation_request_basis CHECK (allocation_basis IN ('DIRECT_PROJECT','BENEFIT_AMOUNT','OCCUPIED_DAYS','CONTRACT_AMOUNT_EXCEPTION'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE finance_cost_allocation_request_line (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    request_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    basis_value DECIMAL(18,4) NOT NULL,
    allocated_amount DECIMAL(18,2) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_finance_allocation_request_line (tenant_id, request_id, project_id),
    CONSTRAINT fk_finance_allocation_request_line_request FOREIGN KEY (request_id) REFERENCES finance_cost_allocation_request(id) ON DELETE CASCADE,
    CONSTRAINT fk_finance_allocation_request_line_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON DELETE RESTRICT,
    CONSTRAINT ck_finance_allocation_request_line_basis CHECK (basis_value >= 0),
    CONSTRAINT ck_finance_allocation_request_line_amount CHECK (allocated_amount <> 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Quality workflows and year/quarter period plans.
ALTER TABLE project_period_plan DROP CHECK ck_project_period_type;
ALTER TABLE project_period_plan
    ADD CONSTRAINT ck_project_period_type CHECK (period_type IN ('YEARLY','QUARTERLY','MONTHLY','WEEKLY'));

ALTER TABLE qs_rectification
    ADD COLUMN approval_instance_id BIGINT NULL AFTER status,
    ADD KEY idx_qs_rectification_approval (approval_instance_id),
    ADD CONSTRAINT fk_qs_rectification_approval FOREIGN KEY (approval_instance_id) REFERENCES wf_instance(id) ON DELETE RESTRICT;
ALTER TABLE qs_rectification DROP CHECK ck_qs_rectification_status;
ALTER TABLE qs_rectification
    ADD CONSTRAINT ck_qs_rectification_status CHECK (status IN ('DRAFT','SUBMITTED','PASSED','REJECTED','WITHDRAWN'));

ALTER TABLE qs_consequence
    ADD COLUMN approval_instance_id BIGINT NULL AFTER status,
    ADD KEY idx_qs_consequence_approval (approval_instance_id),
    ADD CONSTRAINT fk_qs_consequence_approval FOREIGN KEY (approval_instance_id) REFERENCES wf_instance(id) ON DELETE RESTRICT;
ALTER TABLE qs_consequence DROP CHECK ck_qs_consequence_status;
ALTER TABLE qs_consequence
    ADD CONSTRAINT ck_qs_consequence_status CHECK (status IN ('DRAFT','SUBMITTED','REJECTED','WITHDRAWN','POSTED'));

-- Tenant and fixed-role specifications.
CREATE TEMPORARY TABLE m89_tenant (tenant_id BIGINT PRIMARY KEY);
INSERT IGNORE INTO m89_tenant VALUES (0);
INSERT IGNORE INTO m89_tenant SELECT DISTINCT tenant_id FROM sys_user;
INSERT IGNORE INTO m89_tenant SELECT DISTINCT tenant_id FROM sys_role;
INSERT IGNORE INTO m89_tenant SELECT DISTINCT tenant_id FROM sys_menu;
INSERT IGNORE INTO m89_tenant SELECT DISTINCT tenant_id FROM wf_template;

CREATE TEMPORARY TABLE m89_role_spec (
    ordinal_no INT PRIMARY KEY,
    role_code VARCHAR(64) NOT NULL,
    role_name VARCHAR(100) NOT NULL,
    data_scope VARCHAR(50) NOT NULL,
    role_level INT NOT NULL
);

INSERT INTO sys_type_registry
    (id,type_domain,type_code,owner_module,contract_version,status,description,created_at,updated_at)
SELECT x.id,'WORKFLOW_BUSINESS_TYPE',x.type_code,x.owner_module,'1.0','ACTIVE','主线89工作流业务类型',NOW(),NOW()
FROM (
    SELECT 2930001 id,'BID_COST_TARGET_TRANSFER' type_code,'cost' owner_module UNION ALL
    SELECT 2930002,'FINANCE_COST_ALLOCATION','cost' UNION ALL
    SELECT 2930003,'QS_RECTIFICATION','quality' UNION ALL
    SELECT 2930004,'QS_CONSEQUENCE','quality'
) x
WHERE NOT EXISTS (
    SELECT 1 FROM sys_type_registry r
    WHERE r.type_domain='WORKFLOW_BUSINESS_TYPE' AND r.type_code=x.type_code
);
INSERT INTO m89_role_spec VALUES
    (1,'COMPANY_OWNER','公司老板','ALL',1),
    (2,'COMPANY_FINANCE','公司财务','ALL',0),
    (3,'PROJECT_MANAGER','项目经理','PROJECT_MEMBER',2),
    (4,'PROJECT_ACCOUNTANT','项目会计','PROJECT_MEMBER',2),
    (5,'TECHNICAL_LEAD','技术负责人','PROJECT_MEMBER',2),
    (6,'SAFETY_LEAD','安全负责人','PROJECT_MEMBER',2),
    (7,'CONSTRUCTION_LEAD','施工负责人','PROJECT_MEMBER',2),
    (8,'PROCUREMENT_LEAD','采购负责人','PROJECT_MEMBER',2),
    (9,'EMPLOYEE','员工','PROJECT_MEMBER',3),
    (10,'SUPER_ADMIN','隐藏超级管理员','ALL',0);

CREATE TEMPORARY TABLE m89_id_guard (value BIGINT CHECK (value < 9223372036854775000));
SET @m89_role_base = (SELECT GREATEST(COALESCE(MAX(id),0),293000000000000000) FROM sys_role);
INSERT INTO m89_id_guard VALUES (@m89_role_base + (SELECT COUNT(*) FROM m89_tenant) * 10 + 10);

INSERT INTO sys_role
    (id,tenant_id,role_code,role_name,role_type,status,data_scope,created_by,created_at,updated_by,updated_at,deleted_flag,remark,role_level)
SELECT @m89_role_base + ROW_NUMBER() OVER (ORDER BY t.tenant_id,s.ordinal_no),
       t.tenant_id,s.role_code,s.role_name,'SYSTEM','ENABLE',s.data_scope,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,0,'MAINLINE-89',s.role_level
FROM m89_tenant t CROSS JOIN m89_role_spec s
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role r
    WHERE r.tenant_id=t.tenant_id AND r.role_code=s.role_code AND r.deleted_flag=0
);

UPDATE sys_role r
JOIN m89_role_spec s ON s.role_code=r.role_code
SET r.role_name=s.role_name,r.role_type='SYSTEM',r.status='ENABLE',r.data_scope=s.data_scope,
    r.role_level=s.role_level,r.updated_at=CURRENT_TIMESTAMP,r.remark='MAINLINE-89'
WHERE r.deleted_flag=0;

UPDATE sys_role
SET status='DISABLE',updated_at=CURRENT_TIMESTAMP
WHERE deleted_flag=0
  AND role_code NOT IN ('COMPANY_OWNER','COMPANY_FINANCE','PROJECT_MANAGER','PROJECT_ACCOUNTANT',
                        'TECHNICAL_LEAD','SAFETY_LEAD','CONSTRUCTION_LEAD','PROCUREMENT_LEAD','EMPLOYEE','SUPER_ADMIN');

CREATE TEMPORARY TABLE m89_user_target (
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    PRIMARY KEY (tenant_id,user_id,role_code)
);

INSERT IGNORE INTO m89_user_target
SELECT ur.tenant_id,ur.user_id,
       CASE
           WHEN r.role_code IN ('SUPER_ADMIN','FINANCE','COMPANY_FINANCE') THEN 'COMPANY_FINANCE'
           WHEN r.role_code IN ('GENERAL_MANAGER','MANAGEMENT','MANAGEMENT_EXECUTIVE','COMPANY_OWNER') THEN 'COMPANY_OWNER'
WHEN r.role_code IN ('PM','PROJECT_MANAGER') THEN 'PROJECT_MANAGER'
WHEN r.role_code IN ('CM','CSTM','COST_MANAGER','COMMERCIAL_MANAGER','DEPARTMENT_MANAGER','PROJECT_ACCOUNTANT') THEN 'PROJECT_ACCOUNTANT'
           WHEN r.role_code IN ('CHIEF_ENGINEER','TECHNICAL_LEAD') THEN 'TECHNICAL_LEAD'
           WHEN r.role_code IN ('PRODUCTION_MANAGER','CONSTRUCTION_LEAD') THEN 'CONSTRUCTION_LEAD'
           WHEN r.role_code IN ('PURCHASE_MANAGER','MATERIAL_CLERK','PROCUREMENT_LEAD') THEN 'PROCUREMENT_LEAD'
           WHEN r.role_code IN ('COMMON_USER','EMPLOYEE') THEN 'EMPLOYEE'
           WHEN r.role_code='SAFETY_LEAD' THEN 'SAFETY_LEAD'
           ELSE 'EMPLOYEE'
       END
FROM sys_user_role ur
JOIN sys_user u ON u.tenant_id=ur.tenant_id AND u.id=ur.user_id AND u.deleted_flag=0 AND u.status='ENABLE'
JOIN sys_role r ON r.tenant_id=ur.tenant_id AND r.id=ur.role_id;

CREATE TEMPORARY TABLE m89_finance_user (
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (tenant_id,user_id)
);
INSERT INTO m89_finance_user
SELECT tenant_id,user_id FROM m89_user_target WHERE role_code='COMPANY_FINANCE';
INSERT IGNORE INTO m89_user_target
SELECT tenant_id,user_id,'SUPER_ADMIN' FROM m89_finance_user;
CREATE TEMPORARY TABLE m89_assigned_user (
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (tenant_id,user_id)
);
INSERT INTO m89_assigned_user
SELECT DISTINCT tenant_id,user_id FROM m89_user_target;
INSERT IGNORE INTO m89_user_target
SELECT u.tenant_id,u.id,'EMPLOYEE'
FROM sys_user u
WHERE u.deleted_flag=0 AND u.status='ENABLE'
  AND NOT EXISTS (SELECT 1 FROM m89_assigned_user x WHERE x.tenant_id=u.tenant_id AND x.user_id=u.id);

DELETE ur FROM sys_user_role ur
JOIN sys_user u ON u.tenant_id=ur.tenant_id AND u.id=ur.user_id
WHERE u.deleted_flag=0 AND u.status='ENABLE';

SET @m89_user_role_base = (SELECT GREATEST(COALESCE(MAX(id),0),293100000000000000) FROM sys_user_role);
INSERT INTO m89_id_guard VALUES (@m89_user_role_base + (SELECT COUNT(*) FROM m89_user_target) + 1);
INSERT INTO sys_user_role (id,tenant_id,user_id,role_id)
SELECT @m89_user_role_base + ROW_NUMBER() OVER (ORDER BY x.tenant_id,x.user_id,x.role_code),
       x.tenant_id,x.user_id,r.id
FROM m89_user_target x
JOIN sys_role r ON r.tenant_id=x.tenant_id AND r.role_code=x.role_code AND r.deleted_flag=0;

-- New explicit permission nodes, shared by the role package below.
CREATE TEMPORARY TABLE m89_menu_spec (
    ordinal_no INT PRIMARY KEY,
    menu_name VARCHAR(100) NOT NULL,
    perms VARCHAR(200) NOT NULL,
    parent_path VARCHAR(300) NULL
);
INSERT INTO m89_menu_spec VALUES
    (1,'报表目录查询','report:catalog:query','/dashboard/reports'),
    (2,'业务金额查看','business:amount:view',NULL),
    (3,'本人采购申请','purchase:request:self','/inventory/purchase-request'),
    (4,'本人材料领用','requisition:self','/inventory/material-requisition'),
    (5,'本人施工日报','site:daily:self','/site/daily-log'),
    (6,'本人日进度','schedule:daily-progress:self','project-schedule'),
    (7,'新增物资字典','material:dict:add','/material/dictionary'),
    (8,'编辑物资字典','material:dict:edit','/material/dictionary'),
    (9,'提交投标成本移交','cost:subject:transfer:submit','/cost/subject/trace'),
    (10,'提交财务成本分摊','cost:subject:allocation:submit','/cost/subject/trace'),
    (11,'提交质量安全整改','quality:rectification:submit','quality-safety'),
    (12,'提交质量安全金额后果','quality:consequence:submit','quality-safety'),
    (13,'维护工作流模板','workflow:template:manage','/system/workflow');

SET @m89_menu_base = (SELECT GREATEST(COALESCE(MAX(id),0),293200000000000000) FROM sys_menu);
INSERT INTO m89_id_guard VALUES (@m89_menu_base + (SELECT COUNT(*) FROM m89_tenant) * 13 + 13);
INSERT INTO sys_menu
    (id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible,
     created_by,updated_by,remark,created_at,updated_at,deleted_flag)
SELECT @m89_menu_base + ROW_NUMBER() OVER (ORDER BY t.tenant_id,s.ordinal_no),t.tenant_id,
       COALESCE((SELECT MIN(p.id) FROM sys_menu p
                 WHERE p.tenant_id=t.tenant_id AND p.deleted_flag=0 AND p.path=s.parent_path),0),
       s.menu_name,'BUTTON',NULL,NULL,s.perms,NULL,s.ordinal_no,'ENABLE',0,NULL,NULL,'MAINLINE-89',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM m89_tenant t CROSS JOIN m89_menu_spec s
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.tenant_id=t.tenant_id AND m.perms=s.perms AND m.deleted_flag=0);

UPDATE sys_menu m JOIN m89_menu_spec s ON s.perms=m.perms
SET m.menu_name=s.menu_name,m.status='ENABLE',m.visible=0,m.updated_at=CURRENT_TIMESTAMP,m.remark='MAINLINE-89'
WHERE m.deleted_flag=0;

DELETE rm FROM sys_role_menu rm
JOIN sys_role r ON r.tenant_id=rm.tenant_id AND r.id=rm.role_id
WHERE r.deleted_flag=0 AND r.role_code IN
 ('COMPANY_OWNER','COMPANY_FINANCE','PROJECT_MANAGER','PROJECT_ACCOUNTANT','TECHNICAL_LEAD',
  'SAFETY_LEAD','CONSTRUCTION_LEAD','PROCUREMENT_LEAD','EMPLOYEE');

CREATE TEMPORARY TABLE m89_excluded_menu (tenant_id BIGINT NOT NULL, menu_id BIGINT NOT NULL, PRIMARY KEY(tenant_id,menu_id));
INSERT IGNORE INTO m89_excluded_menu
WITH RECURSIVE menu_tree AS (
    SELECT tenant_id,id FROM sys_menu
    WHERE deleted_flag=0 AND path IN ('/system','/system-management','/dashboard/reports')
    UNION ALL
    SELECT child.tenant_id,child.id FROM sys_menu child
    JOIN menu_tree parent ON parent.tenant_id=child.tenant_id AND parent.id=child.parent_id
    WHERE child.deleted_flag=0
)
SELECT tenant_id,id FROM menu_tree;

CREATE TEMPORARY TABLE m89_grant (
    tenant_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    PRIMARY KEY(tenant_id,role_id,menu_id)
);

-- Company finance starts with every current permission/menu.
INSERT IGNORE INTO m89_grant
SELECT r.tenant_id,r.id,m.id
FROM sys_role r JOIN sys_menu m ON m.tenant_id=r.tenant_id AND m.deleted_flag=0 AND m.status='ENABLE'
WHERE r.role_code='COMPANY_FINANCE' AND r.deleted_flag=0;

-- Every visible role gets the current business navigation plus its menu-level read permission.
INSERT IGNORE INTO m89_grant
SELECT r.tenant_id,r.id,m.id
FROM sys_role r JOIN sys_menu m ON m.tenant_id=r.tenant_id AND m.deleted_flag=0 AND m.status='ENABLE'
LEFT JOIN m89_excluded_menu x ON x.tenant_id=m.tenant_id AND x.menu_id=m.id
WHERE r.role_code IN ('COMPANY_OWNER','COMPANY_FINANCE','PROJECT_MANAGER','PROJECT_ACCOUNTANT','TECHNICAL_LEAD',
                      'SAFETY_LEAD','CONSTRUCTION_LEAD','PROCUREMENT_LEAD','EMPLOYEE')
  AND r.deleted_flag=0 AND m.menu_type IN ('DIR','MENU') AND x.menu_id IS NULL;

CREATE TEMPORARY TABLE m89_read_perm (perms VARCHAR(200) PRIMARY KEY);
INSERT INTO m89_read_perm VALUES
 ('contract:query'),('cost:subject:mapping:query'),('dashboard:business-manager:view'),
 ('dashboard:chief-engineer:view'),('dashboard:cost-breakdown:view'),('dashboard:cost-manager:view'),
 ('dashboard:finance:view'),('dashboard:management:view'),('dashboard:production-manager:view'),
 ('dashboard:project-manager:view'),('dashboard:purchase-manager:view'),('file:query'),('invoice:query'),
 ('notification:view'),('org:query'),('partner:query'),('payment:record:query'),('payment:trace:query'),
 ('procurement:trace:query'),('project:commencement:query'),('project:file:query'),('project:member:list'),('project:query');

INSERT IGNORE INTO m89_grant
SELECT r.tenant_id,r.id,m.id
FROM sys_role r JOIN sys_menu m ON m.tenant_id=r.tenant_id
JOIN m89_read_perm p ON p.perms=m.perms
WHERE r.role_code IN ('COMPANY_OWNER','COMPANY_FINANCE','PROJECT_MANAGER','PROJECT_ACCOUNTANT','TECHNICAL_LEAD',
                      'SAFETY_LEAD','CONSTRUCTION_LEAD','PROCUREMENT_LEAD','EMPLOYEE')
  AND r.deleted_flag=0 AND m.deleted_flag=0 AND m.status='ENABLE';

CREATE TEMPORARY TABLE m89_write_perm (role_code VARCHAR(64), perms VARCHAR(200), PRIMARY KEY(role_code,perms));
INSERT INTO m89_write_perm VALUES
 ('COMPANY_OWNER','workflow:approve'),('COMPANY_OWNER','workflow:reject'),
 ('PROJECT_MANAGER','workflow:approve'),('PROJECT_MANAGER','workflow:reject'),
 ('PROJECT_MANAGER','measurement:maintain'),('PROJECT_MANAGER','measurement:submit'),
 ('PROJECT_MANAGER','budget:add'),('PROJECT_MANAGER','budget:edit'),('PROJECT_MANAGER','budget:delete'),('PROJECT_MANAGER','budget:submit'),
 ('PROJECT_MANAGER','settlement:add'),('PROJECT_MANAGER','settlement:edit'),('PROJECT_MANAGER','settlement:delete'),('PROJECT_MANAGER','settlement:submit'),
 ('PROJECT_MANAGER','schedule:maintain'),('PROJECT_MANAGER','schedule:submit'),('PROJECT_MANAGER','schedule:progress'),('PROJECT_MANAGER','schedule:correct'),
 ('PROJECT_MANAGER','revenue:settlement:submit'),
 ('PROJECT_ACCOUNTANT','workflow:approve'),('PROJECT_ACCOUNTANT','workflow:reject'),
 ('PROJECT_ACCOUNTANT','cost:subject:bid-transfer'),('PROJECT_ACCOUNTANT','cost:subject:transfer:submit'),
 ('PROJECT_ACCOUNTANT','cost:subject:finance-allocate'),('PROJECT_ACCOUNTANT','cost:subject:allocation:submit'),
 ('PROJECT_ACCOUNTANT','expense:add'),('PROJECT_ACCOUNTANT','expense:edit'),('PROJECT_ACCOUNTANT','expense:delete'),('PROJECT_ACCOUNTANT','expense:submit'),
 ('PROJECT_ACCOUNTANT','payment:app:add'),('PROJECT_ACCOUNTANT','payment:app:edit'),('PROJECT_ACCOUNTANT','payment:app:submit'),
 ('TECHNICAL_LEAD','workflow:approve'),('TECHNICAL_LEAD','workflow:reject'),
 ('TECHNICAL_LEAD','schedule:maintain'),('TECHNICAL_LEAD','schedule:submit'),('TECHNICAL_LEAD','schedule:progress'),
 ('TECHNICAL_LEAD','technical:scheme:maintain'),('TECHNICAL_LEAD','technical:scheme:submit'),
 ('TECHNICAL_LEAD','variation:order:add'),('TECHNICAL_LEAD','variation:order:edit'),('TECHNICAL_LEAD','variation:order:delete'),('TECHNICAL_LEAD','variation:order:submit'),
 ('SAFETY_LEAD','workflow:approve'),('SAFETY_LEAD','workflow:reject'),
 ('SAFETY_LEAD','quality:safety:plan:maintain'),('SAFETY_LEAD','quality:safety:inspection:maintain'),
 ('SAFETY_LEAD','quality:safety:rectify'),('SAFETY_LEAD','quality:safety:reinspect'),('SAFETY_LEAD','quality:safety:consequence'),
 ('SAFETY_LEAD','quality:rectification:submit'),('SAFETY_LEAD','quality:consequence:submit'),
 ('CONSTRUCTION_LEAD','workflow:approve'),('CONSTRUCTION_LEAD','workflow:reject'),
 ('CONSTRUCTION_LEAD','project:commencement:add'),('CONSTRUCTION_LEAD','project:commencement:edit'),('CONSTRUCTION_LEAD','project:commencement:submit'),
 ('CONSTRUCTION_LEAD','purchase:order:submit'),('CONSTRUCTION_LEAD','receipt:submit'),('CONSTRUCTION_LEAD','receipt:return'),
 ('CONSTRUCTION_LEAD','requisition:add'),('CONSTRUCTION_LEAD','requisition:edit'),('CONSTRUCTION_LEAD','requisition:submit'),
 ('CONSTRUCTION_LEAD','subcontract:measure:add'),('CONSTRUCTION_LEAD','subcontract:measure:edit'),('CONSTRUCTION_LEAD','subcontract:measure:submit'),
 ('PROCUREMENT_LEAD','workflow:approve'),('PROCUREMENT_LEAD','workflow:reject'),
 ('PROCUREMENT_LEAD','purchase:request:add'),('PROCUREMENT_LEAD','purchase:request:edit'),('PROCUREMENT_LEAD','purchase:request:delete'),('PROCUREMENT_LEAD','purchase:request:submit'),
 ('PROCUREMENT_LEAD','material:dict:add'),('PROCUREMENT_LEAD','material:dict:edit'),('PROCUREMENT_LEAD','material:dict:delete'),
 ('EMPLOYEE','purchase:request:self'),('EMPLOYEE','requisition:self'),('EMPLOYEE','site:daily:self'),('EMPLOYEE','schedule:daily-progress:self'),
 ('EMPLOYEE','quality:rectification:submit');

INSERT IGNORE INTO m89_grant
SELECT r.tenant_id,r.id,m.id
FROM m89_write_perm p JOIN sys_role r ON r.role_code=p.role_code AND r.deleted_flag=0
JOIN sys_menu m ON m.tenant_id=r.tenant_id AND m.perms=p.perms AND m.deleted_flag=0 AND m.status='ENABLE';

-- Initiator-only engine checks remain authoritative; every fixed role needs the route permission.
INSERT IGNORE INTO m89_grant
SELECT r.tenant_id,r.id,m.id
FROM sys_role r JOIN sys_menu m ON m.tenant_id=r.tenant_id
WHERE r.role_code IN ('COMPANY_OWNER','COMPANY_FINANCE','PROJECT_MANAGER','PROJECT_ACCOUNTANT','TECHNICAL_LEAD',
                      'SAFETY_LEAD','CONSTRUCTION_LEAD','PROCUREMENT_LEAD','EMPLOYEE')
  AND r.deleted_flag=0 AND m.deleted_flag=0 AND m.status='ENABLE'
  AND m.perms IN ('workflow:withdraw','workflow:resubmit');

-- Amount permission: eight professional/company roles, never pure EMPLOYEE.
INSERT IGNORE INTO m89_grant
SELECT r.tenant_id,r.id,m.id
FROM sys_role r JOIN sys_menu m ON m.tenant_id=r.tenant_id AND m.perms='business:amount:view' AND m.deleted_flag=0
WHERE r.role_code IN ('COMPANY_OWNER','COMPANY_FINANCE','PROJECT_MANAGER','PROJECT_ACCOUNTANT','TECHNICAL_LEAD',
                      'SAFETY_LEAD','CONSTRUCTION_LEAD','PROCUREMENT_LEAD') AND r.deleted_flag=0;

-- Evidence-capable roles receive the existing file upload permission.
INSERT IGNORE INTO m89_grant
SELECT r.tenant_id,r.id,m.id
FROM sys_role r JOIN sys_menu m ON m.tenant_id=r.tenant_id AND m.perms='file:upload' AND m.deleted_flag=0
WHERE r.role_code IN ('COMPANY_OWNER','COMPANY_FINANCE','PROJECT_MANAGER','PROJECT_ACCOUNTANT','TECHNICAL_LEAD',
                      'SAFETY_LEAD','CONSTRUCTION_LEAD','PROCUREMENT_LEAD','EMPLOYEE') AND r.deleted_flag=0;

SET @m89_role_menu_base = (SELECT GREATEST(COALESCE(MAX(id),0),293300000000000000) FROM sys_role_menu);
INSERT INTO m89_id_guard VALUES (@m89_role_menu_base + (SELECT COUNT(*) FROM m89_grant) + 1);
INSERT INTO sys_role_menu (id,tenant_id,role_id,menu_id)
SELECT @m89_role_menu_base + ROW_NUMBER() OVER (ORDER BY tenant_id,role_id,menu_id),tenant_id,role_id,menu_id
FROM m89_grant;

-- Canonical 21-flow matrix. Existing templates remain for history but are disabled.
CREATE TEMPORARY TABLE m89_matrix (
    business_type VARCHAR(50) NOT NULL,
    template_name VARCHAR(200) NOT NULL,
    node_order INT NOT NULL,
    node_name VARCHAR(200) NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    PRIMARY KEY(business_type,node_order)
);
INSERT INTO m89_matrix VALUES
 ('BID_COST_TARGET_TRANSFER','投标成本移交审批',1,'项目会计审批','PROJECT_ACCOUNTANT'),('BID_COST_TARGET_TRANSFER','投标成本移交审批',2,'公司财务审批','COMPANY_FINANCE'),
 ('CONTRACT_APPROVAL','合同审批',1,'项目经理审批','PROJECT_MANAGER'),('CONTRACT_APPROVAL','合同审批',2,'公司财务审批','COMPANY_FINANCE'),('CONTRACT_APPROVAL','合同审批',3,'公司老板审批','COMPANY_OWNER'),
 ('EXPENSE','费用报销审批',1,'项目会计审批','PROJECT_ACCOUNTANT'),('EXPENSE','费用报销审批',2,'公司财务审批','COMPANY_FINANCE'),
 ('FINANCE_COST_ALLOCATION','成本分摊审批',1,'项目会计审批','PROJECT_ACCOUNTANT'),('FINANCE_COST_ALLOCATION','成本分摊审批',2,'公司财务审批','COMPANY_FINANCE'),
 ('MATERIAL_RECEIPT','材料入库审批',1,'施工负责人审批','CONSTRUCTION_LEAD'),('MATERIAL_RECEIPT','材料入库审批',2,'采购负责人审批','PROCUREMENT_LEAD'),
 ('MATERIAL_REQUISITION','材料领用审批',1,'施工负责人审批','CONSTRUCTION_LEAD'),('MATERIAL_REQUISITION','材料领用审批',2,'采购负责人审批','PROCUREMENT_LEAD'),
 ('OWNER_SETTLEMENT','业主结算审批',1,'项目经理审批','PROJECT_MANAGER'),('OWNER_SETTLEMENT','业主结算审批',2,'公司财务审批','COMPANY_FINANCE'),('OWNER_SETTLEMENT','业主结算审批',3,'公司老板审批','COMPANY_OWNER'),
 ('PAY_REQUEST','付款申请审批',1,'项目会计审批','PROJECT_ACCOUNTANT'),('PAY_REQUEST','付款申请审批',2,'公司财务审批','COMPANY_FINANCE'),('PAY_REQUEST','付款申请审批',3,'公司老板审批','COMPANY_OWNER'),
 ('PRODUCTION_MEASUREMENT','产值计量审批',1,'项目经理审批','PROJECT_MANAGER'),('PRODUCTION_MEASUREMENT','产值计量审批',2,'公司财务审批','COMPANY_FINANCE'),
 ('PROJECT_BUDGET','项目预算审批',1,'项目经理审批','PROJECT_MANAGER'),('PROJECT_BUDGET','项目预算审批',2,'公司财务审批','COMPANY_FINANCE'),('PROJECT_BUDGET','项目预算审批',3,'公司老板审批','COMPANY_OWNER'),
 ('PROJECT_COMMENCEMENT','开工审批',1,'施工负责人审批','CONSTRUCTION_LEAD'),('PROJECT_COMMENCEMENT','开工审批',2,'项目经理审批','PROJECT_MANAGER'),
 ('PROJECT_SCHEDULE','总进度计划审批',1,'技术负责人审批','TECHNICAL_LEAD'),('PROJECT_SCHEDULE','总进度计划审批',2,'项目经理审批','PROJECT_MANAGER'),
 ('PROJECT_PERIOD_PLAN','周期进度计划审批',1,'技术负责人审批','TECHNICAL_LEAD'),('PROJECT_PERIOD_PLAN','周期进度计划审批',2,'项目经理审批','PROJECT_MANAGER'),
 ('PURCHASE_ORDER','采购订单审批',1,'施工负责人审批','CONSTRUCTION_LEAD'),('PURCHASE_ORDER','采购订单审批',2,'项目经理审批','PROJECT_MANAGER'),
 ('PURCHASE_REQUEST','采购申请审批',1,'采购负责人审批','PROCUREMENT_LEAD'),
 ('SETTLEMENT','分包结算审批',1,'项目经理审批','PROJECT_MANAGER'),('SETTLEMENT','分包结算审批',2,'公司财务审批','COMPANY_FINANCE'),
 ('SUB_MEASURE','分包计量审批',1,'施工负责人审批','CONSTRUCTION_LEAD'),('SUB_MEASURE','分包计量审批',2,'项目经理审批','PROJECT_MANAGER'),
 ('TECHNICAL_SCHEME','技术方案审批',1,'技术负责人审批','TECHNICAL_LEAD'),('TECHNICAL_SCHEME','技术方案审批',2,'项目经理审批','PROJECT_MANAGER'),
 ('VAR_ORDER','工程变更审批',1,'技术负责人审批','TECHNICAL_LEAD'),('VAR_ORDER','工程变更审批',2,'项目经理审批','PROJECT_MANAGER'),
 ('QS_RECTIFICATION','质量安全整改审批',1,'安全负责人审批','SAFETY_LEAD'),('QS_RECTIFICATION','质量安全整改审批',2,'项目经理审批','PROJECT_MANAGER'),
 ('QS_CONSEQUENCE','质量安全金额后果审批',1,'安全负责人审批','SAFETY_LEAD'),('QS_CONSEQUENCE','质量安全金额后果审批',2,'项目经理审批','PROJECT_MANAGER');

UPDATE wf_template t JOIN (SELECT DISTINCT business_type FROM m89_matrix) x ON x.business_type=t.business_type
SET t.enabled=0,t.updated_at=CURRENT_TIMESTAMP
WHERE t.deleted_flag=0 AND t.template_code<>CONCAT('M89-',t.business_type);

SET @m89_template_base = (SELECT GREATEST(COALESCE(MAX(id),0),293400000000000000) FROM wf_template);
INSERT INTO m89_id_guard VALUES (@m89_template_base + (SELECT COUNT(*) FROM m89_tenant) * 21 + 21);
INSERT INTO wf_template
    (id,tenant_id,template_code,template_name,business_type,enabled,amount_min,amount_max,condition_rule,form_schema,
     created_by,created_at,updated_by,updated_at,deleted_flag,remark)
SELECT @m89_template_base + ROW_NUMBER() OVER (ORDER BY t.tenant_id,x.business_type),t.tenant_id,
       CONCAT('M89-',x.business_type),x.template_name,x.business_type,1,NULL,NULL,
       CASE WHEN x.business_type IN ('CONTRACT_APPROVAL','OWNER_SETTLEMENT','PAY_REQUEST','PROJECT_BUDGET')
            THEN '{"preventInitiatorApproval":true,"maxApprovalsPerUser":1,"requireProjectMembership":true,"allowAdminFallback":false}'
            ELSE '{"preventInitiatorApproval":false,"maxApprovalsPerUser":1,"requireProjectMembership":true,"allowAdminFallback":false}' END,
       NULL,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,0,'MAINLINE-89'
FROM m89_tenant t JOIN (SELECT business_type,MIN(template_name) template_name FROM m89_matrix GROUP BY business_type) x
WHERE NOT EXISTS (SELECT 1 FROM wf_template w WHERE w.tenant_id=t.tenant_id AND w.template_code=CONCAT('M89-',x.business_type) AND w.deleted_flag=0);

UPDATE wf_template t JOIN (SELECT business_type,MIN(template_name) template_name FROM m89_matrix GROUP BY business_type) x
    ON t.business_type=x.business_type AND t.template_code=CONCAT('M89-',x.business_type)
SET t.template_name=x.template_name,t.enabled=1,t.amount_min=NULL,t.amount_max=NULL,
    t.condition_rule=CASE WHEN x.business_type IN ('CONTRACT_APPROVAL','OWNER_SETTLEMENT','PAY_REQUEST','PROJECT_BUDGET')
       THEN '{"preventInitiatorApproval":true,"maxApprovalsPerUser":1,"requireProjectMembership":true,"allowAdminFallback":false}'
       ELSE '{"preventInitiatorApproval":false,"maxApprovalsPerUser":1,"requireProjectMembership":true,"allowAdminFallback":false}' END,
    t.form_schema=NULL,t.updated_at=CURRENT_TIMESTAMP,t.remark='MAINLINE-89'
WHERE t.deleted_flag=0;

UPDATE wf_template_node n
JOIN wf_template t ON t.id=n.template_id AND t.template_code=CONCAT('M89-',t.business_type)
LEFT JOIN m89_matrix x ON x.business_type=t.business_type AND x.node_order=n.node_order
SET n.deleted_flag=1,n.updated_at=CURRENT_TIMESTAMP
WHERE n.deleted_flag=0 AND x.business_type IS NULL;

SET @m89_node_base = (SELECT GREATEST(COALESCE(MAX(id),0),293500000000000000) FROM wf_template_node);
INSERT INTO m89_id_guard VALUES (@m89_node_base + (SELECT COUNT(*) FROM m89_tenant) * 45 + 45);
INSERT INTO wf_template_node
    (id,tenant_id,template_id,node_code,node_name,node_order,node_type,approve_mode,approver_config,
     pass_rule_json,reject_rule_json,condition_rule,node_config,allow_transfer,allow_add_sign,timeout_hours,
     created_by,created_at,updated_by,updated_at,deleted_flag,remark)
SELECT @m89_node_base + ROW_NUMBER() OVER (ORDER BY t.tenant_id,x.business_type,x.node_order),t.tenant_id,t.id,
       CONCAT('M89_',LPAD(x.node_order,2,'0')),x.node_name,x.node_order,'APPROVAL','OR_SIGN',
       CONCAT('{"type":"ROLE","roleCode":"',x.role_code,'"}'),NULL,NULL,NULL,NULL,
       CASE WHEN x.business_type IN ('CONTRACT_APPROVAL','OWNER_SETTLEMENT','PAY_REQUEST','PROJECT_BUDGET') THEN 0 ELSE 1 END,
       CASE WHEN x.business_type IN ('CONTRACT_APPROVAL','OWNER_SETTLEMENT','PAY_REQUEST','PROJECT_BUDGET') THEN 0 ELSE 1 END,
       48,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,0,'MAINLINE-89'
FROM wf_template t JOIN m89_matrix x ON x.business_type=t.business_type
WHERE t.template_code=CONCAT('M89-',t.business_type) AND t.deleted_flag=0
ON DUPLICATE KEY UPDATE
    node_name=VALUES(node_name),node_order=VALUES(node_order),node_type=VALUES(node_type),approve_mode=VALUES(approve_mode),
    approver_config=VALUES(approver_config),pass_rule_json=NULL,reject_rule_json=NULL,condition_rule=NULL,node_config=NULL,
    allow_transfer=VALUES(allow_transfer),allow_add_sign=VALUES(allow_add_sign),timeout_hours=VALUES(timeout_hours),
    deleted_flag=0,updated_at=CURRENT_TIMESTAMP,remark='MAINLINE-89';

-- Enabled non-matrix templates may remain, but no enabled node may retain a legacy role code.
UPDATE wf_template_node n JOIN wf_template t ON t.id=n.template_id AND t.enabled=1 AND t.deleted_flag=0
SET n.approver_config = CASE JSON_UNQUOTE(JSON_EXTRACT(n.approver_config,'$.roleCode'))
    WHEN 'FINANCE' THEN '{"type":"ROLE","roleCode":"COMPANY_FINANCE"}'
    WHEN 'GENERAL_MANAGER' THEN '{"type":"ROLE","roleCode":"COMPANY_OWNER"}'
    WHEN 'MANAGEMENT' THEN '{"type":"ROLE","roleCode":"COMPANY_OWNER"}'
    WHEN 'MANAGEMENT_EXECUTIVE' THEN '{"type":"ROLE","roleCode":"COMPANY_OWNER"}'
    WHEN 'COST_MANAGER' THEN '{"type":"ROLE","roleCode":"PROJECT_ACCOUNTANT"}'
    WHEN 'COMMERCIAL_MANAGER' THEN '{"type":"ROLE","roleCode":"PROJECT_ACCOUNTANT"}'
    WHEN 'DEPARTMENT_MANAGER' THEN '{"type":"ROLE","roleCode":"PROJECT_ACCOUNTANT"}'
    WHEN 'CHIEF_ENGINEER' THEN '{"type":"ROLE","roleCode":"TECHNICAL_LEAD"}'
    WHEN 'PRODUCTION_MANAGER' THEN '{"type":"ROLE","roleCode":"CONSTRUCTION_LEAD"}'
    WHEN 'PURCHASE_MANAGER' THEN '{"type":"ROLE","roleCode":"PROCUREMENT_LEAD"}'
    WHEN 'MATERIAL_CLERK' THEN '{"type":"ROLE","roleCode":"PROCUREMENT_LEAD"}'
    WHEN 'COMMON_USER' THEN '{"type":"ROLE","roleCode":"EMPLOYEE"}'
    WHEN 'PM' THEN '{"type":"ROLE","roleCode":"PROJECT_MANAGER"}'
    WHEN 'CSTM' THEN '{"type":"ROLE","roleCode":"PROJECT_ACCOUNTANT"}'
    WHEN 'CM' THEN '{"type":"ROLE","roleCode":"PROJECT_ACCOUNTANT"}'
    ELSE n.approver_config END,
    n.updated_at=CURRENT_TIMESTAMP
WHERE n.deleted_flag=0
  AND JSON_UNQUOTE(JSON_EXTRACT(n.approver_config,'$.type'))='ROLE'
  AND JSON_EXTRACT(n.approver_config,'$.roleCode') IS NOT NULL;

-- Keep PROJECT_ROLE semantics while removing legacy codes from enabled templates.
UPDATE wf_template_node n JOIN wf_template t ON t.id=n.template_id AND t.enabled=1 AND t.deleted_flag=0
SET n.approver_config=JSON_SET(n.approver_config,'$.roleCode',
    CASE JSON_UNQUOTE(JSON_EXTRACT(n.approver_config,'$.roleCode'))
        WHEN 'PM' THEN 'PROJECT_MANAGER'
        WHEN 'CSTM' THEN 'PROJECT_ACCOUNTANT'
        WHEN 'CM' THEN 'PROJECT_ACCOUNTANT'
        ELSE JSON_UNQUOTE(JSON_EXTRACT(n.approver_config,'$.roleCode')) END),
    n.updated_at=CURRENT_TIMESTAMP
WHERE n.deleted_flag=0
  AND JSON_UNQUOTE(JSON_EXTRACT(n.approver_config,'$.type'))='PROJECT_ROLE'
  AND JSON_UNQUOTE(JSON_EXTRACT(n.approver_config,'$.roleCode')) IN ('PM','CSTM','CM');

DROP TEMPORARY TABLE m89_matrix;
DROP TEMPORARY TABLE m89_write_perm;
DROP TEMPORARY TABLE m89_read_perm;
DROP TEMPORARY TABLE m89_grant;
DROP TEMPORARY TABLE m89_excluded_menu;
DROP TEMPORARY TABLE m89_menu_spec;
DROP TEMPORARY TABLE m89_assigned_user;
DROP TEMPORARY TABLE m89_finance_user;
DROP TEMPORARY TABLE m89_user_target;
DROP TEMPORARY TABLE m89_id_guard;
DROP TEMPORARY TABLE m89_role_spec;
DROP TEMPORARY TABLE m89_tenant;
