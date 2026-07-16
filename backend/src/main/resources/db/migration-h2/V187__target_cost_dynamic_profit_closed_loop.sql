ALTER TABLE cost_target ADD COLUMN total_bid_cost_amount DECIMAL(18,2) NOT NULL DEFAULT 0;
ALTER TABLE cost_target ADD COLUMN total_responsibility_amount DECIMAL(18,2) NOT NULL DEFAULT 0;
ALTER TABLE cost_target ADD COLUMN approval_instance_id BIGINT NULL;
ALTER TABLE cost_target ADD COLUMN version INT NOT NULL DEFAULT 0;
UPDATE cost_target SET total_bid_cost_amount=CASE WHEN total_bid_cost_amount=0 THEN total_target_amount ELSE total_bid_cost_amount END,total_responsibility_amount=CASE WHEN total_responsibility_amount=0 THEN total_target_amount ELSE total_responsibility_amount END;

ALTER TABLE cost_target_item ADD COLUMN bid_cost_amount DECIMAL(18,2) NOT NULL DEFAULT 0;
ALTER TABLE cost_target_item ADD COLUMN responsibility_amount DECIMAL(18,2) NOT NULL DEFAULT 0;
ALTER TABLE cost_target_item ADD COLUMN responsible_user_id BIGINT NULL;
ALTER TABLE cost_target_item ADD COLUMN responsibility_unit VARCHAR(200) NULL;
ALTER TABLE cost_target_item ADD COLUMN sort_order INT NOT NULL DEFAULT 0;
UPDATE cost_target_item SET bid_cost_amount=CASE WHEN bid_cost_amount=0 THEN target_amount ELSE bid_cost_amount END,responsibility_amount=CASE WHEN responsibility_amount=0 THEN target_amount ELSE responsibility_amount END;

CREATE TABLE cost_forecast (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,project_id BIGINT NOT NULL,cost_target_id BIGINT NOT NULL,
 forecast_code VARCHAR(64) NOT NULL,forecast_name VARCHAR(200) NOT NULL,version_no INT NOT NULL,forecast_date DATE NOT NULL,
 bid_cost_amount DECIMAL(18,2) NOT NULL,target_cost_amount DECIMAL(18,2) NOT NULL,responsibility_amount DECIMAL(18,2) NOT NULL,
 committed_cost_amount DECIMAL(18,2) NOT NULL,actual_cost_amount DECIMAL(18,2) NOT NULL,estimated_remaining_amount DECIMAL(18,2) NOT NULL,
 forecast_at_completion_amount DECIMAL(18,2) NOT NULL,contract_income_amount DECIMAL(18,2) NOT NULL,forecast_profit_amount DECIMAL(18,2) NOT NULL,
 cost_variance_amount DECIMAL(18,2) NOT NULL,profit_margin DECIMAL(9,6) DEFAULT 0 NOT NULL,status VARCHAR(24) DEFAULT 'DRAFT' NOT NULL,
 formula_version VARCHAR(40) DEFAULT 'COST_EAC_V1' NOT NULL,confirmed_at TIMESTAMP,confirmed_by BIGINT,version INT DEFAULT 0 NOT NULL,
 created_by BIGINT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,updated_by BIGINT,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 deleted_flag INT DEFAULT 0 NOT NULL,remark VARCHAR(500),FOREIGN KEY(project_id) REFERENCES pm_project(id),FOREIGN KEY(cost_target_id) REFERENCES cost_target(id));
CREATE UNIQUE INDEX uk_cost_forecast_code ON cost_forecast(tenant_id,project_id,forecast_code,deleted_flag);
CREATE UNIQUE INDEX uk_cost_forecast_version ON cost_forecast(tenant_id,project_id,version_no,deleted_flag);
CREATE INDEX idx_cost_forecast_project_status ON cost_forecast(tenant_id,project_id,status,forecast_date);

CREATE TABLE cost_forecast_item (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,forecast_id BIGINT NOT NULL,project_id BIGINT NOT NULL,cost_subject_id BIGINT NOT NULL,
 bid_cost_amount DECIMAL(18,2) NOT NULL,target_cost_amount DECIMAL(18,2) NOT NULL,responsibility_amount DECIMAL(18,2) NOT NULL,
 committed_cost_amount DECIMAL(18,2) NOT NULL,actual_cost_amount DECIMAL(18,2) NOT NULL,estimated_remaining_amount DECIMAL(18,2) NOT NULL,
 forecast_at_completion_amount DECIMAL(18,2) NOT NULL,cost_variance_amount DECIMAL(18,2) NOT NULL,responsible_user_id BIGINT,
 responsibility_unit VARCHAR(200),created_by BIGINT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,remark VARCHAR(500),
 FOREIGN KEY(forecast_id) REFERENCES cost_forecast(id),FOREIGN KEY(project_id) REFERENCES pm_project(id),FOREIGN KEY(cost_subject_id) REFERENCES cost_subject(id));
CREATE UNIQUE INDEX uk_cost_forecast_item ON cost_forecast_item(tenant_id,forecast_id,cost_subject_id);

CREATE TABLE cost_corrective_action (
 id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 0 NOT NULL,project_id BIGINT NOT NULL,forecast_id BIGINT NOT NULL,
 action_code VARCHAR(64) NOT NULL,action_title VARCHAR(200) NOT NULL,root_cause VARCHAR(500) NOT NULL,action_plan VARCHAR(1000) NOT NULL,
 expected_saving_amount DECIMAL(18,2) NOT NULL,actual_saving_amount DECIMAL(18,2),responsible_user_id BIGINT NOT NULL,due_date DATE NOT NULL,
 status VARCHAR(24) DEFAULT 'DRAFT' NOT NULL,approval_instance_id BIGINT,result_description VARCHAR(1000),completed_at TIMESTAMP,
 version INT DEFAULT 0 NOT NULL,created_by BIGINT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,updated_by BIGINT,
 updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,deleted_flag INT DEFAULT 0 NOT NULL,remark VARCHAR(500),
 FOREIGN KEY(project_id) REFERENCES pm_project(id),FOREIGN KEY(forecast_id) REFERENCES cost_forecast(id),FOREIGN KEY(approval_instance_id) REFERENCES wf_instance(id));
CREATE UNIQUE INDEX uk_cost_corrective_code ON cost_corrective_action(tenant_id,project_id,action_code,deleted_flag);
CREATE INDEX idx_cost_corrective_forecast ON cost_corrective_action(tenant_id,forecast_id,status,due_date);

ALTER TABLE cost_target ADD CONSTRAINT fk_cost_target_project_187 FOREIGN KEY(project_id) REFERENCES pm_project(id);
ALTER TABLE cost_target ADD CONSTRAINT fk_cost_target_approval_187 FOREIGN KEY(approval_instance_id) REFERENCES wf_instance(id);
CREATE UNIQUE INDEX uk_cost_target_version_187 ON cost_target(tenant_id,project_id,version_no,deleted_flag);
ALTER TABLE cost_target_item ADD CONSTRAINT fk_cost_target_item_target_187 FOREIGN KEY(target_id) REFERENCES cost_target(id);
ALTER TABLE cost_target_item ADD CONSTRAINT fk_cost_target_item_subject_187 FOREIGN KEY(cost_subject_id) REFERENCES cost_subject(id);
CREATE UNIQUE INDEX uk_cost_target_item_subject_187 ON cost_target_item(tenant_id,target_id,cost_subject_id,deleted_flag);

ALTER TABLE cost_summary ADD COLUMN cost_forecast_id BIGINT NULL;
ALTER TABLE cost_summary ADD COLUMN responsibility_cost DECIMAL(18,2) NOT NULL DEFAULT 0;
ALTER TABLE cost_summary ADD COLUMN forecast_at_completion_cost DECIMAL(18,2) NOT NULL DEFAULT 0;
ALTER TABLE cost_summary ADD COLUMN forecast_profit DECIMAL(18,2) NOT NULL DEFAULT 0;
ALTER TABLE cost_summary ADD COLUMN profit_margin DECIMAL(9,6) NOT NULL DEFAULT 0;
ALTER TABLE cost_summary ADD CONSTRAINT fk_cost_summary_forecast_187 FOREIGN KEY(cost_forecast_id) REFERENCES cost_forecast(id);

INSERT INTO wf_template(id,tenant_id,template_code,template_name,business_type,enabled,created_by,remark)
SELECT 50036,0,'TPL-COST-CORRECTIVE-001','成本偏差纠偏审批','COST_CORRECTIVE_ACTION',1,1,'目标成本与动态利润闭环三级审批' WHERE NOT EXISTS(SELECT 1 FROM wf_template WHERE id=50036);
INSERT INTO wf_template_node(id,tenant_id,template_id,node_code,node_name,node_order,node_type,approve_mode,approver_config,allow_transfer,allow_add_sign,timeout_hours)
SELECT 53601,0,50036,'N1','项目经理审批',1,'APPROVAL','SEQUENTIAL',JSON_OBJECT('type','USER','userId',1),1,1,48 WHERE NOT EXISTS(SELECT 1 FROM wf_template_node WHERE id=53601);
INSERT INTO wf_template_node(id,tenant_id,template_id,node_code,node_name,node_order,node_type,approve_mode,approver_config,allow_transfer,allow_add_sign,timeout_hours)
SELECT 53602,0,50036,'N2','成本经理审批',2,'APPROVAL','SEQUENTIAL',JSON_OBJECT('type','USER','userId',1),1,1,48 WHERE NOT EXISTS(SELECT 1 FROM wf_template_node WHERE id=53602);
INSERT INTO wf_template_node(id,tenant_id,template_id,node_code,node_name,node_order,node_type,approve_mode,approver_config,allow_transfer,allow_add_sign,timeout_hours)
SELECT 53603,0,50036,'N3','分管领导审批',3,'APPROVAL','SEQUENTIAL',JSON_OBJECT('type','USER','userId',1),1,1,72 WHERE NOT EXISTS(SELECT 1 FROM wf_template_node WHERE id=53603);

INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1093,0,903,'动态利润控制','MENU','/cost/control','cost/control','cost:control:query','fund',5,'ENABLE',1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1093);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1094,0,1093,'维护完工预测','BUTTON',NULL,NULL,'cost:forecast:maintain',NULL,1,'ENABLE',1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1094);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1095,0,1093,'确认完工预测','BUTTON',NULL,NULL,'cost:forecast:confirm',NULL,2,'ENABLE',1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1095);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1096,0,1093,'维护纠偏措施','BUTTON',NULL,NULL,'cost:corrective:maintain',NULL,3,'ENABLE',1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1096);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1097,0,1093,'提交与关闭纠偏','BUTTON',NULL,NULL,'cost:corrective:submit',NULL,4,'ENABLE',1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1097);
INSERT INTO sys_role_menu(id,role_id,menu_id)
SELECT 187000000+r.id*10000+m.id,r.id,m.id FROM sys_role r JOIN sys_menu m ON m.id BETWEEN 1093 AND 1097
WHERE r.role_code IN ('SUPER_ADMIN','ADMIN','PROJECT_MANAGER','COST_MANAGER','FINANCE','AUDITOR') AND r.deleted_flag=0
AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.id AND rm.menu_id=m.id);
