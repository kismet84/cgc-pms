-- Mainline 89 H2 parity: fixed roles, amount permissions and immutable workflow snapshots.

ALTER TABLE wf_instance ADD COLUMN security_policy_json VARCHAR(1000);
ALTER TABLE wf_node_instance ADD COLUMN node_type VARCHAR(50);
ALTER TABLE wf_node_instance ADD COLUMN approver_config VARCHAR(1000);
ALTER TABLE wf_node_instance ADD COLUMN allow_transfer TINYINT;
ALTER TABLE wf_node_instance ADD COLUMN allow_add_sign TINYINT;
ALTER TABLE wf_node_instance ADD COLUMN timeout_hours INT;

UPDATE wf_instance
SET security_policy_json='{"preventInitiatorApproval":false,"maxApprovalsPerUser":100,"requireProjectMembership":false,"allowAdminFallback":true}'
WHERE security_policy_json IS NULL;

UPDATE wf_node_instance ni
SET node_type=COALESCE((SELECT tn.node_type FROM wf_template_node tn WHERE tn.id=ni.template_node_id),'APPROVAL'),
    approver_config=(SELECT tn.approver_config FROM wf_template_node tn WHERE tn.id=ni.template_node_id),
    allow_transfer=COALESCE((SELECT tn.allow_transfer FROM wf_template_node tn WHERE tn.id=ni.template_node_id),0),
    allow_add_sign=COALESCE((SELECT tn.allow_add_sign FROM wf_template_node tn WHERE tn.id=ni.template_node_id),0),
    timeout_hours=(SELECT tn.timeout_hours FROM wf_template_node tn WHERE tn.id=ni.template_node_id)
WHERE approver_config IS NULL;

ALTER TABLE sys_operation_audit_log ADD COLUMN before_snapshot VARCHAR(10000);
ALTER TABLE sys_operation_audit_log ADD COLUMN after_snapshot VARCHAR(10000);

CREATE TABLE bid_cost_target_transfer_request (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    request_code VARCHAR(64) NOT NULL,
    bid_cost_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    target_id BIGINT NOT NULL,
    mapping_version_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    total_amount DECIMAL(18,2) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    approval_instance_id BIGINT,
    final_transfer_id BIGINT,
    version INT NOT NULL DEFAULT 0,
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    active_guard TINYINT GENERATED ALWAYS AS (
        CASE WHEN deleted_flag=0 AND status IN ('DRAFT','SUBMITTED') THEN 1 ELSE NULL END
    ),
    remark VARCHAR(500),
    CONSTRAINT uk_bid_transfer_request_code UNIQUE (tenant_id,request_code,deleted_flag),
    CONSTRAINT uk_bid_transfer_request_idem UNIQUE (tenant_id,idempotency_key,deleted_flag),
    CONSTRAINT uk_bid_transfer_request_active UNIQUE (tenant_id,bid_cost_id,target_id,active_guard),
    CONSTRAINT ck_bid_transfer_request_status CHECK (status IN ('DRAFT','SUBMITTED','REJECTED','WITHDRAWN','POSTED')),
    CONSTRAINT ck_bid_transfer_request_amount CHECK (total_amount<>0)
);

CREATE TABLE bid_cost_target_transfer_request_line (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    request_id BIGINT NOT NULL,
    source_cost_item_id BIGINT NOT NULL,
    source_subject_id BIGINT NOT NULL,
    target_subject_id BIGINT NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    CONSTRAINT uk_bid_transfer_request_line UNIQUE (tenant_id,request_id,source_cost_item_id,target_subject_id),
    CONSTRAINT fk_bid_transfer_request_line_request FOREIGN KEY (request_id) REFERENCES bid_cost_target_transfer_request(id) ON DELETE CASCADE,
    CONSTRAINT ck_bid_transfer_request_line_amount CHECK (amount<>0)
);
CREATE INDEX idx_bid_transfer_request_source
    ON bid_cost_target_transfer_request(tenant_id,bid_cost_id,target_id,status);

CREATE TABLE finance_cost_allocation_request (
    id BIGINT NOT NULL PRIMARY KEY,
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
    approval_instance_id BIGINT,
    final_batch_id BIGINT,
    version INT NOT NULL DEFAULT 0,
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    active_guard TINYINT GENERATED ALWAYS AS (
        CASE WHEN deleted_flag=0 AND status IN ('DRAFT','SUBMITTED') THEN 1 ELSE NULL END
    ),
    remark VARCHAR(500),
    CONSTRAINT uk_finance_allocation_request_code UNIQUE (tenant_id,request_code,deleted_flag),
    CONSTRAINT uk_finance_allocation_request_idem UNIQUE (tenant_id,idempotency_key,deleted_flag),
    CONSTRAINT uk_finance_allocation_request_active UNIQUE (tenant_id,source_type,source_id,active_guard),
    CONSTRAINT ck_finance_allocation_request_status CHECK (status IN ('DRAFT','SUBMITTED','REJECTED','WITHDRAWN','POSTED')),
    CONSTRAINT ck_finance_allocation_request_amount CHECK (source_amount<>0),
    CONSTRAINT ck_finance_allocation_request_source CHECK (source_type IN ('ACCOUNTING_ENTRY_LINE','EXPENSE_APPLICATION')),
    CONSTRAINT ck_finance_allocation_request_basis CHECK (allocation_basis IN ('DIRECT_PROJECT','BENEFIT_AMOUNT','OCCUPIED_DAYS','CONTRACT_AMOUNT_EXCEPTION'))
);

CREATE TABLE finance_cost_allocation_request_line (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    request_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    basis_value DECIMAL(18,4) NOT NULL,
    allocated_amount DECIMAL(18,2) NOT NULL,
    CONSTRAINT uk_finance_allocation_request_line UNIQUE (tenant_id,request_id,project_id),
    CONSTRAINT fk_finance_allocation_request_line_request FOREIGN KEY (request_id) REFERENCES finance_cost_allocation_request(id) ON DELETE CASCADE,
    CONSTRAINT ck_finance_allocation_request_line_basis CHECK (basis_value>=0),
    CONSTRAINT ck_finance_allocation_request_line_amount CHECK (allocated_amount<>0)
);
CREATE INDEX idx_finance_allocation_request_source
    ON finance_cost_allocation_request(tenant_id,source_type,source_id,status);

INSERT INTO sys_type_registry
    (id,type_domain,type_code,owner_module,contract_version,status,description,created_at,updated_at)
SELECT x.id,'WORKFLOW_BUSINESS_TYPE',x.type_code,x.owner_module,'1.0','ACTIVE','主线89工作流业务类型',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
FROM (VALUES
    (2930001,'BID_COST_TARGET_TRANSFER','cost'),
    (2930002,'FINANCE_COST_ALLOCATION','cost'),
    (2930003,'QS_RECTIFICATION','quality'),
    (2930004,'QS_CONSEQUENCE','quality')
) x(id,type_code,owner_module)
WHERE NOT EXISTS (
    SELECT 1 FROM sys_type_registry r
    WHERE r.type_domain='WORKFLOW_BUSINESS_TYPE' AND r.type_code=x.type_code
);

ALTER TABLE project_period_plan DROP CONSTRAINT IF EXISTS ck_project_period_type;
ALTER TABLE project_period_plan ADD CONSTRAINT ck_project_period_type
    CHECK (period_type IN ('YEARLY','QUARTERLY','MONTHLY','WEEKLY'));

ALTER TABLE qs_rectification ADD COLUMN approval_instance_id BIGINT;
ALTER TABLE qs_rectification DROP CONSTRAINT IF EXISTS ck_qs_rectification_status;
ALTER TABLE qs_rectification ADD CONSTRAINT ck_qs_rectification_status
    CHECK (status IN ('DRAFT','SUBMITTED','PASSED','REJECTED','WITHDRAWN'));

ALTER TABLE qs_consequence ADD COLUMN approval_instance_id BIGINT;
ALTER TABLE qs_consequence DROP CONSTRAINT IF EXISTS ck_qs_consequence_status;
ALTER TABLE qs_consequence ADD CONSTRAINT ck_qs_consequence_status
    CHECK (status IN ('DRAFT','SUBMITTED','REJECTED','WITHDRAWN','POSTED'));

CREATE LOCAL TEMPORARY TABLE m89_tenant (tenant_id BIGINT PRIMARY KEY);
MERGE INTO m89_tenant KEY(tenant_id) VALUES (0);
MERGE INTO m89_tenant KEY(tenant_id) SELECT DISTINCT tenant_id FROM sys_user;
MERGE INTO m89_tenant KEY(tenant_id) SELECT DISTINCT tenant_id FROM sys_role;
MERGE INTO m89_tenant KEY(tenant_id) SELECT DISTINCT tenant_id FROM sys_menu;
MERGE INTO m89_tenant KEY(tenant_id) SELECT DISTINCT tenant_id FROM wf_template;

CREATE LOCAL TEMPORARY TABLE m89_role_spec (
    ordinal_no INT PRIMARY KEY,
    role_code VARCHAR(64), role_name VARCHAR(100), data_scope VARCHAR(50), role_level INT
);
INSERT INTO m89_role_spec VALUES
 (1,'COMPANY_OWNER','公司老板','ALL',1),(2,'COMPANY_FINANCE','公司财务','ALL',0),
 (3,'PROJECT_MANAGER','项目经理','PROJECT_MEMBER',2),(4,'PROJECT_ACCOUNTANT','项目会计','PROJECT_MEMBER',2),
 (5,'TECHNICAL_LEAD','技术负责人','PROJECT_MEMBER',2),(6,'SAFETY_LEAD','安全负责人','PROJECT_MEMBER',2),
 (7,'CONSTRUCTION_LEAD','施工负责人','PROJECT_MEMBER',2),(8,'PROCUREMENT_LEAD','采购负责人','PROJECT_MEMBER',2),
 (9,'EMPLOYEE','员工','PROJECT_MEMBER',3),(10,'SUPER_ADMIN','隐藏超级管理员','ALL',0);

INSERT INTO sys_role
 (id,tenant_id,role_code,role_name,role_type,status,data_scope,created_by,created_at,updated_by,updated_at,deleted_flag,remark,role_level)
SELECT 293000000000000000 + ROW_NUMBER() OVER (ORDER BY t.tenant_id,s.ordinal_no),
       t.tenant_id,s.role_code,s.role_name,'SYSTEM','ENABLE',s.data_scope,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,0,'MAINLINE-89',s.role_level
FROM m89_tenant t CROSS JOIN m89_role_spec s
WHERE NOT EXISTS (SELECT 1 FROM sys_role r WHERE r.tenant_id=t.tenant_id AND r.role_code=s.role_code AND r.deleted_flag=0);

UPDATE sys_role r
SET role_name=(SELECT s.role_name FROM m89_role_spec s WHERE s.role_code=r.role_code),
    role_type='SYSTEM',status='ENABLE',
    data_scope=(SELECT s.data_scope FROM m89_role_spec s WHERE s.role_code=r.role_code),
    role_level=(SELECT s.role_level FROM m89_role_spec s WHERE s.role_code=r.role_code),
    updated_at=CURRENT_TIMESTAMP,remark='MAINLINE-89'
WHERE r.deleted_flag=0 AND r.role_code IN (SELECT role_code FROM m89_role_spec);

UPDATE sys_role SET status='DISABLE',updated_at=CURRENT_TIMESTAMP
WHERE deleted_flag=0 AND role_code NOT IN (SELECT role_code FROM m89_role_spec);

CREATE LOCAL TEMPORARY TABLE m89_user_target (
    tenant_id BIGINT NOT NULL,user_id BIGINT NOT NULL,role_code VARCHAR(64) NOT NULL,
    PRIMARY KEY(tenant_id,user_id,role_code)
);
INSERT INTO m89_user_target
SELECT DISTINCT ur.tenant_id,ur.user_id,
 CASE WHEN r.role_code IN ('SUPER_ADMIN','FINANCE','COMPANY_FINANCE') THEN 'COMPANY_FINANCE'
      WHEN r.role_code IN ('GENERAL_MANAGER','MANAGEMENT','MANAGEMENT_EXECUTIVE','COMPANY_OWNER') THEN 'COMPANY_OWNER'
WHEN r.role_code IN ('PM','PROJECT_MANAGER') THEN 'PROJECT_MANAGER'
WHEN r.role_code IN ('CM','CSTM','COST_MANAGER','COMMERCIAL_MANAGER','DEPARTMENT_MANAGER','PROJECT_ACCOUNTANT') THEN 'PROJECT_ACCOUNTANT'
      WHEN r.role_code IN ('CHIEF_ENGINEER','TECHNICAL_LEAD') THEN 'TECHNICAL_LEAD'
      WHEN r.role_code IN ('PRODUCTION_MANAGER','CONSTRUCTION_LEAD') THEN 'CONSTRUCTION_LEAD'
      WHEN r.role_code IN ('PURCHASE_MANAGER','MATERIAL_CLERK','PROCUREMENT_LEAD') THEN 'PROCUREMENT_LEAD'
      WHEN r.role_code='SAFETY_LEAD' THEN 'SAFETY_LEAD' ELSE 'EMPLOYEE' END
FROM sys_user_role ur
JOIN sys_user u ON u.tenant_id=ur.tenant_id AND u.id=ur.user_id AND u.deleted_flag=0 AND u.status='ENABLE'
JOIN sys_role r ON r.tenant_id=ur.tenant_id AND r.id=ur.role_id;

CREATE LOCAL TEMPORARY TABLE m89_finance_user AS
SELECT tenant_id,user_id FROM m89_user_target WHERE role_code='COMPANY_FINANCE';
INSERT INTO m89_user_target
SELECT tenant_id,user_id,'SUPER_ADMIN' FROM m89_finance_user;
CREATE LOCAL TEMPORARY TABLE m89_assigned_user AS SELECT DISTINCT tenant_id,user_id FROM m89_user_target;
INSERT INTO m89_user_target
SELECT u.tenant_id,u.id,'EMPLOYEE' FROM sys_user u
WHERE u.deleted_flag=0 AND u.status='ENABLE'
  AND NOT EXISTS (SELECT 1 FROM m89_assigned_user x WHERE x.tenant_id=u.tenant_id AND x.user_id=u.id);

DELETE FROM sys_user_role ur WHERE EXISTS (
 SELECT 1 FROM sys_user u WHERE u.tenant_id=ur.tenant_id AND u.id=ur.user_id AND u.deleted_flag=0 AND u.status='ENABLE'
);
INSERT INTO sys_user_role(id,tenant_id,user_id,role_id)
SELECT 293100000000000000 + ROW_NUMBER() OVER (ORDER BY x.tenant_id,x.user_id,x.role_code),
       x.tenant_id,x.user_id,r.id
FROM m89_user_target x JOIN sys_role r ON r.tenant_id=x.tenant_id AND r.role_code=x.role_code AND r.deleted_flag=0;

CREATE LOCAL TEMPORARY TABLE m89_menu_spec (
    ordinal_no INT PRIMARY KEY,menu_name VARCHAR(100),perms VARCHAR(200),parent_path VARCHAR(300)
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

INSERT INTO sys_menu
 (id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible,
  created_by,updated_by,remark,created_at,updated_at,deleted_flag)
SELECT 293200000000000000 + ROW_NUMBER() OVER (ORDER BY t.tenant_id,s.ordinal_no),t.tenant_id,
 COALESCE((SELECT MIN(p.id) FROM sys_menu p WHERE p.tenant_id=t.tenant_id AND p.deleted_flag=0 AND p.path=s.parent_path),0),
 s.menu_name,'BUTTON',NULL,NULL,s.perms,NULL,s.ordinal_no,'ENABLE',0,NULL,NULL,'MAINLINE-89',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM m89_tenant t CROSS JOIN m89_menu_spec s
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.tenant_id=t.tenant_id AND m.perms=s.perms AND m.deleted_flag=0);

UPDATE sys_menu m SET
 menu_name=(SELECT s.menu_name FROM m89_menu_spec s WHERE s.perms=m.perms),
 status='ENABLE',visible=0,updated_at=CURRENT_TIMESTAMP,remark='MAINLINE-89'
WHERE m.deleted_flag=0 AND m.perms IN (SELECT perms FROM m89_menu_spec);

DELETE FROM sys_role_menu rm WHERE EXISTS (
 SELECT 1 FROM sys_role r WHERE r.tenant_id=rm.tenant_id AND r.id=rm.role_id AND r.deleted_flag=0
 AND r.role_code IN ('COMPANY_OWNER','COMPANY_FINANCE','PROJECT_MANAGER','PROJECT_ACCOUNTANT','TECHNICAL_LEAD',
                     'SAFETY_LEAD','CONSTRUCTION_LEAD','PROCUREMENT_LEAD','EMPLOYEE')
);

CREATE LOCAL TEMPORARY TABLE m89_grant (
 tenant_id BIGINT NOT NULL,role_id BIGINT NOT NULL,menu_id BIGINT NOT NULL,
 PRIMARY KEY(tenant_id,role_id,menu_id)
);
MERGE INTO m89_grant KEY(tenant_id,role_id,menu_id)
SELECT r.tenant_id,r.id,m.id FROM sys_role r JOIN sys_menu m ON m.tenant_id=r.tenant_id
WHERE r.role_code='COMPANY_FINANCE' AND r.deleted_flag=0 AND m.deleted_flag=0 AND m.status='ENABLE';

MERGE INTO m89_grant KEY(tenant_id,role_id,menu_id)
SELECT r.tenant_id,r.id,m.id FROM sys_role r JOIN sys_menu m ON m.tenant_id=r.tenant_id
WHERE r.role_code IN ('COMPANY_OWNER','COMPANY_FINANCE','PROJECT_MANAGER','PROJECT_ACCOUNTANT','TECHNICAL_LEAD',
                      'SAFETY_LEAD','CONSTRUCTION_LEAD','PROCUREMENT_LEAD','EMPLOYEE')
 AND r.deleted_flag=0 AND m.deleted_flag=0 AND m.status='ENABLE' AND m.menu_type IN ('DIR','MENU')
 AND COALESCE(m.path,'') NOT LIKE '/system%' AND COALESCE(m.path,'') NOT LIKE '/dashboard/reports%';

MERGE INTO m89_grant KEY(tenant_id,role_id,menu_id)
SELECT r.tenant_id,r.id,m.id FROM sys_role r JOIN sys_menu m ON m.tenant_id=r.tenant_id
WHERE r.role_code IN ('COMPANY_OWNER','COMPANY_FINANCE','PROJECT_MANAGER','PROJECT_ACCOUNTANT','TECHNICAL_LEAD',
                      'SAFETY_LEAD','CONSTRUCTION_LEAD','PROCUREMENT_LEAD','EMPLOYEE')
 AND r.deleted_flag=0 AND m.deleted_flag=0 AND m.status='ENABLE'
 AND m.perms IN ('contract:query','cost:subject:mapping:query','dashboard:business-manager:view','dashboard:chief-engineer:view',
 'dashboard:cost-breakdown:view','dashboard:cost-manager:view','dashboard:finance:view','dashboard:management:view',
 'dashboard:production-manager:view','dashboard:project-manager:view','dashboard:purchase-manager:view','file:query',
 'invoice:query','notification:view','org:query','partner:query','payment:record:query','payment:trace:query',
 'procurement:trace:query','project:commencement:query','project:file:query','project:member:list','project:query');

CREATE LOCAL TEMPORARY TABLE m89_write_perm (
 role_code VARCHAR(64),perms VARCHAR(200),PRIMARY KEY(role_code,perms)
);
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
 ('TECHNICAL_LEAD','workflow:approve'),('TECHNICAL_LEAD','workflow:reject'),('TECHNICAL_LEAD','schedule:maintain'),
 ('TECHNICAL_LEAD','schedule:submit'),('TECHNICAL_LEAD','schedule:progress'),
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

MERGE INTO m89_grant KEY(tenant_id,role_id,menu_id)
SELECT r.tenant_id,r.id,m.id FROM m89_write_perm p
JOIN sys_role r ON r.role_code=p.role_code AND r.deleted_flag=0
JOIN sys_menu m ON m.tenant_id=r.tenant_id AND m.perms=p.perms AND m.deleted_flag=0 AND m.status='ENABLE';

MERGE INTO m89_grant KEY(tenant_id,role_id,menu_id)
SELECT r.tenant_id,r.id,m.id FROM sys_role r JOIN sys_menu m ON m.tenant_id=r.tenant_id
WHERE r.role_code IN ('COMPANY_OWNER','COMPANY_FINANCE','PROJECT_MANAGER','PROJECT_ACCOUNTANT','TECHNICAL_LEAD',
                      'SAFETY_LEAD','CONSTRUCTION_LEAD','PROCUREMENT_LEAD','EMPLOYEE')
 AND r.deleted_flag=0 AND m.deleted_flag=0 AND m.status='ENABLE'
 AND m.perms IN ('workflow:withdraw','workflow:resubmit');

MERGE INTO m89_grant KEY(tenant_id,role_id,menu_id)
SELECT r.tenant_id,r.id,m.id FROM sys_role r JOIN sys_menu m ON m.tenant_id=r.tenant_id
WHERE r.role_code IN ('COMPANY_OWNER','COMPANY_FINANCE','PROJECT_MANAGER','PROJECT_ACCOUNTANT','TECHNICAL_LEAD',
                      'SAFETY_LEAD','CONSTRUCTION_LEAD','PROCUREMENT_LEAD')
 AND r.deleted_flag=0 AND m.deleted_flag=0 AND m.perms='business:amount:view';

MERGE INTO m89_grant KEY(tenant_id,role_id,menu_id)
SELECT r.tenant_id,r.id,m.id FROM sys_role r JOIN sys_menu m ON m.tenant_id=r.tenant_id
WHERE r.role_code IN ('COMPANY_OWNER','COMPANY_FINANCE','PROJECT_MANAGER','PROJECT_ACCOUNTANT','TECHNICAL_LEAD',
                      'SAFETY_LEAD','CONSTRUCTION_LEAD','PROCUREMENT_LEAD','EMPLOYEE')
 AND r.deleted_flag=0 AND m.deleted_flag=0 AND m.perms='file:upload';

INSERT INTO sys_role_menu(id,tenant_id,role_id,menu_id)
SELECT 293300000000000000 + ROW_NUMBER() OVER (ORDER BY tenant_id,role_id,menu_id),tenant_id,role_id,menu_id FROM m89_grant;

CREATE LOCAL TEMPORARY TABLE m89_matrix (
 business_type VARCHAR(50) NOT NULL,template_name VARCHAR(200) NOT NULL,node_order INT NOT NULL,
 node_name VARCHAR(200) NOT NULL,role_code VARCHAR(64) NOT NULL,PRIMARY KEY(business_type,node_order)
);
INSERT INTO m89_matrix VALUES
 ('BID_COST_TARGET_TRANSFER','投标成本移交审批',1,'项目会计审批','PROJECT_ACCOUNTANT'),
 ('BID_COST_TARGET_TRANSFER','投标成本移交审批',2,'公司财务审批','COMPANY_FINANCE'),
 ('CONTRACT_APPROVAL','合同审批',1,'项目经理审批','PROJECT_MANAGER'),
 ('CONTRACT_APPROVAL','合同审批',2,'公司财务审批','COMPANY_FINANCE'),
 ('CONTRACT_APPROVAL','合同审批',3,'公司老板审批','COMPANY_OWNER'),
 ('EXPENSE','费用报销审批',1,'项目会计审批','PROJECT_ACCOUNTANT'),
 ('EXPENSE','费用报销审批',2,'公司财务审批','COMPANY_FINANCE'),
 ('FINANCE_COST_ALLOCATION','成本分摊审批',1,'项目会计审批','PROJECT_ACCOUNTANT'),
 ('FINANCE_COST_ALLOCATION','成本分摊审批',2,'公司财务审批','COMPANY_FINANCE'),
 ('MATERIAL_RECEIPT','材料入库审批',1,'施工负责人审批','CONSTRUCTION_LEAD'),
 ('MATERIAL_RECEIPT','材料入库审批',2,'采购负责人审批','PROCUREMENT_LEAD'),
 ('MATERIAL_REQUISITION','材料领用审批',1,'施工负责人审批','CONSTRUCTION_LEAD'),
 ('MATERIAL_REQUISITION','材料领用审批',2,'采购负责人审批','PROCUREMENT_LEAD'),
 ('OWNER_SETTLEMENT','业主结算审批',1,'项目经理审批','PROJECT_MANAGER'),
 ('OWNER_SETTLEMENT','业主结算审批',2,'公司财务审批','COMPANY_FINANCE'),
 ('OWNER_SETTLEMENT','业主结算审批',3,'公司老板审批','COMPANY_OWNER'),
 ('PAY_REQUEST','付款申请审批',1,'项目会计审批','PROJECT_ACCOUNTANT'),
 ('PAY_REQUEST','付款申请审批',2,'公司财务审批','COMPANY_FINANCE'),
 ('PAY_REQUEST','付款申请审批',3,'公司老板审批','COMPANY_OWNER'),
 ('PRODUCTION_MEASUREMENT','产值计量审批',1,'项目经理审批','PROJECT_MANAGER'),
 ('PRODUCTION_MEASUREMENT','产值计量审批',2,'公司财务审批','COMPANY_FINANCE'),
 ('PROJECT_BUDGET','项目预算审批',1,'项目经理审批','PROJECT_MANAGER'),
 ('PROJECT_BUDGET','项目预算审批',2,'公司财务审批','COMPANY_FINANCE'),
 ('PROJECT_BUDGET','项目预算审批',3,'公司老板审批','COMPANY_OWNER'),
 ('PROJECT_COMMENCEMENT','开工审批',1,'施工负责人审批','CONSTRUCTION_LEAD'),
 ('PROJECT_COMMENCEMENT','开工审批',2,'项目经理审批','PROJECT_MANAGER'),
 ('PROJECT_SCHEDULE','总进度计划审批',1,'技术负责人审批','TECHNICAL_LEAD'),
 ('PROJECT_SCHEDULE','总进度计划审批',2,'项目经理审批','PROJECT_MANAGER'),
 ('PROJECT_PERIOD_PLAN','周期进度计划审批',1,'技术负责人审批','TECHNICAL_LEAD'),
 ('PROJECT_PERIOD_PLAN','周期进度计划审批',2,'项目经理审批','PROJECT_MANAGER'),
 ('PURCHASE_ORDER','采购订单审批',1,'施工负责人审批','CONSTRUCTION_LEAD'),
 ('PURCHASE_ORDER','采购订单审批',2,'项目经理审批','PROJECT_MANAGER'),
 ('PURCHASE_REQUEST','采购申请审批',1,'采购负责人审批','PROCUREMENT_LEAD'),
 ('SETTLEMENT','分包结算审批',1,'项目经理审批','PROJECT_MANAGER'),
 ('SETTLEMENT','分包结算审批',2,'公司财务审批','COMPANY_FINANCE'),
 ('SUB_MEASURE','分包计量审批',1,'施工负责人审批','CONSTRUCTION_LEAD'),
 ('SUB_MEASURE','分包计量审批',2,'项目经理审批','PROJECT_MANAGER'),
 ('TECHNICAL_SCHEME','技术方案审批',1,'技术负责人审批','TECHNICAL_LEAD'),
 ('TECHNICAL_SCHEME','技术方案审批',2,'项目经理审批','PROJECT_MANAGER'),
 ('VAR_ORDER','工程变更审批',1,'技术负责人审批','TECHNICAL_LEAD'),
 ('VAR_ORDER','工程变更审批',2,'项目经理审批','PROJECT_MANAGER'),
 ('QS_RECTIFICATION','质量安全整改审批',1,'安全负责人审批','SAFETY_LEAD'),
 ('QS_RECTIFICATION','质量安全整改审批',2,'项目经理审批','PROJECT_MANAGER'),
 ('QS_CONSEQUENCE','质量安全金额后果审批',1,'安全负责人审批','SAFETY_LEAD'),
 ('QS_CONSEQUENCE','质量安全金额后果审批',2,'项目经理审批','PROJECT_MANAGER');

UPDATE wf_template SET enabled=0,updated_at=CURRENT_TIMESTAMP
WHERE deleted_flag=0 AND business_type IN (SELECT DISTINCT business_type FROM m89_matrix)
 AND template_code<>CONCAT('M89-',business_type);

INSERT INTO wf_template
 (id,tenant_id,template_code,template_name,business_type,enabled,amount_min,amount_max,condition_rule,form_schema,
  created_by,created_at,updated_by,updated_at,deleted_flag,remark)
SELECT 293400000000000000 + ROW_NUMBER() OVER (ORDER BY t.tenant_id,x.business_type),t.tenant_id,
 CONCAT('M89-',x.business_type),x.template_name,x.business_type,1,NULL,NULL,
 CASE WHEN x.business_type IN ('CONTRACT_APPROVAL','OWNER_SETTLEMENT','PAY_REQUEST','PROJECT_BUDGET')
      THEN JSON '{"preventInitiatorApproval":true,"maxApprovalsPerUser":1,"requireProjectMembership":true,"allowAdminFallback":false}'
      ELSE JSON '{"preventInitiatorApproval":false,"maxApprovalsPerUser":1,"requireProjectMembership":true,"allowAdminFallback":false}' END,
 NULL,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,0,'MAINLINE-89'
FROM m89_tenant t
JOIN (SELECT business_type,MIN(template_name) template_name FROM m89_matrix GROUP BY business_type) x ON 1=1
WHERE NOT EXISTS (SELECT 1 FROM wf_template w WHERE w.tenant_id=t.tenant_id
                  AND w.template_code=CONCAT('M89-',x.business_type) AND w.deleted_flag=0);

UPDATE wf_template t SET
 template_name=(SELECT MIN(x.template_name) FROM m89_matrix x WHERE x.business_type=t.business_type),
 enabled=1,amount_min=NULL,amount_max=NULL,
 condition_rule=CASE WHEN t.business_type IN ('CONTRACT_APPROVAL','OWNER_SETTLEMENT','PAY_REQUEST','PROJECT_BUDGET')
      THEN JSON '{"preventInitiatorApproval":true,"maxApprovalsPerUser":1,"requireProjectMembership":true,"allowAdminFallback":false}'
      ELSE JSON '{"preventInitiatorApproval":false,"maxApprovalsPerUser":1,"requireProjectMembership":true,"allowAdminFallback":false}' END,
 form_schema=NULL,updated_at=CURRENT_TIMESTAMP,remark='MAINLINE-89'
WHERE t.deleted_flag=0 AND t.template_code=CONCAT('M89-',t.business_type)
 AND t.business_type IN (SELECT DISTINCT business_type FROM m89_matrix);

DELETE FROM wf_template_node n WHERE EXISTS (
 SELECT 1 FROM wf_template t WHERE t.id=n.template_id AND t.deleted_flag=0
 AND t.template_code=CONCAT('M89-',t.business_type)
);

INSERT INTO wf_template_node
 (id,tenant_id,template_id,node_code,node_name,node_order,node_type,approve_mode,approver_config,
  pass_rule_json,reject_rule_json,condition_rule,node_config,allow_transfer,allow_add_sign,timeout_hours,
  created_by,created_at,updated_by,updated_at,deleted_flag,remark)
SELECT 293500000000000000 + ROW_NUMBER() OVER (ORDER BY t.tenant_id,x.business_type,x.node_order),t.tenant_id,t.id,
 CONCAT('M89_',LPAD(CAST(x.node_order AS VARCHAR),2,'0')),x.node_name,x.node_order,'APPROVAL','OR_SIGN',
 CONCAT('{"type":"ROLE","roleCode":"',x.role_code,'"}') FORMAT JSON,NULL,NULL,NULL,NULL,
 CASE WHEN x.business_type IN ('CONTRACT_APPROVAL','OWNER_SETTLEMENT','PAY_REQUEST','PROJECT_BUDGET') THEN 0 ELSE 1 END,
 CASE WHEN x.business_type IN ('CONTRACT_APPROVAL','OWNER_SETTLEMENT','PAY_REQUEST','PROJECT_BUDGET') THEN 0 ELSE 1 END,
 48,NULL,CURRENT_TIMESTAMP,NULL,CURRENT_TIMESTAMP,0,'MAINLINE-89'
FROM wf_template t JOIN m89_matrix x ON x.business_type=t.business_type
WHERE t.template_code=CONCAT('M89-',t.business_type) AND t.deleted_flag=0;

UPDATE wf_template_node SET approver_config=REPLACE(CAST(approver_config AS VARCHAR),'"roleCode":"FINANCE"','"roleCode":"COMPANY_FINANCE"') FORMAT JSON
WHERE deleted_flag=0 AND CAST(approver_config AS VARCHAR) LIKE '%"type":"ROLE"%' AND template_id IN (SELECT id FROM wf_template WHERE enabled=1 AND deleted_flag=0);
UPDATE wf_template_node SET approver_config=REPLACE(CAST(approver_config AS VARCHAR),'"roleCode":"GENERAL_MANAGER"','"roleCode":"COMPANY_OWNER"') FORMAT JSON
WHERE deleted_flag=0 AND CAST(approver_config AS VARCHAR) LIKE '%"type":"ROLE"%' AND template_id IN (SELECT id FROM wf_template WHERE enabled=1 AND deleted_flag=0);
UPDATE wf_template_node SET approver_config=REPLACE(CAST(approver_config AS VARCHAR),'"roleCode":"MANAGEMENT"','"roleCode":"COMPANY_OWNER"') FORMAT JSON
WHERE deleted_flag=0 AND CAST(approver_config AS VARCHAR) LIKE '%"type":"ROLE"%' AND template_id IN (SELECT id FROM wf_template WHERE enabled=1 AND deleted_flag=0);
UPDATE wf_template_node SET approver_config=REPLACE(CAST(approver_config AS VARCHAR),'"roleCode":"MANAGEMENT_EXECUTIVE"','"roleCode":"COMPANY_OWNER"') FORMAT JSON
WHERE deleted_flag=0 AND CAST(approver_config AS VARCHAR) LIKE '%"type":"ROLE"%' AND template_id IN (SELECT id FROM wf_template WHERE enabled=1 AND deleted_flag=0);
UPDATE wf_template_node SET approver_config=REPLACE(CAST(approver_config AS VARCHAR),'"roleCode":"COST_MANAGER"','"roleCode":"PROJECT_ACCOUNTANT"') FORMAT JSON
WHERE deleted_flag=0 AND CAST(approver_config AS VARCHAR) LIKE '%"type":"ROLE"%' AND template_id IN (SELECT id FROM wf_template WHERE enabled=1 AND deleted_flag=0);
UPDATE wf_template_node SET approver_config=REPLACE(CAST(approver_config AS VARCHAR),'"roleCode":"COMMERCIAL_MANAGER"','"roleCode":"PROJECT_ACCOUNTANT"') FORMAT JSON
WHERE deleted_flag=0 AND CAST(approver_config AS VARCHAR) LIKE '%"type":"ROLE"%' AND template_id IN (SELECT id FROM wf_template WHERE enabled=1 AND deleted_flag=0);
UPDATE wf_template_node SET approver_config=REPLACE(CAST(approver_config AS VARCHAR),'"roleCode":"DEPARTMENT_MANAGER"','"roleCode":"PROJECT_ACCOUNTANT"') FORMAT JSON
WHERE deleted_flag=0 AND CAST(approver_config AS VARCHAR) LIKE '%"type":"ROLE"%' AND template_id IN (SELECT id FROM wf_template WHERE enabled=1 AND deleted_flag=0);
UPDATE wf_template_node SET approver_config=REPLACE(CAST(approver_config AS VARCHAR),'"roleCode":"CHIEF_ENGINEER"','"roleCode":"TECHNICAL_LEAD"') FORMAT JSON
WHERE deleted_flag=0 AND CAST(approver_config AS VARCHAR) LIKE '%"type":"ROLE"%' AND template_id IN (SELECT id FROM wf_template WHERE enabled=1 AND deleted_flag=0);
UPDATE wf_template_node SET approver_config=REPLACE(CAST(approver_config AS VARCHAR),'"roleCode":"PRODUCTION_MANAGER"','"roleCode":"CONSTRUCTION_LEAD"') FORMAT JSON
WHERE deleted_flag=0 AND CAST(approver_config AS VARCHAR) LIKE '%"type":"ROLE"%' AND template_id IN (SELECT id FROM wf_template WHERE enabled=1 AND deleted_flag=0);
UPDATE wf_template_node SET approver_config=REPLACE(CAST(approver_config AS VARCHAR),'"roleCode":"PURCHASE_MANAGER"','"roleCode":"PROCUREMENT_LEAD"') FORMAT JSON
WHERE deleted_flag=0 AND CAST(approver_config AS VARCHAR) LIKE '%"type":"ROLE"%' AND template_id IN (SELECT id FROM wf_template WHERE enabled=1 AND deleted_flag=0);
UPDATE wf_template_node SET approver_config=REPLACE(CAST(approver_config AS VARCHAR),'"roleCode":"MATERIAL_CLERK"','"roleCode":"PROCUREMENT_LEAD"') FORMAT JSON
WHERE deleted_flag=0 AND CAST(approver_config AS VARCHAR) LIKE '%"type":"ROLE"%' AND template_id IN (SELECT id FROM wf_template WHERE enabled=1 AND deleted_flag=0);
UPDATE wf_template_node SET approver_config=REPLACE(CAST(approver_config AS VARCHAR),'"roleCode":"COMMON_USER"','"roleCode":"EMPLOYEE"') FORMAT JSON
WHERE deleted_flag=0 AND CAST(approver_config AS VARCHAR) LIKE '%"type":"ROLE"%' AND template_id IN (SELECT id FROM wf_template WHERE enabled=1 AND deleted_flag=0);
UPDATE wf_template_node SET approver_config=REPLACE(CAST(approver_config AS VARCHAR),'"roleCode":"PM"','"roleCode":"PROJECT_MANAGER"') FORMAT JSON
WHERE deleted_flag=0 AND CAST(approver_config AS VARCHAR) LIKE '%"type":"ROLE"%' AND template_id IN (SELECT id FROM wf_template WHERE enabled=1 AND deleted_flag=0);
UPDATE wf_template_node SET approver_config=REPLACE(CAST(approver_config AS VARCHAR),'"roleCode":"CSTM"','"roleCode":"PROJECT_ACCOUNTANT"') FORMAT JSON
WHERE deleted_flag=0 AND CAST(approver_config AS VARCHAR) LIKE '%"type":"ROLE"%' AND template_id IN (SELECT id FROM wf_template WHERE enabled=1 AND deleted_flag=0);
UPDATE wf_template_node SET approver_config=REPLACE(CAST(approver_config AS VARCHAR),'"roleCode":"CM"','"roleCode":"PROJECT_ACCOUNTANT"') FORMAT JSON
WHERE deleted_flag=0 AND CAST(approver_config AS VARCHAR) LIKE '%"type":"ROLE"%' AND template_id IN (SELECT id FROM wf_template WHERE enabled=1 AND deleted_flag=0);

UPDATE wf_template_node SET approver_config=REPLACE(CAST(approver_config AS VARCHAR),'"roleCode":"PM"','"roleCode":"PROJECT_MANAGER"') FORMAT JSON
WHERE deleted_flag=0 AND CAST(approver_config AS VARCHAR) LIKE '%"type":"PROJECT_ROLE"%' AND template_id IN (SELECT id FROM wf_template WHERE enabled=1 AND deleted_flag=0);
UPDATE wf_template_node SET approver_config=REPLACE(CAST(approver_config AS VARCHAR),'"roleCode":"CSTM"','"roleCode":"PROJECT_ACCOUNTANT"') FORMAT JSON
WHERE deleted_flag=0 AND CAST(approver_config AS VARCHAR) LIKE '%"type":"PROJECT_ROLE"%' AND template_id IN (SELECT id FROM wf_template WHERE enabled=1 AND deleted_flag=0);
UPDATE wf_template_node SET approver_config=REPLACE(CAST(approver_config AS VARCHAR),'"roleCode":"CM"','"roleCode":"PROJECT_ACCOUNTANT"') FORMAT JSON
WHERE deleted_flag=0 AND CAST(approver_config AS VARCHAR) LIKE '%"type":"PROJECT_ROLE"%' AND template_id IN (SELECT id FROM wf_template WHERE enabled=1 AND deleted_flag=0);

DROP TABLE m89_matrix;
DROP TABLE m89_write_perm;
DROP TABLE m89_grant;
DROP TABLE m89_menu_spec;
DROP TABLE m89_assigned_user;
DROP TABLE m89_finance_user;
DROP TABLE m89_user_target;
DROP TABLE m89_role_spec;
DROP TABLE m89_tenant;
