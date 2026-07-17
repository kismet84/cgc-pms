ALTER TABLE accounting_entry ADD COLUMN review_status VARCHAR(32) DEFAULT 'PENDING' NOT NULL;
ALTER TABLE accounting_entry ADD COLUMN reviewed_by BIGINT;
ALTER TABLE accounting_entry ADD COLUMN reviewed_at TIMESTAMP;
ALTER TABLE accounting_entry ADD COLUMN review_comment VARCHAR(500);
ALTER TABLE accounting_entry ADD COLUMN posted_by BIGINT;
ALTER TABLE accounting_entry ADD COLUMN period_id BIGINT;
ALTER TABLE accounting_entry ADD COLUMN adjustment_flag SMALLINT DEFAULT 0 NOT NULL;
ALTER TABLE accounting_entry ADD COLUMN original_entry_id BIGINT;
UPDATE accounting_entry SET review_status='APPROVED' WHERE entry_status IN ('POSTED','REVERSED');
CREATE INDEX idx_accounting_entry_period ON accounting_entry(tenant_id,entry_date,entry_status,review_status);

CREATE TABLE finance_period (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,period_code VARCHAR(16) NOT NULL,
 fiscal_year INT NOT NULL,fiscal_month INT NOT NULL,start_date DATE NOT NULL,end_date DATE NOT NULL,
 status VARCHAR(32) DEFAULT 'OPEN' NOT NULL,last_check_at TIMESTAMP,issue_count INT DEFAULT 0 NOT NULL,
 closed_by BIGINT,closed_at TIMESTAMP,close_comment VARCHAR(500),reopened_by BIGINT,reopened_at TIMESTAMP,reopen_reason VARCHAR(500),
 version INT DEFAULT 0 NOT NULL,created_by BIGINT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 updated_by BIGINT,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 CONSTRAINT uk_finance_period UNIQUE(tenant_id,fiscal_year,fiscal_month),
 CONSTRAINT ck_finance_period_month CHECK(fiscal_month BETWEEN 1 AND 12),
 CONSTRAINT ck_finance_period_status CHECK(status IN ('OPEN','CHECKING','CLOSED','REOPENED')));

CREATE TABLE finance_period_check (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,period_id BIGINT NOT NULL,check_type VARCHAR(64) NOT NULL,
 check_status VARCHAR(16) NOT NULL,issue_count INT DEFAULT 0 NOT NULL,detail_json CLOB,checked_by BIGINT,
 checked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,CONSTRAINT uk_finance_period_check UNIQUE(tenant_id,period_id,check_type),
 CONSTRAINT fk_finance_period_check_period FOREIGN KEY(period_id) REFERENCES finance_period(id),
 CONSTRAINT ck_finance_period_check_status CHECK(check_status IN ('PASS','FAIL')));

CREATE TABLE finance_account_reconciliation (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,period_id BIGINT NOT NULL,account_type VARCHAR(16) NOT NULL,
 expected_amount DECIMAL(18,2) NOT NULL,ledger_amount DECIMAL(18,2) NOT NULL,difference_amount DECIMAL(18,2) NOT NULL,
 status VARCHAR(16) NOT NULL,detail_json CLOB,reconciled_by BIGINT,reconciled_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 CONSTRAINT uk_finance_account_recon UNIQUE(tenant_id,period_id,account_type),
 CONSTRAINT fk_finance_account_recon_period FOREIGN KEY(period_id) REFERENCES finance_period(id),
 CONSTRAINT ck_finance_account_recon_type CHECK(account_type IN ('AR','AP')),
 CONSTRAINT ck_finance_account_recon_status CHECK(status IN ('MATCHED','EXCEPTION')));

CREATE TABLE finance_bank_reconciliation (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,period_id BIGINT NOT NULL,bank_receipt_id BIGINT NOT NULL,
 direction VARCHAR(8) NOT NULL,business_type VARCHAR(32),business_id BIGINT,cash_journal_id BIGINT,
 bank_amount DECIMAL(18,2) NOT NULL,business_amount DECIMAL(18,2) DEFAULT 0 NOT NULL,difference_amount DECIMAL(18,2) DEFAULT 0 NOT NULL,
 status VARCHAR(16) NOT NULL,match_method VARCHAR(16) DEFAULT 'AUTO' NOT NULL,resolved_by BIGINT,resolved_at TIMESTAMP,
 resolution_note VARCHAR(500),created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 CONSTRAINT uk_finance_bank_recon UNIQUE(tenant_id,period_id,bank_receipt_id),
 CONSTRAINT fk_finance_bank_recon_period FOREIGN KEY(period_id) REFERENCES finance_period(id),
 CONSTRAINT fk_finance_bank_recon_receipt FOREIGN KEY(bank_receipt_id) REFERENCES bank_receipt(id),
 CONSTRAINT fk_finance_bank_recon_journal FOREIGN KEY(cash_journal_id) REFERENCES cash_journal_entry(id),
 CONSTRAINT ck_finance_bank_recon_status CHECK(status IN ('MATCHED','EXCEPTION','RESOLVED')));

ALTER TABLE accounting_entry ADD CONSTRAINT fk_accounting_entry_period FOREIGN KEY(period_id) REFERENCES finance_period(id);
ALTER TABLE accounting_entry ADD CONSTRAINT fk_accounting_entry_original FOREIGN KEY(original_entry_id) REFERENCES accounting_entry(id);

INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1132,0,906,'财务月结','MENU','/financial-close','financial-close/index','finance:close:query','account-book',6,'ENABLE',1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1132);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1133,0,1132,'复核会计凭证','BUTTON',NULL,NULL,'accounting:review',NULL,1,'ENABLE',0 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1133);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1134,0,1132,'凭证过账','BUTTON',NULL,NULL,'accounting:post',NULL,2,'ENABLE',0 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1134);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1135,0,1132,'运行月结检查','BUTTON',NULL,NULL,'finance:close:check',NULL,3,'ENABLE',0 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1135);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1136,0,1132,'执行月结','BUTTON',NULL,NULL,'finance:close:close',NULL,4,'ENABLE',0 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1136);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1137,0,1132,'反结账','BUTTON',NULL,NULL,'finance:close:reopen',NULL,5,'ENABLE',0 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1137);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1138,0,1132,'处理对账差异','BUTTON',NULL,NULL,'finance:close:reconcile',NULL,6,'ENABLE',0 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1138);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1139,0,1132,'创建调整凭证','BUTTON',NULL,NULL,'accounting:adjustment:add',NULL,7,'ENABLE',0 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1139);

INSERT INTO sys_role_menu(id,role_id,menu_id)
SELECT 192000000+r.id*10000+m.id,r.id,m.id FROM sys_role r JOIN sys_menu m ON m.id BETWEEN 1132 AND 1139
WHERE r.role_code IN ('SUPER_ADMIN','ADMIN','FINANCE') AND r.deleted_flag=0
AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.id AND rm.menu_id=m.id);
INSERT INTO sys_role_menu(id,role_id,menu_id)
SELECT 192900000+r.id*10000+1132,r.id,1132 FROM sys_role r WHERE r.role_code='AUDITOR' AND r.deleted_flag=0
AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.id AND rm.menu_id=1132);
