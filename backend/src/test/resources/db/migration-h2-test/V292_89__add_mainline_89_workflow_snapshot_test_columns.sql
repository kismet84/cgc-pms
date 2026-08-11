ALTER TABLE wf_instance
    ADD COLUMN IF NOT EXISTS security_policy_json VARCHAR(1000);

ALTER TABLE wf_node_instance
    ADD COLUMN IF NOT EXISTS node_type VARCHAR(50);
ALTER TABLE wf_node_instance
    ADD COLUMN IF NOT EXISTS approver_config VARCHAR(1000);
ALTER TABLE wf_node_instance
    ADD COLUMN IF NOT EXISTS allow_transfer TINYINT;
ALTER TABLE wf_node_instance
    ADD COLUMN IF NOT EXISTS allow_add_sign TINYINT;
ALTER TABLE wf_node_instance
    ADD COLUMN IF NOT EXISTS timeout_hours INT;

ALTER TABLE sys_operation_audit_log
    ADD COLUMN IF NOT EXISTS before_snapshot VARCHAR(10000);
ALTER TABLE sys_operation_audit_log
    ADD COLUMN IF NOT EXISTS after_snapshot VARCHAR(10000);

-- Frozen legacy Spring fixtures predate V293. Keep legacy templates executable while
-- dedicated V293 tests verify strict policies and the fixed-role migration itself.
UPDATE wf_template
SET condition_rule='{"preventInitiatorApproval":false,"maxApprovalsPerUser":100,"requireProjectMembership":false,"allowAdminFallback":true}'
WHERE enabled=1 AND deleted_flag=0;

-- Legacy controller/service tests authenticate as ADMIN. Represent that historical
-- test identity as an ALL-scope role without changing the production fixed-role set.
INSERT INTO sys_role
    (id,tenant_id,role_code,role_name,role_type,status,data_scope,created_at,updated_at,deleted_flag,remark,role_level)
SELECT 292890000000000000 + ROW_NUMBER() OVER (ORDER BY t.tenant_id),
       t.tenant_id,'ADMIN','Legacy test administrator','SYSTEM','ENABLE','ALL',
       CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'TEST-FIXTURE-ONLY',0
FROM (SELECT DISTINCT tenant_id FROM sys_user) t
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role r
    WHERE r.tenant_id=t.tenant_id AND r.role_code='ADMIN' AND r.deleted_flag=0
);

INSERT INTO sys_role
    (id,tenant_id,role_code,role_name,role_type,status,data_scope,created_at,updated_at,deleted_flag,remark,role_level)
SELECT 292891000000000000 + ROW_NUMBER() OVER (ORDER BY t.tenant_id,s.ordinal_no),
       t.tenant_id,s.role_code,s.role_name,'SYSTEM','ENABLE',s.data_scope,
       CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'TEST-FIXTURE-ONLY',s.role_level
FROM (SELECT DISTINCT tenant_id FROM sys_role) t
CROSS JOIN (VALUES
    (1,'COMPANY_OWNER','公司老板','ALL',1),
    (2,'COMPANY_FINANCE','公司财务','ALL',0),
    (3,'PROJECT_MANAGER','项目经理','PROJECT_MEMBER',2),
    (4,'PROJECT_ACCOUNTANT','项目会计','PROJECT_MEMBER',2),
    (5,'TECHNICAL_LEAD','技术负责人','PROJECT_MEMBER',2),
    (6,'SAFETY_LEAD','安全负责人','PROJECT_MEMBER',2),
    (7,'CONSTRUCTION_LEAD','施工负责人','PROJECT_MEMBER',2),
    (8,'PROCUREMENT_LEAD','采购负责人','PROJECT_MEMBER',2),
    (9,'EMPLOYEE','员工','PROJECT_MEMBER',3),
    (10,'SUPER_ADMIN','隐藏超级管理员','ALL',0)
) s(ordinal_no,role_code,role_name,data_scope,role_level)
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role r
    WHERE r.tenant_id=t.tenant_id AND r.role_code=s.role_code AND r.deleted_flag=0
);

CREATE TABLE IF NOT EXISTS bid_cost_target_transfer_request (
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
    remark VARCHAR(500)
);
CREATE INDEX IF NOT EXISTS idx_bid_transfer_request_source
    ON bid_cost_target_transfer_request(tenant_id,bid_cost_id,target_id,status);

CREATE TABLE IF NOT EXISTS bid_cost_target_transfer_request_line (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    request_id BIGINT NOT NULL,
    source_cost_item_id BIGINT NOT NULL,
    source_subject_id BIGINT NOT NULL,
    target_subject_id BIGINT NOT NULL,
    amount DECIMAL(18,2) NOT NULL
);

CREATE TABLE IF NOT EXISTS finance_cost_allocation_request (
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
    remark VARCHAR(500)
);
CREATE INDEX IF NOT EXISTS idx_finance_allocation_request_source
    ON finance_cost_allocation_request(tenant_id,source_type,source_id,status);

CREATE TABLE IF NOT EXISTS finance_cost_allocation_request_line (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    request_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    basis_value DECIMAL(18,4) NOT NULL,
    allocated_amount DECIMAL(18,2) NOT NULL
);

INSERT INTO sys_type_registry
    (id,type_domain,type_code,owner_module,contract_version,status,description,created_at,updated_at)
SELECT x.id,'WORKFLOW_BUSINESS_TYPE',x.type_code,x.owner_module,'1.0','ACTIVE',
       'Mainline 89 test fixture',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
FROM (VALUES
    (2928901,'BID_COST_TARGET_TRANSFER','cost'),
    (2928902,'FINANCE_COST_ALLOCATION','cost'),
    (2928903,'QS_RECTIFICATION','quality'),
    (2928904,'QS_CONSEQUENCE','quality')
) x(id,type_code,owner_module)
WHERE NOT EXISTS (
    SELECT 1 FROM sys_type_registry r
    WHERE r.type_domain='WORKFLOW_BUSINESS_TYPE' AND r.type_code=x.type_code
);
