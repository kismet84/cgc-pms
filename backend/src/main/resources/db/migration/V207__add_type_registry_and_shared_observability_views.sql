SET NAMES utf8mb4;

CREATE TABLE sys_type_registry (
    id BIGINT NOT NULL COMMENT '注册项ID',
    type_domain VARCHAR(64) NOT NULL COMMENT '类型域，例如WORKFLOW_BUSINESS_TYPE',
    type_code VARCHAR(64) NOT NULL COMMENT '类型编码',
    owner_module VARCHAR(64) NOT NULL COMMENT '权威维护模块',
    contract_version VARCHAR(16) NOT NULL COMMENT '契约版本',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DEPRECATED',
    description VARCHAR(500) NOT NULL COMMENT '语义与引用边界',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY(id),
    UNIQUE KEY uk_type_registry_domain_code(type_domain,type_code),
    CONSTRAINT ck_type_registry_status CHECK(status IN ('ACTIVE','DEPRECATED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='多态业务类型契约注册表';

INSERT IGNORE INTO sys_type_registry
    (id,type_domain,type_code,owner_module,contract_version,status,description)
VALUES
    (200001,'WORKFLOW_BUSINESS_TYPE','CONTRACT_APPROVAL','workflow','1.0','ACTIVE','工作流业务类型'),
    (200002,'WORKFLOW_BUSINESS_TYPE','PROJECT_APPROVAL','workflow','1.0','ACTIVE','工作流业务类型'),
    (200003,'WORKFLOW_BUSINESS_TYPE','PURCHASE_ORDER','workflow','1.0','ACTIVE','工作流业务类型'),
    (200004,'WORKFLOW_BUSINESS_TYPE','MATERIAL_RECEIPT','workflow','1.0','ACTIVE','工作流业务类型'),
    (200005,'WORKFLOW_BUSINESS_TYPE','SUB_MEASURE','workflow','1.0','ACTIVE','工作流业务类型'),
    (200006,'WORKFLOW_BUSINESS_TYPE','PAY_REQUEST','workflow','1.0','ACTIVE','工作流业务类型'),
    (200007,'WORKFLOW_BUSINESS_TYPE','VAR_ORDER','workflow','1.0','ACTIVE','工作流业务类型'),
    (200008,'WORKFLOW_BUSINESS_TYPE','PURCHASE_REQUEST','workflow','1.0','ACTIVE','工作流业务类型'),
    (200009,'WORKFLOW_BUSINESS_TYPE','CT_CHANGE','workflow','1.0','ACTIVE','工作流业务类型'),
    (200010,'WORKFLOW_BUSINESS_TYPE','SETTLEMENT','workflow','1.0','ACTIVE','工作流业务类型'),
    (200011,'WORKFLOW_BUSINESS_TYPE','COST_TARGET','workflow','1.0','ACTIVE','工作流业务类型'),
    (200012,'WORKFLOW_BUSINESS_TYPE','CONTRACT_REVENUE','workflow','1.0','ACTIVE','工作流业务类型'),
    (200013,'WORKFLOW_BUSINESS_TYPE','MATERIAL_REQUISITION','workflow','1.0','ACTIVE','工作流业务类型'),
    (200014,'WORKFLOW_BUSINESS_TYPE','TECH_ITEM','workflow','1.0','ACTIVE','工作流业务类型'),
    (200015,'WORKFLOW_BUSINESS_TYPE','PROJECT_BUDGET','workflow','1.0','ACTIVE','工作流业务类型'),
    (200016,'WORKFLOW_BUSINESS_TYPE','EXPENSE','workflow','1.0','ACTIVE','工作流业务类型'),
    (200017,'WORKFLOW_BUSINESS_TYPE','OWNER_SETTLEMENT','workflow','1.0','ACTIVE','工作流业务类型'),
    (200018,'WORKFLOW_BUSINESS_TYPE','PRODUCTION_MEASUREMENT','workflow','1.0','ACTIVE','工作流业务类型'),
    (200019,'WORKFLOW_BUSINESS_TYPE','PROJECT_SCHEDULE','workflow','1.0','ACTIVE','工作流业务类型'),
    (200020,'WORKFLOW_BUSINESS_TYPE','PROJECT_PERIOD_PLAN','workflow','1.0','ACTIVE','工作流业务类型'),
    (200021,'WORKFLOW_BUSINESS_TYPE','PROJECT_CORRECTIVE_ACTION','workflow','1.0','ACTIVE','工作流业务类型'),
    (200022,'WORKFLOW_BUSINESS_TYPE','COST_CORRECTIVE_ACTION','workflow','1.0','ACTIVE','工作流业务类型'),
    (200023,'WORKFLOW_BUSINESS_TYPE','TECHNICAL_SCHEME','workflow','1.0','ACTIVE','工作流业务类型'),
    (200024,'WORKFLOW_BUSINESS_TYPE','PROJECT_FINAL_ACCEPTANCE','workflow','1.0','ACTIVE','工作流业务类型'),
    (200101,'COST_SOURCE_TYPE','CT_CONTRACT','cost','1.0','ACTIVE','成本来源类型'),
    (200102,'COST_SOURCE_TYPE','MAT_RECEIPT','cost','1.0','ACTIVE','成本来源类型'),
    (200103,'COST_SOURCE_TYPE','SUB_MEASURE','cost','1.0','ACTIVE','成本来源类型'),
    (200104,'COST_SOURCE_TYPE','VAR_ORDER','cost','1.0','ACTIVE','成本来源类型'),
    (200105,'COST_SOURCE_TYPE','CT_REVENUE','cost','1.0','ACTIVE','成本来源类型'),
    (200106,'COST_SOURCE_TYPE','MAT_REQUISITION','cost','1.0','ACTIVE','成本来源类型'),
    (200107,'COST_SOURCE_TYPE','MATERIAL_RETURN','cost','1.0','ACTIVE','成本来源类型'),
    (200108,'COST_SOURCE_TYPE','SUPPLIER_RETURN','cost','1.0','ACTIVE','成本来源类型'),
    (200109,'COST_SOURCE_TYPE','CT_CHANGE','cost','1.0','ACTIVE','成本来源类型'),
    (200110,'COST_SOURCE_TYPE','BID_COST','cost','1.0','ACTIVE','成本来源类型'),
    (200111,'COST_SOURCE_TYPE','BID_COST_TRANSFERRED','cost','1.0','ACTIVE','成本来源类型'),
    (200112,'COST_SOURCE_TYPE','OVERHEAD_ALLOCATION','cost','1.0','ACTIVE','成本来源类型');

CREATE OR REPLACE VIEW v_business_audit_event AS
SELECT 'FINANCE' event_domain,id,tenant_id,event_type,business_type,business_id,project_id,
       operator_id,event_at,archive_bucket,payload_json,payload_hash
  FROM finance_audit_event
UNION ALL
SELECT 'REVENUE' event_domain,id,tenant_id,event_type,business_type,business_id,project_id,
       operator_id,event_at,archive_bucket,payload_json,payload_hash
  FROM revenue_audit_event;

CREATE OR REPLACE VIEW v_reconciliation_issue AS
SELECT 'FINANCE' issue_domain,id,tenant_id,run_id,dimension_type,business_id,issue_code,
       expected_amount,actual_amount,status,detail,created_at
  FROM finance_reconciliation_issue
UNION ALL
SELECT 'REVENUE' issue_domain,id,tenant_id,run_id,dimension_type,business_id,issue_code,
       expected_amount,actual_amount,status,detail,created_at
  FROM revenue_reconciliation_issue;
