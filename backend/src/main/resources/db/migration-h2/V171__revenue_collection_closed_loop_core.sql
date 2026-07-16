ALTER TABLE contract_revenue ADD COLUMN IF NOT EXISTS approval_instance_id BIGINT;
ALTER TABLE contract_revenue ADD COLUMN IF NOT EXISTS formula_version VARCHAR(64) DEFAULT 'REVENUE_PROGRESS_V1' NOT NULL;
ALTER TABLE contract_revenue ADD COLUMN IF NOT EXISTS attachment_count INT DEFAULT 0 NOT NULL;
ALTER TABLE contract_revenue ADD COLUMN IF NOT EXISTS version INT DEFAULT 0 NOT NULL;

CREATE TABLE IF NOT EXISTS owner_settlement (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,project_id BIGINT NOT NULL,contract_id BIGINT NOT NULL,revenue_id BIGINT,
 settlement_code VARCHAR(64) NOT NULL,settlement_period VARCHAR(32) NOT NULL,settlement_date DATE NOT NULL,
 gross_amount DECIMAL(18,2) NOT NULL,tax_amount DECIMAL(18,2) DEFAULT 0 NOT NULL,retention_amount DECIMAL(18,2) DEFAULT 0 NOT NULL,
 net_receivable_amount DECIMAL(18,2) NOT NULL,due_date DATE NOT NULL,customer_id BIGINT NOT NULL,status VARCHAR(32) DEFAULT 'DRAFT' NOT NULL,
 approval_instance_id BIGINT,attachment_count INT DEFAULT 0 NOT NULL,formula_version VARCHAR(64) DEFAULT 'OWNER_SETTLEMENT_V1' NOT NULL,
 version INT DEFAULT 0 NOT NULL,created_by BIGINT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,updated_by BIGINT,
 updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,deleted_flag INT DEFAULT 0 NOT NULL,remark VARCHAR(500),
 CONSTRAINT fk_owner_settlement_project FOREIGN KEY(project_id) REFERENCES pm_project(id),
 CONSTRAINT fk_owner_settlement_contract FOREIGN KEY(contract_id) REFERENCES ct_contract(id),
 CONSTRAINT fk_owner_settlement_revenue FOREIGN KEY(revenue_id) REFERENCES contract_revenue(id),
 CONSTRAINT fk_owner_settlement_customer FOREIGN KEY(customer_id) REFERENCES md_partner(id));
CREATE UNIQUE INDEX IF NOT EXISTS uk_owner_settlement_code ON owner_settlement(tenant_id,settlement_code,deleted_flag);

CREATE TABLE IF NOT EXISTS account_receivable (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,project_id BIGINT NOT NULL,contract_id BIGINT NOT NULL,settlement_id BIGINT NOT NULL,
 customer_id BIGINT NOT NULL,receivable_type VARCHAR(32) NOT NULL,receivable_code VARCHAR(64) NOT NULL,original_amount DECIMAL(18,2) NOT NULL,
 collected_amount DECIMAL(18,2) DEFAULT 0 NOT NULL,credited_amount DECIMAL(18,2) DEFAULT 0 NOT NULL,outstanding_amount DECIMAL(18,2) NOT NULL,
 due_date DATE NOT NULL,status VARCHAR(32) DEFAULT 'OPEN' NOT NULL,version INT DEFAULT 0 NOT NULL,created_by BIGINT,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,updated_by BIGINT,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 deleted_flag INT DEFAULT 0 NOT NULL,remark VARCHAR(500),
 CONSTRAINT fk_receivable_project FOREIGN KEY(project_id) REFERENCES pm_project(id),
 CONSTRAINT fk_receivable_contract FOREIGN KEY(contract_id) REFERENCES ct_contract(id),
 CONSTRAINT fk_receivable_settlement FOREIGN KEY(settlement_id) REFERENCES owner_settlement(id),
 CONSTRAINT fk_receivable_customer FOREIGN KEY(customer_id) REFERENCES md_partner(id));
CREATE UNIQUE INDEX IF NOT EXISTS uk_receivable_code ON account_receivable(tenant_id,receivable_code,deleted_flag);
CREATE UNIQUE INDEX IF NOT EXISTS uk_receivable_settlement_type ON account_receivable(tenant_id,settlement_id,receivable_type,deleted_flag);

CREATE TABLE IF NOT EXISTS sales_invoice (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,project_id BIGINT NOT NULL,contract_id BIGINT NOT NULL,customer_id BIGINT NOT NULL,
 invoice_code VARCHAR(64),invoice_no VARCHAR(128) NOT NULL,invoice_type VARCHAR(32) NOT NULL,invoice_date DATE NOT NULL,
 amount_without_tax DECIMAL(18,2) NOT NULL,tax_amount DECIMAL(18,2) NOT NULL,total_amount DECIMAL(18,2) NOT NULL,
 allocated_amount DECIMAL(18,2) DEFAULT 0 NOT NULL,status VARCHAR(32) DEFAULT 'ISSUED' NOT NULL,
 verification_status VARCHAR(32) DEFAULT 'UNVERIFIED' NOT NULL,attachment_count INT DEFAULT 0 NOT NULL,version INT DEFAULT 0 NOT NULL,
 created_by BIGINT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,updated_by BIGINT,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 deleted_flag INT DEFAULT 0 NOT NULL,remark VARCHAR(500),
 CONSTRAINT fk_sales_invoice_project FOREIGN KEY(project_id) REFERENCES pm_project(id),
 CONSTRAINT fk_sales_invoice_contract FOREIGN KEY(contract_id) REFERENCES ct_contract(id),
 CONSTRAINT fk_sales_invoice_customer FOREIGN KEY(customer_id) REFERENCES md_partner(id));
CREATE UNIQUE INDEX IF NOT EXISTS uk_sales_invoice_no ON sales_invoice(tenant_id,invoice_no,deleted_flag);

CREATE TABLE IF NOT EXISTS sales_invoice_allocation (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,invoice_id BIGINT NOT NULL,receivable_id BIGINT NOT NULL,
 allocated_amount DECIMAL(18,2) NOT NULL,created_by BIGINT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 CONSTRAINT fk_sales_alloc_invoice FOREIGN KEY(invoice_id) REFERENCES sales_invoice(id),
 CONSTRAINT fk_sales_alloc_receivable FOREIGN KEY(receivable_id) REFERENCES account_receivable(id));
CREATE UNIQUE INDEX IF NOT EXISTS uk_sales_invoice_receivable ON sales_invoice_allocation(tenant_id,invoice_id,receivable_id);

CREATE TABLE IF NOT EXISTS collection_record (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,project_id BIGINT NOT NULL,contract_id BIGINT NOT NULL,customer_id BIGINT NOT NULL,
 fund_account_id BIGINT NOT NULL,collection_code VARCHAR(64) NOT NULL,external_txn_no VARCHAR(128) NOT NULL,collected_at TIMESTAMP NOT NULL,
 amount DECIMAL(18,2) NOT NULL,allocated_amount DECIMAL(18,2) DEFAULT 0 NOT NULL,unallocated_amount DECIMAL(18,2) NOT NULL,
 payer_name VARCHAR(200) NOT NULL,status VARCHAR(32) DEFAULT 'SUCCESS' NOT NULL,reversal_of_id BIGINT,reversed_at TIMESTAMP,
 failure_reason VARCHAR(500),attachment_count INT DEFAULT 0 NOT NULL,version INT DEFAULT 0 NOT NULL,created_by BIGINT,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,updated_by BIGINT,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 deleted_flag INT DEFAULT 0 NOT NULL,remark VARCHAR(500),
 CONSTRAINT fk_collection_project FOREIGN KEY(project_id) REFERENCES pm_project(id),
 CONSTRAINT fk_collection_contract FOREIGN KEY(contract_id) REFERENCES ct_contract(id),
 CONSTRAINT fk_collection_customer FOREIGN KEY(customer_id) REFERENCES md_partner(id),
 CONSTRAINT fk_collection_account FOREIGN KEY(fund_account_id) REFERENCES fund_account(id),
 CONSTRAINT fk_collection_reversal FOREIGN KEY(reversal_of_id) REFERENCES collection_record(id));
CREATE UNIQUE INDEX IF NOT EXISTS uk_collection_code ON collection_record(tenant_id,collection_code,deleted_flag);
CREATE UNIQUE INDEX IF NOT EXISTS uk_collection_external_txn ON collection_record(tenant_id,external_txn_no,deleted_flag);

CREATE TABLE IF NOT EXISTS collection_allocation (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,collection_id BIGINT NOT NULL,receivable_id BIGINT NOT NULL,
 allocated_amount DECIMAL(18,2) NOT NULL,allocation_type VARCHAR(32) DEFAULT 'COLLECTION' NOT NULL,created_by BIGINT,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 CONSTRAINT fk_collection_alloc_collection FOREIGN KEY(collection_id) REFERENCES collection_record(id),
 CONSTRAINT fk_collection_alloc_receivable FOREIGN KEY(receivable_id) REFERENCES account_receivable(id));
CREATE UNIQUE INDEX IF NOT EXISTS uk_collection_receivable ON collection_allocation(tenant_id,collection_id,receivable_id,allocation_type);

ALTER TABLE cash_journal_entry DROP CONSTRAINT IF EXISTS ck_cash_journal_source_type;
ALTER TABLE cash_journal_entry ADD COLUMN IF NOT EXISTS collection_record_id BIGINT;
ALTER TABLE cash_journal_entry ADD CONSTRAINT ck_cash_journal_source_type CHECK(source_type IN('MANUAL','PAY_RECORD','COLLECTION_RECORD','REVERSAL'));
CREATE UNIQUE INDEX IF NOT EXISTS uk_cash_journal_collection ON cash_journal_entry(tenant_id,collection_record_id,deleted_flag);
ALTER TABLE accounting_entry ADD COLUMN IF NOT EXISTS collection_record_id BIGINT;
CREATE UNIQUE INDEX IF NOT EXISTS uk_entry_collection ON accounting_entry(tenant_id,collection_record_id,entry_type,deleted_flag);

INSERT INTO wf_template(id,tenant_id,template_code,template_name,business_type,enabled,amount_min,amount_max,created_by,remark)
SELECT 50031,0,'TPL-OWNER-SETTLEMENT-001','业主结算审批流程','OWNER_SETTLEMENT',1,0.01,999999999999.99,1,'项目收入闭环：项目、商务、财务三级审批'
WHERE NOT EXISTS(SELECT 1 FROM wf_template WHERE id=50031);
INSERT INTO wf_template_node(id,tenant_id,template_id,node_code,node_name,node_order,node_type,approve_mode,approver_config,allow_transfer,allow_add_sign,timeout_hours)
SELECT 53101,0,50031,'N1','项目经理审批',1,'APPROVAL','SEQUENTIAL',JSON_OBJECT('type','USER','userId',1),1,1,48 WHERE NOT EXISTS(SELECT 1 FROM wf_template_node WHERE id=53101);
INSERT INTO wf_template_node(id,tenant_id,template_id,node_code,node_name,node_order,node_type,approve_mode,approver_config,allow_transfer,allow_add_sign,timeout_hours)
SELECT 53102,0,50031,'N2','商务经理审批',2,'APPROVAL','SEQUENTIAL',JSON_OBJECT('type','USER','userId',1),1,1,48 WHERE NOT EXISTS(SELECT 1 FROM wf_template_node WHERE id=53102);
INSERT INTO wf_template_node(id,tenant_id,template_id,node_code,node_name,node_order,node_type,approve_mode,approver_config,allow_transfer,allow_add_sign,timeout_hours)
SELECT 53103,0,50031,'N3','财务经理审批',3,'APPROVAL','SEQUENTIAL',JSON_OBJECT('type','USER','userId',1),1,1,48 WHERE NOT EXISTS(SELECT 1 FROM wf_template_node WHERE id=53103);

INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1070,0,2,'收入与回款','MENU','revenue','revenue/index','revenue:operations:query','fund',10,'ENABLE',1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1070);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1071,0,1070,'维护收入业务','BUTTON',NULL,NULL,'revenue:operations:maintain',NULL,1,'ENABLE',1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1071);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1072,0,1070,'提交业主结算','BUTTON',NULL,NULL,'revenue:settlement:submit',NULL,2,'ENABLE',1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1072);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1073,0,1070,'回款冲销','BUTTON',NULL,NULL,'revenue:collection:reverse',NULL,3,'ENABLE',1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1073);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1074,0,1070,'收入审计导出','BUTTON',NULL,NULL,'revenue:audit:export',NULL,4,'ENABLE',1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1074);
INSERT INTO sys_role_menu(id,role_id,menu_id)
SELECT 171000000+r.id*10000+m.id,r.id,m.id FROM sys_role r JOIN sys_menu m ON m.id BETWEEN 1070 AND 1074
WHERE r.role_code IN('SUPER_ADMIN','ADMIN','PROJECT_MANAGER','COST_MANAGER','FINANCE','AUDITOR') AND r.deleted_flag=0
AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.id AND rm.menu_id=m.id);
