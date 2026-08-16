-- Frozen legacy Spring fixtures intentionally do not load the production B215/V301 chain.
-- Keep their cost_item shape compatible with the current entity while production migration
-- behavior remains covered by the dedicated H2/MySQL Flyway compatibility suites.
ALTER TABLE cost_item ADD COLUMN IF NOT EXISTS classification_status VARCHAR(24) DEFAULT 'LEGACY_CLASSIFIED' NOT NULL;
ALTER TABLE cost_item ADD COLUMN IF NOT EXISTS classification_business_category VARCHAR(64) DEFAULT '*' NOT NULL;
ALTER TABLE cost_item ADD COLUMN IF NOT EXISTS recognition_role VARCHAR(16) DEFAULT 'ACTUAL' NOT NULL;
ALTER TABLE cost_item ADD COLUMN IF NOT EXISTS root_source_type VARCHAR(64);
ALTER TABLE cost_item ADD COLUMN IF NOT EXISTS mapping_version_id BIGINT;
ALTER TABLE cost_item ADD COLUMN IF NOT EXISTS assignment_rule_id BIGINT;
ALTER TABLE cost_item ADD COLUMN IF NOT EXISTS original_cost_subject_id BIGINT;
ALTER TABLE cost_item ADD COLUMN IF NOT EXISTS classification_override_id BIGINT;
ALTER TABLE cost_item ADD COLUMN IF NOT EXISTS classification_snapshot_id BIGINT;
ALTER TABLE cost_item ADD COLUMN IF NOT EXISTS adjustment_batch_id BIGINT;
ALTER TABLE cost_item ADD COLUMN IF NOT EXISTS original_cost_item_id BIGINT;

-- Frozen Spring fixtures do not load production V300. Keep their accounting catalog aligned so
-- entry-generation tests exercise governed codes instead of failing on missing test data.
CREATE LOCAL TEMPORARY TABLE m96_test_accounting_subject(
 subject_code VARCHAR(64) PRIMARY KEY,subject_name VARCHAR(128) NOT NULL,
 account_category VARCHAR(32) NOT NULL,sort_order INT NOT NULL);
INSERT INTO m96_test_accounting_subject VALUES
 ('1002-BANK','银行存款','ASSET',10),('1122-AR','应收账款','ASSET',20),
 ('1123-PREPAY','预付账款','ASSET',30),('2202-AP','应付账款','LIABILITY',40),
 ('2203-ADVANCE','预收账款','LIABILITY',50);
INSERT INTO cost_subject
 (id,tenant_id,parent_id,subject_code,subject_name,subject_type,account_category,
  level,sort_order,status,created_at,updated_at,deleted_flag)
SELECT 292960400000000000+ROW_NUMBER() OVER(ORDER BY tenants.tenant_id,catalog.sort_order),
 tenants.tenant_id,0,catalog.subject_code,catalog.subject_name,'GENERAL_LEDGER',
 catalog.account_category,1,catalog.sort_order,'ENABLE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM (SELECT DISTINCT tenant_id FROM cost_subject WHERE deleted_flag=0) tenants
JOIN m96_test_accounting_subject catalog ON 1=1
WHERE NOT EXISTS(SELECT 1 FROM cost_subject existing
 WHERE existing.tenant_id=tenants.tenant_id AND existing.subject_code=catalog.subject_code
   AND existing.deleted_flag=0);
DROP TABLE m96_test_accounting_subject;

ALTER TABLE finance_cost_allocation_request ADD COLUMN IF NOT EXISTS matched_cost_subject_id BIGINT;
ALTER TABLE finance_cost_allocation_request_line ADD COLUMN IF NOT EXISTS matched_cost_subject_id BIGINT;
ALTER TABLE finance_cost_allocation_request_line ADD COLUMN IF NOT EXISTS selected_cost_subject_id BIGINT;

CREATE TABLE IF NOT EXISTS project_cost_subject_scope_history (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,project_id BIGINT NOT NULL,
 config_request_id BIGINT,configuration_version INT NOT NULL,cost_subject_id BIGINT NOT NULL,
 enabled TINYINT NOT NULL,effective_from DATE NOT NULL,effective_to DATE,recorded_by BIGINT,
 recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,remark VARCHAR(500),
 CONSTRAINT uk_test_project_cost_scope_history
  UNIQUE(tenant_id,project_id,cost_subject_id,configuration_version));

CREATE TABLE IF NOT EXISTS cost_recalculation_batch (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,batch_type VARCHAR(32) NOT NULL,status VARCHAR(16) NOT NULL);

CREATE TABLE IF NOT EXISTS cost_project_config_request_line (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,request_id BIGINT NOT NULL,
 cost_subject_id BIGINT NOT NULL,enabled TINYINT NOT NULL,effective_from DATE NOT NULL,
 effective_to DATE,impact_snapshot CLOB);

CREATE TABLE IF NOT EXISTS cost_recalculation_line (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,batch_id BIGINT NOT NULL,
 original_cost_item_id BIGINT NOT NULL,old_cost_subject_id BIGINT,new_cost_subject_id BIGINT,
 mapping_version_id BIGINT,assignment_rule_id BIGINT,amount DECIMAL(18,2) NOT NULL,
 tax_amount DECIMAL(18,2) DEFAULT 0 NOT NULL,amount_without_tax DECIMAL(18,2) DEFAULT 0 NOT NULL,
 source_snapshot_hash VARCHAR(64) NOT NULL,difference_type VARCHAR(24) NOT NULL,
 negative_cost_item_id BIGINT,positive_cost_item_id BIGINT);

CREATE TABLE IF NOT EXISTS cost_recalculation_fact_reservation (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,batch_id BIGINT NOT NULL,
 original_cost_item_id BIGINT NOT NULL,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 CONSTRAINT uk_test_cost_recalculation_reservation UNIQUE(tenant_id,original_cost_item_id));

CREATE TABLE IF NOT EXISTS cost_classification_override (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,source_type VARCHAR(64) NOT NULL,source_id BIGINT NOT NULL,
 source_item_id BIGINT DEFAULT 0 NOT NULL,original_cost_subject_id BIGINT,matched_cost_subject_id BIGINT,
 override_cost_subject_id BIGINT NOT NULL,mapping_version_id BIGINT,assignment_rule_id BIGINT,
 override_reason VARCHAR(500) NOT NULL,status VARCHAR(16) DEFAULT 'ACTIVE' NOT NULL,version INT DEFAULT 0 NOT NULL,
 created_by BIGINT NOT NULL,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,retired_by BIGINT,retired_at TIMESTAMP,
 active_guard TINYINT GENERATED ALWAYS AS (CASE WHEN status='ACTIVE' THEN 1 ELSE NULL END),
 CONSTRAINT uk_test_cost_classification_override UNIQUE(tenant_id,source_type,source_id,source_item_id,active_guard));

CREATE TABLE IF NOT EXISTS cost_classification_snapshot (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,source_type VARCHAR(64) NOT NULL,source_id BIGINT NOT NULL,
 source_item_id BIGINT DEFAULT 0 NOT NULL,project_id BIGINT NOT NULL,original_cost_subject_id BIGINT,
 matched_cost_subject_id BIGINT NOT NULL,mapping_version_id BIGINT,assignment_rule_id BIGINT,
 classification_override_id BIGINT,classification_status VARCHAR(24) DEFAULT 'CLASSIFIED' NOT NULL,
 business_category VARCHAR(64) DEFAULT '*' NOT NULL,status VARCHAR(16) DEFAULT 'PENDING' NOT NULL,
 active_guard TINYINT GENERATED ALWAYS AS (CASE WHEN status='PENDING' THEN 1 ELSE NULL END),
 created_by BIGINT NOT NULL,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,posted_at TIMESTAMP,
 CONSTRAINT uk_test_cost_classification_snapshot UNIQUE(tenant_id,source_type,source_id,source_item_id,active_guard));

CREATE TABLE IF NOT EXISTS cost_unclassified_case (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,project_id BIGINT NOT NULL,
 source_type VARCHAR(64) NOT NULL,source_id BIGINT NOT NULL,source_item_id BIGINT DEFAULT 0 NOT NULL,
 business_category VARCHAR(64) DEFAULT '*' NOT NULL,original_cost_subject_id BIGINT,
 error_code VARCHAR(64) NOT NULL,error_message VARCHAR(500) NOT NULL,status VARCHAR(16) DEFAULT 'OPEN' NOT NULL,
 active_guard TINYINT GENERATED ALWAYS AS(CASE WHEN status='OPEN' THEN 1 ELSE NULL END),
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 resolved_at TIMESTAMP,
 CONSTRAINT uk_test_cost_unclassified_case UNIQUE(tenant_id,source_type,source_id,source_item_id,active_guard));

CREATE LOCAL TEMPORARY TABLE m96_test_workflow_type(type_code VARCHAR(64) PRIMARY KEY);
INSERT INTO m96_test_workflow_type VALUES
 ('COST_RULE_PLAN'),('COST_PROJECT_CONFIG'),('COST_RECALCULATION'),
 ('COST_POST_CLOSE_ADJUSTMENT'),('COST_REVERSAL');
INSERT INTO sys_type_registry
 (id,type_domain,type_code,owner_module,contract_version,status,description,created_at,updated_at)
SELECT 292960100000000000+ROW_NUMBER() OVER(ORDER BY type_code),'WORKFLOW_BUSINESS_TYPE',type_code,
 'cost','2.0','ACTIVE','测试链成本治理工作流',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
FROM m96_test_workflow_type source
WHERE NOT EXISTS(SELECT 1 FROM sys_type_registry existing
 WHERE existing.type_domain='WORKFLOW_BUSINESS_TYPE' AND existing.type_code=source.type_code);

CREATE LOCAL TEMPORARY TABLE m96_test_cost_source(type_code VARCHAR(64) PRIMARY KEY);
INSERT INTO m96_test_cost_source VALUES
 ('QUALITY_SAFETY_CONSEQUENCE'),('OVERHEAD_ALLOCATION_CLEARING'),('ACCOUNTING_ENTRY_LINE'),
 ('EXPENSE_APPLICATION'),('FINANCE_COST_ALLOCATION'),('FINANCE_COST_ALLOCATION_REVERSAL'),
 ('BID_COST_WRITE_OFF'),('MATERIAL_RETURN_REVERSAL'),('SUPPLIER_RETURN_REVERSAL'),
 ('COST_RECALCULATION_NEGATIVE'),('COST_RECALCULATION_POSITIVE'),('COST_RECALCULATION_REVERSAL');
INSERT INTO sys_type_registry
 (id,type_domain,type_code,owner_module,contract_version,status,description,created_at,updated_at)
SELECT 292960200000000000+ROW_NUMBER() OVER(ORDER BY type_code),'COST_SOURCE_TYPE',type_code,
 'cost','2.0','ACTIVE','测试链成本事实来源',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
FROM m96_test_cost_source source
WHERE NOT EXISTS(SELECT 1 FROM sys_type_registry existing
 WHERE existing.type_domain='COST_SOURCE_TYPE' AND existing.type_code=source.type_code);

-- Legacy Spring tests exercise authoritative cost writers without owning an M96 rule fixture.
-- Provide one explicit tenant-0 wildcard plan for those frozen fixtures; governance tests use
-- isolated tenants/databases or retire this plan before asserting unclassified behavior.
INSERT INTO cost_subject_mapping_version
 (id,tenant_id,version_code,version_name,status,effective_date,version,created_by,created_at,updated_by,updated_at,remark)
SELECT 292960300000000000,0,'M96-LEGACY-TEST','旧测试成本归类兼容方案','ACTIVE',DATE '2000-01-01',
 0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,'test fixture only'
WHERE NOT EXISTS(SELECT 1 FROM cost_subject_mapping_version
 WHERE tenant_id=0 AND version_code='M96-LEGACY-TEST');

CREATE LOCAL TEMPORARY TABLE m96_test_default_rule(
 source_type VARCHAR(64) PRIMARY KEY,cost_subject_id BIGINT NOT NULL);
INSERT INTO m96_test_default_rule VALUES
 ('MAT_RECEIPT',901002),('MAT_REQUISITION',901002),('SUB_MEASURE',901004),
 ('VAR_ORDER',901008),('CT_CHANGE',901008),('CT_CONTRACT',901008),
 ('QUALITY_SAFETY_CONSEQUENCE',901008),('OVERHEAD_ALLOCATION',901007),
 ('OVERHEAD_ALLOCATION_CLEARING',901007),('ACCOUNTING_ENTRY_LINE',901009),
 ('EXPENSE_APPLICATION',901009),('FINANCE_COST_ALLOCATION',901009),
 ('FINANCE_COST_ALLOCATION_REVERSAL',901009),('BID_COST',901009),
 ('BID_COST_WRITE_OFF',901009),('MATERIAL_RETURN',901002),
 ('MATERIAL_RETURN_REVERSAL',901002),('SUPPLIER_RETURN',901002),
 ('SUPPLIER_RETURN_REVERSAL',901002);
INSERT INTO cost_subject_assignment_rule
 (id,tenant_id,mapping_version_id,rule_code,source_type,business_category,project_id,
  cost_subject_id,priority,status,effective_from,version,created_by,created_at,updated_by,updated_at,remark)
SELECT 292960310000000000+ROW_NUMBER() OVER(ORDER BY source_type),0,292960300000000000,
 'M96-LEGACY-'||source_type,source_type,'*',NULL,cost_subject_id,1000,'ACTIVE',
 DATE '2000-01-01',0,1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,'test fixture only'
FROM m96_test_default_rule source
WHERE NOT EXISTS(SELECT 1 FROM cost_subject_assignment_rule existing
 WHERE existing.tenant_id=0 AND existing.rule_code='M96-LEGACY-'||source.source_type);

DROP TABLE m96_test_workflow_type;
DROP TABLE m96_test_cost_source;
DROP TABLE m96_test_default_rule;
