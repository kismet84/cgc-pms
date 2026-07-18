CREATE TABLE sp_sourcing_event (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,project_id BIGINT NOT NULL,purchase_request_id BIGINT NOT NULL,
 sourcing_code VARCHAR(64) NOT NULL,sourcing_title VARCHAR(200) NOT NULL,sourcing_type VARCHAR(16) NOT NULL,deadline TIMESTAMP NOT NULL,
 currency_code VARCHAR(8) DEFAULT 'CNY' NOT NULL,status VARCHAR(20) DEFAULT 'DRAFT' NOT NULL,awarded_quote_id BIGINT,
 awarded_partner_id BIGINT,contract_id BIGINT,award_reason VARCHAR(1000),published_by BIGINT,published_at TIMESTAMP,
 awarded_by BIGINT,awarded_at TIMESTAMP,contracted_by BIGINT,contracted_at TIMESTAMP,version INT DEFAULT 0 NOT NULL,
 created_by BIGINT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,updated_by BIGINT,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 deleted_flag INT DEFAULT 0 NOT NULL,remark VARCHAR(500),FOREIGN KEY(project_id) REFERENCES pm_project(id),
 FOREIGN KEY(purchase_request_id) REFERENCES mat_purchase_request(id),FOREIGN KEY(awarded_partner_id) REFERENCES md_partner(id),
 FOREIGN KEY(contract_id) REFERENCES ct_contract(id));
CREATE UNIQUE INDEX uk_sp_sourcing_code ON sp_sourcing_event(tenant_id,sourcing_code,deleted_flag);
CREATE UNIQUE INDEX uk_sp_sourcing_request ON sp_sourcing_event(tenant_id,purchase_request_id,deleted_flag);
CREATE INDEX idx_sp_sourcing_project_status ON sp_sourcing_event(tenant_id,project_id,status,deadline);

CREATE TABLE sp_sourcing_supplier (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,sourcing_event_id BIGINT NOT NULL,partner_id BIGINT NOT NULL,
 invitation_status VARCHAR(20) DEFAULT 'PENDING' NOT NULL,invited_at TIMESTAMP,responded_at TIMESTAMP,disqualification_reason VARCHAR(500),
 created_by BIGINT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,updated_by BIGINT,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 deleted_flag INT DEFAULT 0 NOT NULL,remark VARCHAR(500),FOREIGN KEY(sourcing_event_id) REFERENCES sp_sourcing_event(id),FOREIGN KEY(partner_id) REFERENCES md_partner(id));
CREATE UNIQUE INDEX uk_sp_sourcing_supplier ON sp_sourcing_supplier(tenant_id,sourcing_event_id,partner_id,deleted_flag);
CREATE INDEX idx_sp_supplier_partner ON sp_sourcing_supplier(tenant_id,partner_id,invitation_status);

CREATE TABLE sp_supplier_quote (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,sourcing_event_id BIGINT NOT NULL,sourcing_supplier_id BIGINT NOT NULL,
 partner_id BIGINT NOT NULL,quote_code VARCHAR(64) NOT NULL,total_amount DECIMAL(18,2) NOT NULL,tax_rate DECIMAL(8,4) DEFAULT 0 NOT NULL,
 delivery_days INT NOT NULL,validity_date DATE NOT NULL,commercial_terms VARCHAR(2000) NOT NULL,status VARCHAR(16) DEFAULT 'DRAFT' NOT NULL,
 submitted_by BIGINT,submitted_at TIMESTAMP,version INT DEFAULT 0 NOT NULL,created_by BIGINT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 updated_by BIGINT,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,deleted_flag INT DEFAULT 0 NOT NULL,remark VARCHAR(500),
 FOREIGN KEY(sourcing_event_id) REFERENCES sp_sourcing_event(id),FOREIGN KEY(sourcing_supplier_id) REFERENCES sp_sourcing_supplier(id),
 FOREIGN KEY(partner_id) REFERENCES md_partner(id));
CREATE UNIQUE INDEX uk_sp_quote_supplier ON sp_supplier_quote(tenant_id,sourcing_event_id,partner_id,deleted_flag);
CREATE UNIQUE INDEX uk_sp_quote_code ON sp_supplier_quote(tenant_id,quote_code,deleted_flag);
CREATE INDEX idx_sp_quote_event_status ON sp_supplier_quote(tenant_id,sourcing_event_id,status,total_amount);

CREATE TABLE sp_bid_evaluation (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,sourcing_event_id BIGINT NOT NULL,quote_id BIGINT NOT NULL,partner_id BIGINT NOT NULL,
 commercial_score DECIMAL(5,2) NOT NULL,technical_score DECIMAL(5,2) NOT NULL,delivery_score DECIMAL(5,2) NOT NULL,
 quality_score DECIMAL(5,2) NOT NULL,total_score DECIMAL(5,2) NOT NULL,evaluation_comment VARCHAR(1000) NOT NULL,
 evaluated_by BIGINT NOT NULL,evaluated_at TIMESTAMP NOT NULL,created_by BIGINT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 updated_by BIGINT,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,deleted_flag INT DEFAULT 0 NOT NULL,remark VARCHAR(500),FOREIGN KEY(sourcing_event_id) REFERENCES sp_sourcing_event(id),
 FOREIGN KEY(quote_id) REFERENCES sp_supplier_quote(id),FOREIGN KEY(partner_id) REFERENCES md_partner(id));
CREATE UNIQUE INDEX uk_sp_bid_evaluation ON sp_bid_evaluation(tenant_id,quote_id,deleted_flag);
CREATE INDEX idx_sp_bid_event_score ON sp_bid_evaluation(tenant_id,sourcing_event_id,total_score);
ALTER TABLE sp_sourcing_event ADD CONSTRAINT fk_sp_sourcing_award_quote_189 FOREIGN KEY(awarded_quote_id) REFERENCES sp_supplier_quote(id);

CREATE TABLE sp_supplier_return (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,project_id BIGINT NOT NULL,partner_id BIGINT NOT NULL,
 contract_id BIGINT NOT NULL,purchase_order_id BIGINT NOT NULL,receipt_id BIGINT NOT NULL,return_code VARCHAR(64) NOT NULL,
 return_date DATE NOT NULL,return_quantity DECIMAL(18,4) NOT NULL,return_amount DECIMAL(18,2) NOT NULL,reason VARCHAR(1000) NOT NULL,
 status VARCHAR(16) DEFAULT 'DRAFT' NOT NULL,confirmed_by BIGINT,confirmed_at TIMESTAMP,version INT DEFAULT 0 NOT NULL,
 created_by BIGINT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,updated_by BIGINT,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 deleted_flag INT DEFAULT 0 NOT NULL,remark VARCHAR(500),FOREIGN KEY(project_id) REFERENCES pm_project(id),
 FOREIGN KEY(partner_id) REFERENCES md_partner(id),FOREIGN KEY(contract_id) REFERENCES ct_contract(id),
 FOREIGN KEY(purchase_order_id) REFERENCES mat_purchase_order(id),FOREIGN KEY(receipt_id) REFERENCES mat_receipt(id));
CREATE UNIQUE INDEX uk_sp_supplier_return_code ON sp_supplier_return(tenant_id,return_code,deleted_flag);
CREATE INDEX idx_sp_supplier_return_order ON sp_supplier_return(tenant_id,purchase_order_id,status,return_date);

CREATE TABLE sp_performance_evaluation (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,project_id BIGINT NOT NULL,partner_id BIGINT NOT NULL,contract_id BIGINT NOT NULL,
 purchase_order_id BIGINT NOT NULL,evaluation_code VARCHAR(64) NOT NULL,period_start DATE NOT NULL,period_end DATE NOT NULL,
 delivery_score DECIMAL(5,2) NOT NULL,quality_score DECIMAL(5,2) NOT NULL,service_score DECIMAL(5,2) NOT NULL,
 commercial_score DECIMAL(5,2) NOT NULL,total_score DECIMAL(5,2) NOT NULL,grade VARCHAR(8) NOT NULL,on_time_flag INT NOT NULL,
 approved_receipt_count INT DEFAULT 0 NOT NULL,unqualified_receipt_count INT DEFAULT 0 NOT NULL,return_count INT DEFAULT 0 NOT NULL,
 finalized_settlement_count INT DEFAULT 0 NOT NULL,quality_safety_fact_count INT DEFAULT 0 NOT NULL,quality_safety_average DECIMAL(5,2),
 evaluation_comment VARCHAR(1000) NOT NULL,recommend_blacklist INT DEFAULT 0 NOT NULL,status VARCHAR(16) DEFAULT 'DRAFT' NOT NULL,
 confirmed_by BIGINT,confirmed_at TIMESTAMP,version INT DEFAULT 0 NOT NULL,created_by BIGINT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 updated_by BIGINT,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,deleted_flag INT DEFAULT 0 NOT NULL,remark VARCHAR(500),
 FOREIGN KEY(project_id) REFERENCES pm_project(id),FOREIGN KEY(partner_id) REFERENCES md_partner(id),FOREIGN KEY(contract_id) REFERENCES ct_contract(id),
 FOREIGN KEY(purchase_order_id) REFERENCES mat_purchase_order(id));
CREATE UNIQUE INDEX uk_sp_performance_order ON sp_performance_evaluation(tenant_id,purchase_order_id,deleted_flag);
CREATE UNIQUE INDEX uk_sp_performance_code ON sp_performance_evaluation(tenant_id,evaluation_code,deleted_flag);
CREATE INDEX idx_sp_performance_partner ON sp_performance_evaluation(tenant_id,partner_id,status,period_end);

CREATE TABLE sp_blacklist_record (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,performance_evaluation_id BIGINT NOT NULL,partner_id BIGINT NOT NULL,
 project_id BIGINT NOT NULL,action_type VARCHAR(12) DEFAULT 'ADD' NOT NULL,reason VARCHAR(1000) NOT NULL,status VARCHAR(16) DEFAULT 'DRAFT' NOT NULL,
 submitted_by BIGINT,submitted_at TIMESTAMP,reviewed_by BIGINT,reviewed_at TIMESTAMP,review_comment VARCHAR(1000),version INT DEFAULT 0 NOT NULL,
 created_by BIGINT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,updated_by BIGINT,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 deleted_flag INT DEFAULT 0 NOT NULL,remark VARCHAR(500),FOREIGN KEY(performance_evaluation_id) REFERENCES sp_performance_evaluation(id),
 FOREIGN KEY(partner_id) REFERENCES md_partner(id),FOREIGN KEY(project_id) REFERENCES pm_project(id));
CREATE UNIQUE INDEX uk_sp_blacklist_evaluation ON sp_blacklist_record(tenant_id,performance_evaluation_id,deleted_flag);
CREATE INDEX idx_sp_blacklist_partner ON sp_blacklist_record(tenant_id,partner_id,status);

INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1104,0,2,'供应商招采履约','MENU','supplier-sourcing','supplier-sourcing/index','supplier:sourcing:query','team',10,'ENABLE',1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1104);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1105,0,1104,'维护询价招标','BUTTON',NULL,NULL,'supplier:sourcing:maintain',NULL,1,'ENABLE',1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1105);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1106,0,1104,'维护供应商报价','BUTTON',NULL,NULL,'supplier:sourcing:quote',NULL,2,'ENABLE',1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1106);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1107,0,1104,'执行比价评审','BUTTON',NULL,NULL,'supplier:sourcing:evaluate',NULL,3,'ENABLE',1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1107);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1108,0,1104,'执行定标与合同关联','BUTTON',NULL,NULL,'supplier:sourcing:award',NULL,4,'ENABLE',1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1108);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1109,0,1104,'确认履约评价','BUTTON',NULL,NULL,'supplier:performance:evaluate',NULL,5,'ENABLE',1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1109);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1110,0,1104,'审核供应商黑名单','BUTTON',NULL,NULL,'supplier:blacklist:review',NULL,6,'ENABLE',1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1110);
INSERT INTO sys_role_menu(id,role_id,menu_id)
SELECT 189000000+r.id*10000+m.id,r.id,m.id FROM sys_role r JOIN sys_menu m ON m.id BETWEEN 1104 AND 1110
WHERE r.role_code IN ('SUPER_ADMIN','ADMIN','PROJECT_MANAGER','PURCHASE_MANAGER','COST_MANAGER','AUDITOR') AND r.deleted_flag=0
AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.id AND rm.menu_id=m.id);
