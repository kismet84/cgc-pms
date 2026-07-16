CREATE TABLE qs_inspection_plan (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,project_id BIGINT NOT NULL,plan_code VARCHAR(64) NOT NULL,
 plan_name VARCHAR(200) NOT NULL,inspection_type VARCHAR(16) NOT NULL,frequency_type VARCHAR(16) NOT NULL,start_date DATE NOT NULL,
 end_date DATE NOT NULL,owner_user_id BIGINT NOT NULL,status VARCHAR(16) DEFAULT 'DRAFT' NOT NULL,activated_by BIGINT,activated_at TIMESTAMP,
 completed_by BIGINT,completed_at TIMESTAMP,version INT DEFAULT 0 NOT NULL,created_by BIGINT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 updated_by BIGINT,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,deleted_flag INT DEFAULT 0 NOT NULL,remark VARCHAR(500),
 FOREIGN KEY(project_id) REFERENCES pm_project(id));
CREATE UNIQUE INDEX uk_qs_plan_code ON qs_inspection_plan(tenant_id,project_id,plan_code,deleted_flag);
CREATE INDEX idx_qs_plan_project_status ON qs_inspection_plan(tenant_id,project_id,status,start_date);

CREATE TABLE qs_inspection_record (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,plan_id BIGINT NOT NULL,project_id BIGINT NOT NULL,inspection_code VARCHAR(64) NOT NULL,
 inspection_date DATE NOT NULL,location VARCHAR(200) NOT NULL,inspector_user_id BIGINT NOT NULL,conclusion VARCHAR(16) DEFAULT 'PENDING' NOT NULL,
 summary VARCHAR(1000) NOT NULL,status VARCHAR(16) DEFAULT 'DRAFT' NOT NULL,submitted_by BIGINT,submitted_at TIMESTAMP,version INT DEFAULT 0 NOT NULL,
 created_by BIGINT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,updated_by BIGINT,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 deleted_flag INT DEFAULT 0 NOT NULL,remark VARCHAR(500),FOREIGN KEY(plan_id) REFERENCES qs_inspection_plan(id),FOREIGN KEY(project_id) REFERENCES pm_project(id));
CREATE UNIQUE INDEX uk_qs_inspection_code ON qs_inspection_record(tenant_id,project_id,inspection_code,deleted_flag);
CREATE INDEX idx_qs_inspection_plan ON qs_inspection_record(tenant_id,plan_id,inspection_date,status);

CREATE TABLE qs_issue (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,plan_id BIGINT NOT NULL,inspection_id BIGINT NOT NULL,project_id BIGINT NOT NULL,
 issue_code VARCHAR(64) NOT NULL,issue_type VARCHAR(16) NOT NULL,category VARCHAR(100) NOT NULL,severity VARCHAR(16) NOT NULL,title VARCHAR(200) NOT NULL,
 description VARCHAR(2000) NOT NULL,responsible_kind VARCHAR(16) NOT NULL,responsible_partner_id BIGINT,responsible_user_id BIGINT NOT NULL,
 due_date DATE NOT NULL,status VARCHAR(32) DEFAULT 'OPEN' NOT NULL,closed_by BIGINT,closed_at TIMESTAMP,version INT DEFAULT 0 NOT NULL,
 created_by BIGINT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,updated_by BIGINT,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 deleted_flag INT DEFAULT 0 NOT NULL,remark VARCHAR(500),FOREIGN KEY(plan_id) REFERENCES qs_inspection_plan(id),
 FOREIGN KEY(inspection_id) REFERENCES qs_inspection_record(id),FOREIGN KEY(project_id) REFERENCES pm_project(id),FOREIGN KEY(responsible_partner_id) REFERENCES md_partner(id));
CREATE UNIQUE INDEX uk_qs_issue_code ON qs_issue(tenant_id,project_id,issue_code,deleted_flag);
CREATE INDEX idx_qs_issue_record ON qs_issue(tenant_id,inspection_id,status);
CREATE INDEX idx_qs_issue_responsible ON qs_issue(tenant_id,responsible_user_id,due_date,status);
CREATE INDEX idx_qs_issue_partner ON qs_issue(tenant_id,responsible_partner_id,status);

CREATE TABLE qs_rectification (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,issue_id BIGINT NOT NULL,project_id BIGINT NOT NULL,round_no INT NOT NULL,
 action_description VARCHAR(2000) NOT NULL,responsible_user_id BIGINT NOT NULL,planned_complete_date DATE NOT NULL,actual_completed_at TIMESTAMP,
 status VARCHAR(16) DEFAULT 'DRAFT' NOT NULL,submitted_by BIGINT,submitted_at TIMESTAMP,reinspection_comment VARCHAR(1000),reinspected_by BIGINT,
 reinspected_at TIMESTAMP,version INT DEFAULT 0 NOT NULL,created_by BIGINT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 updated_by BIGINT,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,deleted_flag INT DEFAULT 0 NOT NULL,remark VARCHAR(500),
 FOREIGN KEY(issue_id) REFERENCES qs_issue(id),FOREIGN KEY(project_id) REFERENCES pm_project(id));
CREATE UNIQUE INDEX uk_qs_rectification_round ON qs_rectification(tenant_id,issue_id,round_no,deleted_flag);
CREATE INDEX idx_qs_rectification_issue ON qs_rectification(tenant_id,issue_id,status);

CREATE TABLE qs_consequence (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,issue_id BIGINT NOT NULL,project_id BIGINT NOT NULL,partner_id BIGINT NOT NULL,
 contract_id BIGINT,consequence_code VARCHAR(64) NOT NULL,decision_type VARCHAR(20) NOT NULL,fine_amount DECIMAL(18,2) DEFAULT 0 NOT NULL,
 rework_cost_amount DECIMAL(18,2) DEFAULT 0 NOT NULL,evaluation_score DECIMAL(5,2) NOT NULL,evaluation_comment VARCHAR(1000) NOT NULL,
 status VARCHAR(16) DEFAULT 'DRAFT' NOT NULL,cost_item_id BIGINT,evaluation_id BIGINT,posted_by BIGINT,posted_at TIMESTAMP,version INT DEFAULT 0 NOT NULL,
 created_by BIGINT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,updated_by BIGINT,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 deleted_flag INT DEFAULT 0 NOT NULL,remark VARCHAR(500),FOREIGN KEY(issue_id) REFERENCES qs_issue(id),FOREIGN KEY(project_id) REFERENCES pm_project(id),
 FOREIGN KEY(partner_id) REFERENCES md_partner(id),FOREIGN KEY(contract_id) REFERENCES ct_contract(id),FOREIGN KEY(cost_item_id) REFERENCES cost_item(id));
CREATE UNIQUE INDEX uk_qs_consequence_issue ON qs_consequence(tenant_id,issue_id,deleted_flag);
CREATE UNIQUE INDEX uk_qs_consequence_code ON qs_consequence(tenant_id,project_id,consequence_code,deleted_flag);
CREATE INDEX idx_qs_consequence_partner ON qs_consequence(tenant_id,partner_id,status);

CREATE TABLE qs_partner_evaluation (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,consequence_id BIGINT NOT NULL,issue_id BIGINT NOT NULL,project_id BIGINT NOT NULL,
 partner_id BIGINT NOT NULL,evaluation_type VARCHAR(16) NOT NULL,score DECIMAL(5,2) NOT NULL,evaluation_comment VARCHAR(1000) NOT NULL,
 evaluated_by BIGINT NOT NULL,evaluated_at TIMESTAMP NOT NULL,created_by BIGINT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 deleted_flag INT DEFAULT 0 NOT NULL,remark VARCHAR(500),FOREIGN KEY(consequence_id) REFERENCES qs_consequence(id),FOREIGN KEY(issue_id) REFERENCES qs_issue(id),
 FOREIGN KEY(project_id) REFERENCES pm_project(id),FOREIGN KEY(partner_id) REFERENCES md_partner(id));
CREATE UNIQUE INDEX uk_qs_partner_evaluation ON qs_partner_evaluation(tenant_id,consequence_id,deleted_flag);
CREATE INDEX idx_qs_partner_score ON qs_partner_evaluation(tenant_id,partner_id,evaluation_type,evaluated_at);
ALTER TABLE qs_consequence ADD CONSTRAINT fk_qs_consequence_evaluation_188 FOREIGN KEY(evaluation_id) REFERENCES qs_partner_evaluation(id);

INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1098,0,2,'质量安全整改','MENU','quality-safety','quality-safety/index','quality:safety:query','safety-certificate',9,'ENABLE',1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1098);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1099,0,1098,'维护检查计划','BUTTON',NULL,NULL,'quality:safety:plan:maintain',NULL,1,'ENABLE',1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1099);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1100,0,1098,'维护检查记录与问题','BUTTON',NULL,NULL,'quality:safety:inspection:maintain',NULL,2,'ENABLE',1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1100);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1101,0,1098,'提交整改','BUTTON',NULL,NULL,'quality:safety:rectify',NULL,3,'ENABLE',1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1101);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1102,0,1098,'复验与关闭','BUTTON',NULL,NULL,'quality:safety:reinspect',NULL,4,'ENABLE',1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1102);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1103,0,1098,'处罚成本与评价','BUTTON',NULL,NULL,'quality:safety:consequence',NULL,5,'ENABLE',1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1103);
INSERT INTO sys_role_menu(id,role_id,menu_id)
SELECT 188000000+r.id*10000+m.id,r.id,m.id FROM sys_role r JOIN sys_menu m ON m.id BETWEEN 1098 AND 1103
WHERE r.role_code IN ('SUPER_ADMIN','ADMIN','PROJECT_MANAGER','COST_MANAGER','AUDITOR') AND r.deleted_flag=0
AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.id AND rm.menu_id=m.id);
