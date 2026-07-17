CREATE TABLE sys_type_registry (
    id BIGINT NOT NULL PRIMARY KEY,
    type_domain VARCHAR(64) NOT NULL,
    type_code VARCHAR(64) NOT NULL,
    owner_module VARCHAR(64) NOT NULL,
    contract_version VARCHAR(16) NOT NULL,
    status VARCHAR(32) DEFAULT 'ACTIVE' NOT NULL,
    description VARCHAR(500) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uk_type_registry_domain_code UNIQUE(type_domain,type_code),
    CONSTRAINT ck_type_registry_status CHECK(status IN ('ACTIVE','DEPRECATED'))
);

INSERT INTO sys_type_registry(id,type_domain,type_code,owner_module,contract_version,status,description) SELECT 200001,'WORKFLOW_BUSINESS_TYPE','CONTRACT_APPROVAL','workflow','1.0','ACTIVE','工作流业务类型' WHERE NOT EXISTS(SELECT 1 FROM sys_type_registry WHERE type_domain='WORKFLOW_BUSINESS_TYPE' AND type_code='CONTRACT_APPROVAL');
INSERT INTO sys_type_registry(id,type_domain,type_code,owner_module,contract_version,status,description) SELECT 200002,'WORKFLOW_BUSINESS_TYPE','PROJECT_APPROVAL','workflow','1.0','ACTIVE','工作流业务类型' WHERE NOT EXISTS(SELECT 1 FROM sys_type_registry WHERE type_domain='WORKFLOW_BUSINESS_TYPE' AND type_code='PROJECT_APPROVAL');
INSERT INTO sys_type_registry(id,type_domain,type_code,owner_module,contract_version,status,description) SELECT 200003,'WORKFLOW_BUSINESS_TYPE','PURCHASE_ORDER','workflow','1.0','ACTIVE','工作流业务类型' WHERE NOT EXISTS(SELECT 1 FROM sys_type_registry WHERE type_domain='WORKFLOW_BUSINESS_TYPE' AND type_code='PURCHASE_ORDER');
INSERT INTO sys_type_registry(id,type_domain,type_code,owner_module,contract_version,status,description) SELECT 200004,'WORKFLOW_BUSINESS_TYPE','MATERIAL_RECEIPT','workflow','1.0','ACTIVE','工作流业务类型' WHERE NOT EXISTS(SELECT 1 FROM sys_type_registry WHERE type_domain='WORKFLOW_BUSINESS_TYPE' AND type_code='MATERIAL_RECEIPT');
INSERT INTO sys_type_registry(id,type_domain,type_code,owner_module,contract_version,status,description) SELECT 200005,'WORKFLOW_BUSINESS_TYPE','SUB_MEASURE','workflow','1.0','ACTIVE','工作流业务类型' WHERE NOT EXISTS(SELECT 1 FROM sys_type_registry WHERE type_domain='WORKFLOW_BUSINESS_TYPE' AND type_code='SUB_MEASURE');
INSERT INTO sys_type_registry(id,type_domain,type_code,owner_module,contract_version,status,description) SELECT 200006,'WORKFLOW_BUSINESS_TYPE','PAY_REQUEST','workflow','1.0','ACTIVE','工作流业务类型' WHERE NOT EXISTS(SELECT 1 FROM sys_type_registry WHERE type_domain='WORKFLOW_BUSINESS_TYPE' AND type_code='PAY_REQUEST');
INSERT INTO sys_type_registry(id,type_domain,type_code,owner_module,contract_version,status,description) SELECT 200007,'WORKFLOW_BUSINESS_TYPE','VAR_ORDER','workflow','1.0','ACTIVE','工作流业务类型' WHERE NOT EXISTS(SELECT 1 FROM sys_type_registry WHERE type_domain='WORKFLOW_BUSINESS_TYPE' AND type_code='VAR_ORDER');
INSERT INTO sys_type_registry(id,type_domain,type_code,owner_module,contract_version,status,description) SELECT 200008,'WORKFLOW_BUSINESS_TYPE','PURCHASE_REQUEST','workflow','1.0','ACTIVE','工作流业务类型' WHERE NOT EXISTS(SELECT 1 FROM sys_type_registry WHERE type_domain='WORKFLOW_BUSINESS_TYPE' AND type_code='PURCHASE_REQUEST');
INSERT INTO sys_type_registry(id,type_domain,type_code,owner_module,contract_version,status,description) SELECT 200009,'WORKFLOW_BUSINESS_TYPE','CT_CHANGE','workflow','1.0','ACTIVE','工作流业务类型' WHERE NOT EXISTS(SELECT 1 FROM sys_type_registry WHERE type_domain='WORKFLOW_BUSINESS_TYPE' AND type_code='CT_CHANGE');
INSERT INTO sys_type_registry(id,type_domain,type_code,owner_module,contract_version,status,description) SELECT 200010,'WORKFLOW_BUSINESS_TYPE','SETTLEMENT','workflow','1.0','ACTIVE','工作流业务类型' WHERE NOT EXISTS(SELECT 1 FROM sys_type_registry WHERE type_domain='WORKFLOW_BUSINESS_TYPE' AND type_code='SETTLEMENT');
INSERT INTO sys_type_registry(id,type_domain,type_code,owner_module,contract_version,status,description) SELECT 200011,'WORKFLOW_BUSINESS_TYPE','COST_TARGET','workflow','1.0','ACTIVE','工作流业务类型' WHERE NOT EXISTS(SELECT 1 FROM sys_type_registry WHERE type_domain='WORKFLOW_BUSINESS_TYPE' AND type_code='COST_TARGET');
INSERT INTO sys_type_registry(id,type_domain,type_code,owner_module,contract_version,status,description) SELECT 200012,'WORKFLOW_BUSINESS_TYPE','CONTRACT_REVENUE','workflow','1.0','ACTIVE','工作流业务类型' WHERE NOT EXISTS(SELECT 1 FROM sys_type_registry WHERE type_domain='WORKFLOW_BUSINESS_TYPE' AND type_code='CONTRACT_REVENUE');
INSERT INTO sys_type_registry(id,type_domain,type_code,owner_module,contract_version,status,description) SELECT 200013,'WORKFLOW_BUSINESS_TYPE','MATERIAL_REQUISITION','workflow','1.0','ACTIVE','工作流业务类型' WHERE NOT EXISTS(SELECT 1 FROM sys_type_registry WHERE type_domain='WORKFLOW_BUSINESS_TYPE' AND type_code='MATERIAL_REQUISITION');
INSERT INTO sys_type_registry(id,type_domain,type_code,owner_module,contract_version,status,description) SELECT 200014,'WORKFLOW_BUSINESS_TYPE','TECH_ITEM','workflow','1.0','ACTIVE','工作流业务类型' WHERE NOT EXISTS(SELECT 1 FROM sys_type_registry WHERE type_domain='WORKFLOW_BUSINESS_TYPE' AND type_code='TECH_ITEM');
INSERT INTO sys_type_registry(id,type_domain,type_code,owner_module,contract_version,status,description) SELECT 200015,'WORKFLOW_BUSINESS_TYPE','PROJECT_BUDGET','workflow','1.0','ACTIVE','工作流业务类型' WHERE NOT EXISTS(SELECT 1 FROM sys_type_registry WHERE type_domain='WORKFLOW_BUSINESS_TYPE' AND type_code='PROJECT_BUDGET');
INSERT INTO sys_type_registry(id,type_domain,type_code,owner_module,contract_version,status,description) SELECT 200016,'WORKFLOW_BUSINESS_TYPE','EXPENSE','workflow','1.0','ACTIVE','工作流业务类型' WHERE NOT EXISTS(SELECT 1 FROM sys_type_registry WHERE type_domain='WORKFLOW_BUSINESS_TYPE' AND type_code='EXPENSE');
INSERT INTO sys_type_registry(id,type_domain,type_code,owner_module,contract_version,status,description) SELECT 200017,'WORKFLOW_BUSINESS_TYPE','OWNER_SETTLEMENT','workflow','1.0','ACTIVE','工作流业务类型' WHERE NOT EXISTS(SELECT 1 FROM sys_type_registry WHERE type_domain='WORKFLOW_BUSINESS_TYPE' AND type_code='OWNER_SETTLEMENT');
INSERT INTO sys_type_registry(id,type_domain,type_code,owner_module,contract_version,status,description) SELECT 200018,'WORKFLOW_BUSINESS_TYPE','PRODUCTION_MEASUREMENT','workflow','1.0','ACTIVE','工作流业务类型' WHERE NOT EXISTS(SELECT 1 FROM sys_type_registry WHERE type_domain='WORKFLOW_BUSINESS_TYPE' AND type_code='PRODUCTION_MEASUREMENT');
INSERT INTO sys_type_registry(id,type_domain,type_code,owner_module,contract_version,status,description) SELECT 200019,'WORKFLOW_BUSINESS_TYPE','PROJECT_SCHEDULE','workflow','1.0','ACTIVE','工作流业务类型' WHERE NOT EXISTS(SELECT 1 FROM sys_type_registry WHERE type_domain='WORKFLOW_BUSINESS_TYPE' AND type_code='PROJECT_SCHEDULE');
INSERT INTO sys_type_registry(id,type_domain,type_code,owner_module,contract_version,status,description) SELECT 200020,'WORKFLOW_BUSINESS_TYPE','PROJECT_PERIOD_PLAN','workflow','1.0','ACTIVE','工作流业务类型' WHERE NOT EXISTS(SELECT 1 FROM sys_type_registry WHERE type_domain='WORKFLOW_BUSINESS_TYPE' AND type_code='PROJECT_PERIOD_PLAN');
INSERT INTO sys_type_registry(id,type_domain,type_code,owner_module,contract_version,status,description) SELECT 200021,'WORKFLOW_BUSINESS_TYPE','PROJECT_CORRECTIVE_ACTION','workflow','1.0','ACTIVE','工作流业务类型' WHERE NOT EXISTS(SELECT 1 FROM sys_type_registry WHERE type_domain='WORKFLOW_BUSINESS_TYPE' AND type_code='PROJECT_CORRECTIVE_ACTION');
INSERT INTO sys_type_registry(id,type_domain,type_code,owner_module,contract_version,status,description) SELECT 200022,'WORKFLOW_BUSINESS_TYPE','COST_CORRECTIVE_ACTION','workflow','1.0','ACTIVE','工作流业务类型' WHERE NOT EXISTS(SELECT 1 FROM sys_type_registry WHERE type_domain='WORKFLOW_BUSINESS_TYPE' AND type_code='COST_CORRECTIVE_ACTION');
INSERT INTO sys_type_registry(id,type_domain,type_code,owner_module,contract_version,status,description) SELECT 200101,'COST_SOURCE_TYPE','CT_CONTRACT','cost','1.0','ACTIVE','成本来源类型' WHERE NOT EXISTS(SELECT 1 FROM sys_type_registry WHERE type_domain='COST_SOURCE_TYPE' AND type_code='CT_CONTRACT');
INSERT INTO sys_type_registry(id,type_domain,type_code,owner_module,contract_version,status,description) SELECT 200102,'COST_SOURCE_TYPE','MAT_RECEIPT','cost','1.0','ACTIVE','成本来源类型' WHERE NOT EXISTS(SELECT 1 FROM sys_type_registry WHERE type_domain='COST_SOURCE_TYPE' AND type_code='MAT_RECEIPT');
INSERT INTO sys_type_registry(id,type_domain,type_code,owner_module,contract_version,status,description) SELECT 200103,'COST_SOURCE_TYPE','SUB_MEASURE','cost','1.0','ACTIVE','成本来源类型' WHERE NOT EXISTS(SELECT 1 FROM sys_type_registry WHERE type_domain='COST_SOURCE_TYPE' AND type_code='SUB_MEASURE');
INSERT INTO sys_type_registry(id,type_domain,type_code,owner_module,contract_version,status,description) SELECT 200104,'COST_SOURCE_TYPE','VAR_ORDER','cost','1.0','ACTIVE','成本来源类型' WHERE NOT EXISTS(SELECT 1 FROM sys_type_registry WHERE type_domain='COST_SOURCE_TYPE' AND type_code='VAR_ORDER');
INSERT INTO sys_type_registry(id,type_domain,type_code,owner_module,contract_version,status,description) SELECT 200105,'COST_SOURCE_TYPE','CT_REVENUE','cost','1.0','ACTIVE','成本来源类型' WHERE NOT EXISTS(SELECT 1 FROM sys_type_registry WHERE type_domain='COST_SOURCE_TYPE' AND type_code='CT_REVENUE');
INSERT INTO sys_type_registry(id,type_domain,type_code,owner_module,contract_version,status,description) SELECT 200106,'COST_SOURCE_TYPE','MAT_REQUISITION','cost','1.0','ACTIVE','成本来源类型' WHERE NOT EXISTS(SELECT 1 FROM sys_type_registry WHERE type_domain='COST_SOURCE_TYPE' AND type_code='MAT_REQUISITION');
INSERT INTO sys_type_registry(id,type_domain,type_code,owner_module,contract_version,status,description) SELECT 200107,'COST_SOURCE_TYPE','MATERIAL_RETURN','cost','1.0','ACTIVE','成本来源类型' WHERE NOT EXISTS(SELECT 1 FROM sys_type_registry WHERE type_domain='COST_SOURCE_TYPE' AND type_code='MATERIAL_RETURN');
INSERT INTO sys_type_registry(id,type_domain,type_code,owner_module,contract_version,status,description) SELECT 200108,'COST_SOURCE_TYPE','SUPPLIER_RETURN','cost','1.0','ACTIVE','成本来源类型' WHERE NOT EXISTS(SELECT 1 FROM sys_type_registry WHERE type_domain='COST_SOURCE_TYPE' AND type_code='SUPPLIER_RETURN');
INSERT INTO sys_type_registry(id,type_domain,type_code,owner_module,contract_version,status,description) SELECT 200109,'COST_SOURCE_TYPE','CT_CHANGE','cost','1.0','ACTIVE','成本来源类型' WHERE NOT EXISTS(SELECT 1 FROM sys_type_registry WHERE type_domain='COST_SOURCE_TYPE' AND type_code='CT_CHANGE');
INSERT INTO sys_type_registry(id,type_domain,type_code,owner_module,contract_version,status,description) SELECT 200110,'COST_SOURCE_TYPE','BID_COST','cost','1.0','ACTIVE','成本来源类型' WHERE NOT EXISTS(SELECT 1 FROM sys_type_registry WHERE type_domain='COST_SOURCE_TYPE' AND type_code='BID_COST');
INSERT INTO sys_type_registry(id,type_domain,type_code,owner_module,contract_version,status,description) SELECT 200111,'COST_SOURCE_TYPE','BID_COST_TRANSFERRED','cost','1.0','ACTIVE','成本来源类型' WHERE NOT EXISTS(SELECT 1 FROM sys_type_registry WHERE type_domain='COST_SOURCE_TYPE' AND type_code='BID_COST_TRANSFERRED');
INSERT INTO sys_type_registry(id,type_domain,type_code,owner_module,contract_version,status,description) SELECT 200112,'COST_SOURCE_TYPE','OVERHEAD_ALLOCATION','cost','1.0','ACTIVE','成本来源类型' WHERE NOT EXISTS(SELECT 1 FROM sys_type_registry WHERE type_domain='COST_SOURCE_TYPE' AND type_code='OVERHEAD_ALLOCATION');

CREATE OR REPLACE VIEW v_business_audit_event AS
SELECT 'FINANCE' event_domain,id,tenant_id,event_type,business_type,business_id,project_id,
       operator_id,event_at,archive_bucket,payload_json,payload_hash FROM finance_audit_event
UNION ALL
SELECT 'REVENUE' event_domain,id,tenant_id,event_type,business_type,business_id,project_id,
       operator_id,event_at,archive_bucket,payload_json,payload_hash FROM revenue_audit_event;

CREATE OR REPLACE VIEW v_reconciliation_issue AS
SELECT 'FINANCE' issue_domain,id,tenant_id,run_id,dimension_type,business_id,issue_code,
       expected_amount,actual_amount,status,detail,created_at FROM finance_reconciliation_issue
UNION ALL
SELECT 'REVENUE' issue_domain,id,tenant_id,run_id,dimension_type,business_id,issue_code,
       expected_amount,actual_amount,status,detail,created_at FROM revenue_reconciliation_issue;
