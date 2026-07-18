CREATE TABLE IF NOT EXISTS project_schedule_plan (
 id BIGINT PRIMARY KEY, tenant_id BIGINT DEFAULT 0 NOT NULL, project_id BIGINT NOT NULL,
 plan_code VARCHAR(64) NOT NULL, plan_name VARCHAR(200) NOT NULL, plan_type VARCHAR(20) DEFAULT 'BASELINE' NOT NULL,
 version_no INT NOT NULL, parent_plan_id BIGINT, corrective_action_id BIGINT, planned_start_date DATE NOT NULL,
 planned_end_date DATE NOT NULL, status VARCHAR(20) DEFAULT 'DRAFT' NOT NULL, approval_instance_id BIGINT, activated_at TIMESTAMP,
 version INT DEFAULT 0 NOT NULL, created_by BIGINT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 updated_by BIGINT, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL, deleted_flag INT DEFAULT 0 NOT NULL, remark VARCHAR(500),
 CONSTRAINT fk_project_schedule_project FOREIGN KEY(project_id) REFERENCES pm_project(id),
 CONSTRAINT fk_project_schedule_parent FOREIGN KEY(parent_plan_id) REFERENCES project_schedule_plan(id),
 CONSTRAINT fk_project_schedule_approval FOREIGN KEY(approval_instance_id) REFERENCES wf_instance(id));
CREATE UNIQUE INDEX IF NOT EXISTS uk_project_schedule_code ON project_schedule_plan(tenant_id,project_id,plan_code,deleted_flag);
CREATE UNIQUE INDEX IF NOT EXISTS uk_project_schedule_version ON project_schedule_plan(tenant_id,project_id,version_no,deleted_flag);

CREATE TABLE IF NOT EXISTS project_wbs_task (
 id BIGINT PRIMARY KEY, tenant_id BIGINT DEFAULT 0 NOT NULL, project_id BIGINT NOT NULL, schedule_plan_id BIGINT NOT NULL,
 parent_task_id BIGINT, predecessor_task_id BIGINT, task_code VARCHAR(64) NOT NULL, task_name VARCHAR(200) NOT NULL,
 work_area VARCHAR(200), responsible_user_id BIGINT, planned_start_date DATE NOT NULL, planned_end_date DATE NOT NULL,
 weight_percent DECIMAL(7,4) NOT NULL, planned_quantity DECIMAL(18,4), unit VARCHAR(30), actual_start_date DATE,
 actual_end_date DATE, actual_quantity DECIMAL(18,4) DEFAULT 0 NOT NULL, actual_progress DECIMAL(7,4) DEFAULT 0 NOT NULL,
 status VARCHAR(20) DEFAULT 'NOT_STARTED' NOT NULL, sort_order INT DEFAULT 0 NOT NULL, version INT DEFAULT 0 NOT NULL,
 created_by BIGINT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL, updated_by BIGINT,
 updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL, deleted_flag INT DEFAULT 0 NOT NULL, remark VARCHAR(500),
 CONSTRAINT fk_project_wbs_project FOREIGN KEY(project_id) REFERENCES pm_project(id),
 CONSTRAINT fk_project_wbs_schedule FOREIGN KEY(schedule_plan_id) REFERENCES project_schedule_plan(id),
 CONSTRAINT fk_project_wbs_parent FOREIGN KEY(parent_task_id) REFERENCES project_wbs_task(id),
 CONSTRAINT fk_project_wbs_predecessor FOREIGN KEY(predecessor_task_id) REFERENCES project_wbs_task(id));
CREATE UNIQUE INDEX IF NOT EXISTS uk_project_wbs_code ON project_wbs_task(tenant_id,schedule_plan_id,task_code,deleted_flag);

CREATE TABLE IF NOT EXISTS project_period_plan (
 id BIGINT PRIMARY KEY, tenant_id BIGINT DEFAULT 0 NOT NULL, project_id BIGINT NOT NULL, schedule_plan_id BIGINT NOT NULL,
 parent_period_plan_id BIGINT, period_type VARCHAR(20) NOT NULL, period_code VARCHAR(64) NOT NULL,
 period_name VARCHAR(200) NOT NULL, start_date DATE NOT NULL, end_date DATE NOT NULL, status VARCHAR(20) DEFAULT 'DRAFT' NOT NULL,
 approval_instance_id BIGINT, version INT DEFAULT 0 NOT NULL, created_by BIGINT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 updated_by BIGINT, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL, deleted_flag INT DEFAULT 0 NOT NULL, remark VARCHAR(500),
 CONSTRAINT fk_project_period_project FOREIGN KEY(project_id) REFERENCES pm_project(id),
 CONSTRAINT fk_project_period_schedule FOREIGN KEY(schedule_plan_id) REFERENCES project_schedule_plan(id),
 CONSTRAINT fk_project_period_parent FOREIGN KEY(parent_period_plan_id) REFERENCES project_period_plan(id),
 CONSTRAINT fk_project_period_approval FOREIGN KEY(approval_instance_id) REFERENCES wf_instance(id));
CREATE UNIQUE INDEX IF NOT EXISTS uk_project_period_code ON project_period_plan(tenant_id,project_id,period_code,deleted_flag);

CREATE TABLE IF NOT EXISTS project_period_plan_item (
 id BIGINT PRIMARY KEY, tenant_id BIGINT DEFAULT 0 NOT NULL, period_plan_id BIGINT NOT NULL, wbs_task_id BIGINT NOT NULL,
 target_progress DECIMAL(7,4) NOT NULL, planned_quantity DECIMAL(18,4), created_by BIGINT,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL, updated_by BIGINT, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 CONSTRAINT fk_project_period_item_plan FOREIGN KEY(period_plan_id) REFERENCES project_period_plan(id),
 CONSTRAINT fk_project_period_item_task FOREIGN KEY(wbs_task_id) REFERENCES project_wbs_task(id));
CREATE UNIQUE INDEX IF NOT EXISTS uk_project_period_item ON project_period_plan_item(tenant_id,period_plan_id,wbs_task_id);

CREATE TABLE IF NOT EXISTS site_daily_progress (
 id BIGINT PRIMARY KEY, tenant_id BIGINT DEFAULT 0 NOT NULL, daily_log_id BIGINT NOT NULL, project_id BIGINT NOT NULL,
 schedule_plan_id BIGINT NOT NULL, weekly_plan_id BIGINT NOT NULL, wbs_task_id BIGINT NOT NULL,
 previous_progress DECIMAL(7,4) NOT NULL, current_progress DECIMAL(7,4) NOT NULL,
 completed_quantity DECIMAL(18,4) DEFAULT 0 NOT NULL, work_description VARCHAR(500) NOT NULL,
 created_by BIGINT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL, updated_by BIGINT,
 updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 CONSTRAINT fk_site_daily_progress_log FOREIGN KEY(daily_log_id) REFERENCES site_daily_log(id),
 CONSTRAINT fk_site_daily_progress_project FOREIGN KEY(project_id) REFERENCES pm_project(id),
 CONSTRAINT fk_site_daily_progress_schedule FOREIGN KEY(schedule_plan_id) REFERENCES project_schedule_plan(id),
 CONSTRAINT fk_site_daily_progress_week FOREIGN KEY(weekly_plan_id) REFERENCES project_period_plan(id),
 CONSTRAINT fk_site_daily_progress_task FOREIGN KEY(wbs_task_id) REFERENCES project_wbs_task(id));
CREATE UNIQUE INDEX IF NOT EXISTS uk_site_daily_progress ON site_daily_progress(tenant_id,daily_log_id,wbs_task_id);

CREATE TABLE IF NOT EXISTS project_progress_snapshot (
 id BIGINT PRIMARY KEY, tenant_id BIGINT DEFAULT 0 NOT NULL, project_id BIGINT NOT NULL, schedule_plan_id BIGINT NOT NULL,
 snapshot_date DATE NOT NULL, source_daily_log_id BIGINT, planned_progress DECIMAL(7,4) NOT NULL,
 actual_progress DECIMAL(7,4) NOT NULL, deviation_percent DECIMAL(7,4) NOT NULL,
 lagging_task_count INT DEFAULT 0 NOT NULL, status VARCHAR(20) NOT NULL,
 formula_version VARCHAR(40) DEFAULT 'SCHEDULE_PROGRESS_V1' NOT NULL, created_by BIGINT,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 CONSTRAINT fk_project_snapshot_project FOREIGN KEY(project_id) REFERENCES pm_project(id),
 CONSTRAINT fk_project_snapshot_schedule FOREIGN KEY(schedule_plan_id) REFERENCES project_schedule_plan(id),
 CONSTRAINT fk_project_snapshot_log FOREIGN KEY(source_daily_log_id) REFERENCES site_daily_log(id));
CREATE UNIQUE INDEX IF NOT EXISTS uk_project_progress_snapshot ON project_progress_snapshot(tenant_id,schedule_plan_id,snapshot_date);

CREATE TABLE IF NOT EXISTS project_corrective_action (
 id BIGINT PRIMARY KEY, tenant_id BIGINT DEFAULT 0 NOT NULL, project_id BIGINT NOT NULL, schedule_plan_id BIGINT NOT NULL,
 snapshot_id BIGINT NOT NULL, alert_id BIGINT, action_code VARCHAR(64) NOT NULL, reason VARCHAR(500) NOT NULL,
 action_plan VARCHAR(1000) NOT NULL, responsible_user_id BIGINT NOT NULL, due_date DATE NOT NULL,
 status VARCHAR(20) DEFAULT 'DRAFT' NOT NULL, approval_instance_id BIGINT, generated_revision_plan_id BIGINT,
 completed_at TIMESTAMP, version INT DEFAULT 0 NOT NULL, created_by BIGINT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 updated_by BIGINT, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL, deleted_flag INT DEFAULT 0 NOT NULL, remark VARCHAR(500),
 CONSTRAINT fk_project_corrective_project FOREIGN KEY(project_id) REFERENCES pm_project(id),
 CONSTRAINT fk_project_corrective_schedule FOREIGN KEY(schedule_plan_id) REFERENCES project_schedule_plan(id),
 CONSTRAINT fk_project_corrective_snapshot FOREIGN KEY(snapshot_id) REFERENCES project_progress_snapshot(id),
 CONSTRAINT fk_project_corrective_alert FOREIGN KEY(alert_id) REFERENCES alert_log(id),
 CONSTRAINT fk_project_corrective_approval FOREIGN KEY(approval_instance_id) REFERENCES wf_instance(id),
 CONSTRAINT fk_project_corrective_revision FOREIGN KEY(generated_revision_plan_id) REFERENCES project_schedule_plan(id));
CREATE UNIQUE INDEX IF NOT EXISTS uk_project_corrective_code ON project_corrective_action(tenant_id,project_id,action_code,deleted_flag);
CREATE UNIQUE INDEX IF NOT EXISTS uk_project_corrective_snapshot ON project_corrective_action(tenant_id,snapshot_id,deleted_flag);

ALTER TABLE project_schedule_plan ADD CONSTRAINT IF NOT EXISTS fk_project_schedule_corrective FOREIGN KEY(corrective_action_id) REFERENCES project_corrective_action(id);

INSERT INTO wf_template(id,tenant_id,template_code,template_name,business_type,enabled,created_by,remark)
SELECT 50033,0,'TPL-PROJECT-SCHEDULE-001','项目基线与修订计划审批','PROJECT_SCHEDULE',1,1,'项目计划闭环三级审批' WHERE NOT EXISTS(SELECT 1 FROM wf_template WHERE id=50033);
INSERT INTO wf_template(id,tenant_id,template_code,template_name,business_type,enabled,created_by,remark)
SELECT 50034,0,'TPL-PROJECT-PERIOD-PLAN-001','项目月周计划审批','PROJECT_PERIOD_PLAN',1,1,'项目月周计划两级审批' WHERE NOT EXISTS(SELECT 1 FROM wf_template WHERE id=50034);
INSERT INTO wf_template(id,tenant_id,template_code,template_name,business_type,enabled,created_by,remark)
SELECT 50035,0,'TPL-PROJECT-CORRECTIVE-001','项目进度纠偏审批','PROJECT_CORRECTIVE_ACTION',1,1,'项目纠偏三级审批' WHERE NOT EXISTS(SELECT 1 FROM wf_template WHERE id=50035);

INSERT INTO wf_template_node(id,tenant_id,template_id,node_code,node_name,node_order,node_type,approve_mode,approver_config,allow_transfer,allow_add_sign,timeout_hours)
SELECT 53301,0,50033,'N1','项目经理审批',1,'APPROVAL','SEQUENTIAL',JSON_OBJECT('type','USER','userId',1),1,1,48 WHERE NOT EXISTS(SELECT 1 FROM wf_template_node WHERE id=53301);
INSERT INTO wf_template_node(id,tenant_id,template_id,node_code,node_name,node_order,node_type,approve_mode,approver_config,allow_transfer,allow_add_sign,timeout_hours)
SELECT 53302,0,50033,'N2','工程经理审批',2,'APPROVAL','SEQUENTIAL',JSON_OBJECT('type','USER','userId',1),1,1,48 WHERE NOT EXISTS(SELECT 1 FROM wf_template_node WHERE id=53302);
INSERT INTO wf_template_node(id,tenant_id,template_id,node_code,node_name,node_order,node_type,approve_mode,approver_config,allow_transfer,allow_add_sign,timeout_hours)
SELECT 53303,0,50033,'N3','分管领导审批',3,'APPROVAL','SEQUENTIAL',JSON_OBJECT('type','USER','userId',1),1,1,72 WHERE NOT EXISTS(SELECT 1 FROM wf_template_node WHERE id=53303);
INSERT INTO wf_template_node(id,tenant_id,template_id,node_code,node_name,node_order,node_type,approve_mode,approver_config,allow_transfer,allow_add_sign,timeout_hours)
SELECT 53401,0,50034,'N1','项目经理审批',1,'APPROVAL','SEQUENTIAL',JSON_OBJECT('type','USER','userId',1),1,1,48 WHERE NOT EXISTS(SELECT 1 FROM wf_template_node WHERE id=53401);
INSERT INTO wf_template_node(id,tenant_id,template_id,node_code,node_name,node_order,node_type,approve_mode,approver_config,allow_transfer,allow_add_sign,timeout_hours)
SELECT 53402,0,50034,'N2','工程经理审批',2,'APPROVAL','SEQUENTIAL',JSON_OBJECT('type','USER','userId',1),1,1,48 WHERE NOT EXISTS(SELECT 1 FROM wf_template_node WHERE id=53402);
INSERT INTO wf_template_node(id,tenant_id,template_id,node_code,node_name,node_order,node_type,approve_mode,approver_config,allow_transfer,allow_add_sign,timeout_hours)
SELECT 53501,0,50035,'N1','项目经理审批',1,'APPROVAL','SEQUENTIAL',JSON_OBJECT('type','USER','userId',1),1,1,48 WHERE NOT EXISTS(SELECT 1 FROM wf_template_node WHERE id=53501);
INSERT INTO wf_template_node(id,tenant_id,template_id,node_code,node_name,node_order,node_type,approve_mode,approver_config,allow_transfer,allow_add_sign,timeout_hours)
SELECT 53502,0,50035,'N2','工程经理审批',2,'APPROVAL','SEQUENTIAL',JSON_OBJECT('type','USER','userId',1),1,1,48 WHERE NOT EXISTS(SELECT 1 FROM wf_template_node WHERE id=53502);
INSERT INTO wf_template_node(id,tenant_id,template_id,node_code,node_name,node_order,node_type,approve_mode,approver_config,allow_transfer,allow_add_sign,timeout_hours)
SELECT 53503,0,50035,'N3','分管领导审批',3,'APPROVAL','SEQUENTIAL',JSON_OBJECT('type','USER','userId',1),1,1,72 WHERE NOT EXISTS(SELECT 1 FROM wf_template_node WHERE id=53503);

INSERT INTO alert_rule_config(id,tenant_id,rule_type,alert_domain,alert_category,enabled,dedup_hours,threshold_ratio,severity_override,created_by,remark)
SELECT 185001,0,'PROJECT_PROGRESS_DELAY','SCHEDULE','PROGRESS_DELAY',1,24,0.0500,'HIGH',1,'计划实际偏差达到5个百分点时生成延期预警'
WHERE NOT EXISTS(SELECT 1 FROM alert_rule_config WHERE id=185001);

INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1085,0,2,'项目计划','MENU','project-schedule','project-schedule/index','schedule:query','schedule',8,'ENABLE',1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1085);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1086,0,1085,'维护项目计划','BUTTON',NULL,NULL,'schedule:maintain',NULL,1,'ENABLE',1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1086);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1087,0,1085,'提交计划审批','BUTTON',NULL,NULL,'schedule:submit',NULL,2,'ENABLE',1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1087);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1088,0,1085,'填报实际进度','BUTTON',NULL,NULL,'schedule:progress',NULL,3,'ENABLE',1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1088);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1089,0,1085,'发起纠偏审批','BUTTON',NULL,NULL,'schedule:correct',NULL,4,'ENABLE',1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1089);

INSERT INTO sys_role_menu(id,role_id,menu_id)
SELECT 185000000+r.id*10000+m.id,r.id,m.id FROM sys_role r JOIN sys_menu m ON m.id BETWEEN 1085 AND 1089
WHERE r.role_code IN ('SUPER_ADMIN','ADMIN','PROJECT_MANAGER','COST_MANAGER','AUDITOR') AND r.deleted_flag=0
AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.id AND rm.menu_id=m.id);
