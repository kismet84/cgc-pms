CREATE TABLE IF NOT EXISTS measurement_period (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,project_id BIGINT NOT NULL,contract_id BIGINT NOT NULL,
 period_code VARCHAR(32) NOT NULL,period_name VARCHAR(100) NOT NULL,start_date DATE NOT NULL,end_date DATE NOT NULL,cutoff_date DATE NOT NULL,
 status VARCHAR(20) DEFAULT 'OPEN' NOT NULL,version INT DEFAULT 0 NOT NULL,created_by BIGINT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 updated_by BIGINT,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,deleted_flag INT DEFAULT 0 NOT NULL,remark VARCHAR(500),
 CONSTRAINT fk_measurement_period_project FOREIGN KEY(project_id) REFERENCES pm_project(id),
 CONSTRAINT fk_measurement_period_contract FOREIGN KEY(contract_id) REFERENCES ct_contract(id));
CREATE UNIQUE INDEX IF NOT EXISTS uk_measurement_period ON measurement_period(tenant_id,contract_id,period_code,deleted_flag);

CREATE TABLE IF NOT EXISTS production_measurement (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,project_id BIGINT NOT NULL,contract_id BIGINT NOT NULL,period_id BIGINT NOT NULL,
 measure_code VARCHAR(64) NOT NULL,measure_date DATE NOT NULL,current_reported_amount DECIMAL(18,2) DEFAULT 0 NOT NULL,
 cumulative_reported_amount DECIMAL(18,2) DEFAULT 0 NOT NULL,status VARCHAR(32) DEFAULT 'DRAFT' NOT NULL,
 approval_status VARCHAR(32) DEFAULT 'DRAFT' NOT NULL,approval_instance_id BIGINT,attachment_count INT DEFAULT 0 NOT NULL,
 formula_version VARCHAR(64) DEFAULT 'PRODUCTION_MEASUREMENT_V1' NOT NULL,version INT DEFAULT 0 NOT NULL,
 created_by BIGINT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,updated_by BIGINT,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 deleted_flag INT DEFAULT 0 NOT NULL,remark VARCHAR(500),
 CONSTRAINT fk_production_measure_project FOREIGN KEY(project_id) REFERENCES pm_project(id),
 CONSTRAINT fk_production_measure_contract FOREIGN KEY(contract_id) REFERENCES ct_contract(id),
 CONSTRAINT fk_production_measure_period FOREIGN KEY(period_id) REFERENCES measurement_period(id),
 CONSTRAINT fk_production_measure_approval FOREIGN KEY(approval_instance_id) REFERENCES wf_instance(id));
CREATE UNIQUE INDEX IF NOT EXISTS uk_production_measure_code ON production_measurement(tenant_id,measure_code,deleted_flag);
CREATE UNIQUE INDEX IF NOT EXISTS uk_production_measure_period ON production_measurement(tenant_id,contract_id,period_id,deleted_flag);

CREATE TABLE IF NOT EXISTS production_measurement_line (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,measurement_id BIGINT NOT NULL,source_type VARCHAR(32) NOT NULL,
 contract_item_id BIGINT,contract_change_id BIGINT,item_code VARCHAR(64),item_name VARCHAR(200) NOT NULL,item_spec VARCHAR(300),unit VARCHAR(50),
 contract_quantity DECIMAL(18,4) NOT NULL,prior_approved_quantity DECIMAL(18,4) DEFAULT 0 NOT NULL,
 current_reported_quantity DECIMAL(18,4) NOT NULL,cumulative_reported_quantity DECIMAL(18,4) NOT NULL,unit_price DECIMAL(18,4) NOT NULL,
 current_reported_amount DECIMAL(18,2) NOT NULL,cumulative_reported_amount DECIMAL(18,2) NOT NULL,evidence_count INT DEFAULT 0 NOT NULL,
 sort_order INT DEFAULT 0 NOT NULL,created_by BIGINT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 CONSTRAINT fk_production_measure_line_header FOREIGN KEY(measurement_id) REFERENCES production_measurement(id),
 CONSTRAINT fk_production_measure_line_item FOREIGN KEY(contract_item_id) REFERENCES ct_contract_item(id),
 CONSTRAINT fk_production_measure_line_change FOREIGN KEY(contract_change_id) REFERENCES ct_contract_change(id));
CREATE UNIQUE INDEX IF NOT EXISTS uk_production_measure_item ON production_measurement_line(tenant_id,measurement_id,contract_item_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_production_measure_change ON production_measurement_line(tenant_id,measurement_id,contract_change_id);

CREATE TABLE IF NOT EXISTS owner_measurement_submission (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,project_id BIGINT NOT NULL,contract_id BIGINT NOT NULL,measurement_id BIGINT NOT NULL,
 submission_code VARCHAR(64) NOT NULL,revision_no INT NOT NULL,submitted_at TIMESTAMP NOT NULL,external_document_no VARCHAR(128),
 submitted_amount DECIMAL(18,2) NOT NULL,confirmed_amount DECIMAL(18,2) DEFAULT 0 NOT NULL,deducted_amount DECIMAL(18,2) DEFAULT 0 NOT NULL,
 status VARCHAR(32) DEFAULT 'SUBMITTED' NOT NULL,reviewer_name VARCHAR(100),review_comment VARCHAR(500),reviewed_at TIMESTAMP,
 attachment_count INT DEFAULT 0 NOT NULL,version INT DEFAULT 0 NOT NULL,created_by BIGINT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 updated_by BIGINT,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,deleted_flag INT DEFAULT 0 NOT NULL,remark VARCHAR(500),
 CONSTRAINT fk_owner_measure_submission_project FOREIGN KEY(project_id) REFERENCES pm_project(id),
 CONSTRAINT fk_owner_measure_submission_contract FOREIGN KEY(contract_id) REFERENCES ct_contract(id),
 CONSTRAINT fk_owner_measure_submission_measure FOREIGN KEY(measurement_id) REFERENCES production_measurement(id));
CREATE UNIQUE INDEX IF NOT EXISTS uk_owner_measure_submission_code ON owner_measurement_submission(tenant_id,submission_code,deleted_flag);
CREATE UNIQUE INDEX IF NOT EXISTS uk_owner_measure_revision ON owner_measurement_submission(tenant_id,measurement_id,revision_no,deleted_flag);

CREATE TABLE IF NOT EXISTS owner_measurement_review_line (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,submission_id BIGINT NOT NULL,measurement_line_id BIGINT NOT NULL,
 submitted_quantity DECIMAL(18,4) NOT NULL,submitted_amount DECIMAL(18,2) NOT NULL,confirmed_quantity DECIMAL(18,4),
 confirmed_amount DECIMAL(18,2),deducted_amount DECIMAL(18,2),deduction_reason VARCHAR(500),created_by BIGINT,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,updated_by BIGINT,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 CONSTRAINT fk_owner_review_submission FOREIGN KEY(submission_id) REFERENCES owner_measurement_submission(id),
 CONSTRAINT fk_owner_review_measure_line FOREIGN KEY(measurement_line_id) REFERENCES production_measurement_line(id));
CREATE UNIQUE INDEX IF NOT EXISTS uk_owner_review_measure_line ON owner_measurement_review_line(tenant_id,submission_id,measurement_line_id);

ALTER TABLE owner_settlement ADD COLUMN IF NOT EXISTS production_measurement_id BIGINT;
ALTER TABLE owner_settlement ADD COLUMN IF NOT EXISTS owner_submission_id BIGINT;
ALTER TABLE owner_settlement ADD COLUMN IF NOT EXISTS reported_amount DECIMAL(18,2) DEFAULT 0 NOT NULL;
ALTER TABLE owner_settlement ADD COLUMN IF NOT EXISTS deducted_amount DECIMAL(18,2) DEFAULT 0 NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_owner_settlement_submission ON owner_settlement(tenant_id,owner_submission_id,deleted_flag);

INSERT INTO wf_template(id,tenant_id,template_code,template_name,business_type,enabled,amount_min,amount_max,created_by,remark)
SELECT 50032,0,'TPL-PRODUCTION-MEASUREMENT-001','产值计量审批流程','PRODUCTION_MEASUREMENT',1,0.01,999999999999.99,1,'产值计量闭环：项目、商务、分管领导三级审批'
WHERE NOT EXISTS(SELECT 1 FROM wf_template WHERE id=50032);
INSERT INTO wf_template_node(id,tenant_id,template_id,node_code,node_name,node_order,node_type,approve_mode,approver_config,allow_transfer,allow_add_sign,timeout_hours)
SELECT 53201,0,50032,'N1','项目经理审批',1,'APPROVAL','SEQUENTIAL',JSON_OBJECT('type','USER','userId',1),1,1,48 WHERE NOT EXISTS(SELECT 1 FROM wf_template_node WHERE id=53201);
INSERT INTO wf_template_node(id,tenant_id,template_id,node_code,node_name,node_order,node_type,approve_mode,approver_config,allow_transfer,allow_add_sign,timeout_hours)
SELECT 53202,0,50032,'N2','商务经理审批',2,'APPROVAL','SEQUENTIAL',JSON_OBJECT('type','USER','userId',1),1,1,48 WHERE NOT EXISTS(SELECT 1 FROM wf_template_node WHERE id=53202);
INSERT INTO wf_template_node(id,tenant_id,template_id,node_code,node_name,node_order,node_type,approve_mode,approver_config,allow_transfer,allow_add_sign,timeout_hours)
SELECT 53203,0,50032,'N3','分管领导审批',3,'APPROVAL','SEQUENTIAL',JSON_OBJECT('type','USER','userId',1),1,1,48 WHERE NOT EXISTS(SELECT 1 FROM wf_template_node WHERE id=53203);

INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1080,0,2,'产值计量','MENU','production-measurement','production-measurement/index','measurement:query','calculator',9,'ENABLE',1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1080);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1081,0,1080,'维护计量','BUTTON',NULL,NULL,'measurement:maintain',NULL,1,'ENABLE',1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1081);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1082,0,1080,'提交内部审批','BUTTON',NULL,NULL,'measurement:submit',NULL,2,'ENABLE',1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1082);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1083,0,1080,'提交业主报量','BUTTON',NULL,NULL,'measurement:owner:submit',NULL,3,'ENABLE',1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1083);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1084,0,1080,'登记业主核定','BUTTON',NULL,NULL,'measurement:owner:review',NULL,4,'ENABLE',1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1084);
INSERT INTO sys_role_menu(id,role_id,menu_id)
SELECT 175000000+r.id*10000+m.id,r.id,m.id FROM sys_role r JOIN sys_menu m ON m.id BETWEEN 1080 AND 1084
WHERE r.role_code IN('SUPER_ADMIN','ADMIN','PROJECT_MANAGER','COST_MANAGER','FINANCE','AUDITOR') AND r.deleted_flag=0
AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.id AND rm.menu_id=m.id);
