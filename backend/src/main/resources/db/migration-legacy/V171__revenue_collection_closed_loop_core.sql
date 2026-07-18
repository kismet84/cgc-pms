ALTER TABLE contract_revenue
    ADD COLUMN approval_instance_id BIGINT NULL,
    ADD COLUMN formula_version VARCHAR(64) NOT NULL DEFAULT 'REVENUE_PROGRESS_V1',
    ADD COLUMN attachment_count INT NOT NULL DEFAULT 0,
    ADD COLUMN version INT NOT NULL DEFAULT 0;

ALTER TABLE contract_revenue
    ADD CONSTRAINT fk_contract_revenue_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_contract_revenue_contract FOREIGN KEY (contract_id) REFERENCES ct_contract(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_contract_revenue_approval FOREIGN KEY (approval_instance_id) REFERENCES wf_instance(id) ON DELETE RESTRICT;

CREATE TABLE owner_settlement (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL, contract_id BIGINT NOT NULL, revenue_id BIGINT NULL,
    settlement_code VARCHAR(64) NOT NULL, settlement_period VARCHAR(32) NOT NULL,
    settlement_date DATE NOT NULL, gross_amount DECIMAL(18,2) NOT NULL,
    tax_amount DECIMAL(18,2) NOT NULL DEFAULT 0, retention_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    net_receivable_amount DECIMAL(18,2) NOT NULL, due_date DATE NOT NULL,
    customer_id BIGINT NOT NULL, status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    approval_instance_id BIGINT NULL, attachment_count INT NOT NULL DEFAULT 0,
    formula_version VARCHAR(64) NOT NULL DEFAULT 'OWNER_SETTLEMENT_V1', version INT NOT NULL DEFAULT 0,
    created_by BIGINT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0, remark VARCHAR(500) NULL,
    PRIMARY KEY (id), UNIQUE KEY uk_owner_settlement_code (tenant_id,settlement_code,deleted_flag),
    KEY idx_owner_settlement_contract (tenant_id,contract_id,settlement_date),
    CONSTRAINT fk_owner_settlement_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON DELETE RESTRICT,
    CONSTRAINT fk_owner_settlement_contract FOREIGN KEY (contract_id) REFERENCES ct_contract(id) ON DELETE RESTRICT,
    CONSTRAINT fk_owner_settlement_revenue FOREIGN KEY (revenue_id) REFERENCES contract_revenue(id) ON DELETE RESTRICT,
    CONSTRAINT fk_owner_settlement_customer FOREIGN KEY (customer_id) REFERENCES md_partner(id) ON DELETE RESTRICT,
    CONSTRAINT fk_owner_settlement_approval FOREIGN KEY (approval_instance_id) REFERENCES wf_instance(id) ON DELETE RESTRICT,
    CONSTRAINT ck_owner_settlement_amount CHECK (gross_amount>0 AND tax_amount>=0 AND retention_amount>=0 AND net_receivable_amount=gross_amount-retention_amount)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE account_receivable (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL, contract_id BIGINT NOT NULL, settlement_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL, receivable_type VARCHAR(32) NOT NULL,
    receivable_code VARCHAR(64) NOT NULL, original_amount DECIMAL(18,2) NOT NULL,
    collected_amount DECIMAL(18,2) NOT NULL DEFAULT 0, credited_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    outstanding_amount DECIMAL(18,2) NOT NULL, due_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN', version INT NOT NULL DEFAULT 0,
    created_by BIGINT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0, remark VARCHAR(500) NULL,
    PRIMARY KEY (id), UNIQUE KEY uk_receivable_code (tenant_id,receivable_code,deleted_flag),
    UNIQUE KEY uk_receivable_settlement_type (tenant_id,settlement_id,receivable_type,deleted_flag),
    KEY idx_receivable_due (tenant_id,status,due_date),
    CONSTRAINT fk_receivable_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON DELETE RESTRICT,
    CONSTRAINT fk_receivable_contract FOREIGN KEY (contract_id) REFERENCES ct_contract(id) ON DELETE RESTRICT,
    CONSTRAINT fk_receivable_settlement FOREIGN KEY (settlement_id) REFERENCES owner_settlement(id) ON DELETE RESTRICT,
    CONSTRAINT fk_receivable_customer FOREIGN KEY (customer_id) REFERENCES md_partner(id) ON DELETE RESTRICT,
    CONSTRAINT ck_receivable_amount CHECK (original_amount>0 AND collected_amount>=0 AND credited_amount>=0 AND outstanding_amount>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sales_invoice (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL, contract_id BIGINT NOT NULL, customer_id BIGINT NOT NULL,
    invoice_code VARCHAR(64) NULL, invoice_no VARCHAR(128) NOT NULL, invoice_type VARCHAR(32) NOT NULL,
    invoice_date DATE NOT NULL, amount_without_tax DECIMAL(18,2) NOT NULL,
    tax_amount DECIMAL(18,2) NOT NULL, total_amount DECIMAL(18,2) NOT NULL,
    allocated_amount DECIMAL(18,2) NOT NULL DEFAULT 0, status VARCHAR(32) NOT NULL DEFAULT 'ISSUED',
    verification_status VARCHAR(32) NOT NULL DEFAULT 'UNVERIFIED', attachment_count INT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0, created_by BIGINT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0, remark VARCHAR(500) NULL,
    PRIMARY KEY (id), UNIQUE KEY uk_sales_invoice_no (tenant_id,invoice_no,deleted_flag),
    KEY idx_sales_invoice_contract (tenant_id,contract_id,invoice_date),
    CONSTRAINT fk_sales_invoice_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON DELETE RESTRICT,
    CONSTRAINT fk_sales_invoice_contract FOREIGN KEY (contract_id) REFERENCES ct_contract(id) ON DELETE RESTRICT,
    CONSTRAINT fk_sales_invoice_customer FOREIGN KEY (customer_id) REFERENCES md_partner(id) ON DELETE RESTRICT,
    CONSTRAINT ck_sales_invoice_amount CHECK (amount_without_tax>=0 AND tax_amount>=0 AND total_amount=amount_without_tax+tax_amount AND allocated_amount>=0 AND allocated_amount<=total_amount)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sales_invoice_allocation (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0,
    invoice_id BIGINT NOT NULL, receivable_id BIGINT NOT NULL, allocated_amount DECIMAL(18,2) NOT NULL,
    created_by BIGINT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE KEY uk_sales_invoice_receivable (tenant_id,invoice_id,receivable_id),
    CONSTRAINT fk_sales_alloc_invoice FOREIGN KEY (invoice_id) REFERENCES sales_invoice(id) ON DELETE RESTRICT,
    CONSTRAINT fk_sales_alloc_receivable FOREIGN KEY (receivable_id) REFERENCES account_receivable(id) ON DELETE RESTRICT,
    CONSTRAINT ck_sales_alloc_amount CHECK (allocated_amount>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE collection_record (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL, contract_id BIGINT NOT NULL, customer_id BIGINT NOT NULL,
    fund_account_id BIGINT NOT NULL, collection_code VARCHAR(64) NOT NULL,
    external_txn_no VARCHAR(128) NOT NULL, collected_at DATETIME NOT NULL,
    amount DECIMAL(18,2) NOT NULL, allocated_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    unallocated_amount DECIMAL(18,2) NOT NULL, payer_name VARCHAR(200) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'SUCCESS', reversal_of_id BIGINT NULL,
    reversed_at DATETIME NULL, failure_reason VARCHAR(500) NULL, attachment_count INT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0, created_by BIGINT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0, remark VARCHAR(500) NULL,
    PRIMARY KEY (id), UNIQUE KEY uk_collection_code (tenant_id,collection_code,deleted_flag),
    UNIQUE KEY uk_collection_external_txn (tenant_id,external_txn_no,deleted_flag),
    KEY idx_collection_contract (tenant_id,contract_id,collected_at),
    CONSTRAINT fk_collection_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON DELETE RESTRICT,
    CONSTRAINT fk_collection_contract FOREIGN KEY (contract_id) REFERENCES ct_contract(id) ON DELETE RESTRICT,
    CONSTRAINT fk_collection_customer FOREIGN KEY (customer_id) REFERENCES md_partner(id) ON DELETE RESTRICT,
    CONSTRAINT fk_collection_account FOREIGN KEY (fund_account_id) REFERENCES fund_account(id) ON DELETE RESTRICT,
    CONSTRAINT fk_collection_reversal FOREIGN KEY (reversal_of_id) REFERENCES collection_record(id) ON DELETE RESTRICT,
    CONSTRAINT ck_collection_amount CHECK (amount>0 AND allocated_amount>=0 AND unallocated_amount>=0 AND amount=allocated_amount+unallocated_amount)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE collection_allocation (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0,
    collection_id BIGINT NOT NULL, receivable_id BIGINT NOT NULL, allocated_amount DECIMAL(18,2) NOT NULL,
    allocation_type VARCHAR(32) NOT NULL DEFAULT 'COLLECTION', created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE KEY uk_collection_receivable (tenant_id,collection_id,receivable_id,allocation_type),
    CONSTRAINT fk_collection_alloc_collection FOREIGN KEY (collection_id) REFERENCES collection_record(id) ON DELETE RESTRICT,
    CONSTRAINT fk_collection_alloc_receivable FOREIGN KEY (receivable_id) REFERENCES account_receivable(id) ON DELETE RESTRICT,
    CONSTRAINT ck_collection_alloc_amount CHECK (allocated_amount>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE cash_journal_entry DROP CHECK ck_cash_journal_source_type;
ALTER TABLE cash_journal_entry
    ADD COLUMN collection_record_id BIGINT NULL,
    ADD CONSTRAINT ck_cash_journal_source_type CHECK (source_type IN ('MANUAL','PAY_RECORD','COLLECTION_RECORD','REVERSAL')),
    ADD CONSTRAINT fk_cash_journal_collection FOREIGN KEY (collection_record_id) REFERENCES collection_record(id) ON DELETE RESTRICT;
CREATE UNIQUE INDEX uk_cash_journal_collection ON cash_journal_entry(tenant_id,collection_record_id,deleted_flag);

ALTER TABLE accounting_entry ADD COLUMN collection_record_id BIGINT NULL;
ALTER TABLE accounting_entry ADD CONSTRAINT fk_entry_collection FOREIGN KEY (collection_record_id) REFERENCES collection_record(id) ON DELETE RESTRICT;
CREATE UNIQUE INDEX uk_entry_collection ON accounting_entry(tenant_id,collection_record_id,entry_type,deleted_flag);

INSERT IGNORE INTO wf_template(id,tenant_id,template_code,template_name,business_type,enabled,amount_min,amount_max,created_by,remark)
VALUES(50031,0,'TPL-OWNER-SETTLEMENT-001','业主结算审批流程','OWNER_SETTLEMENT',1,0.01,999999999999.99,1,'项目收入闭环：项目、商务、财务三级审批');
INSERT IGNORE INTO wf_template_node(id,tenant_id,template_id,node_code,node_name,node_order,node_type,approve_mode,approver_config,allow_transfer,allow_add_sign,timeout_hours)
VALUES
(53101,0,50031,'N1','项目经理审批',1,'APPROVAL','SEQUENTIAL',JSON_OBJECT('type','USER','userId',1),1,1,48),
(53102,0,50031,'N2','商务经理审批',2,'APPROVAL','SEQUENTIAL',JSON_OBJECT('type','USER','userId',1),1,1,48),
(53103,0,50031,'N3','财务经理审批',3,'APPROVAL','SEQUENTIAL',JSON_OBJECT('type','USER','userId',1),1,1,48);

INSERT IGNORE INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
VALUES
(1070,0,2,'收入与回款','MENU','revenue','revenue/index','revenue:operations:query','fund',10,'ENABLE',1),
(1071,0,1070,'维护收入业务','BUTTON',NULL,NULL,'revenue:operations:maintain',NULL,1,'ENABLE',1),
(1072,0,1070,'提交业主结算','BUTTON',NULL,NULL,'revenue:settlement:submit',NULL,2,'ENABLE',1),
(1073,0,1070,'回款冲销','BUTTON',NULL,NULL,'revenue:collection:reverse',NULL,3,'ENABLE',1),
(1074,0,1070,'收入审计导出','BUTTON',NULL,NULL,'revenue:audit:export',NULL,4,'ENABLE',1);
INSERT IGNORE INTO sys_role_menu(id,role_id,menu_id)
SELECT 171000000+r.id*10000+m.id,r.id,m.id FROM sys_role r JOIN sys_menu m ON m.id BETWEEN 1070 AND 1074
WHERE r.role_code IN ('SUPER_ADMIN','ADMIN','PROJECT_MANAGER','COST_MANAGER','FINANCE','AUDITOR') AND r.deleted_flag=0;
