ALTER TABLE owner_settlement ADD COLUMN settlement_type VARCHAR(32) NOT NULL DEFAULT 'PROGRESS';

CREATE TABLE project_closeout (
 id BIGINT PRIMARY KEY,tenant_id BIGINT NOT NULL DEFAULT 0,project_id BIGINT NOT NULL,closeout_code VARCHAR(64) NOT NULL,
 planned_completion_date DATE NOT NULL,actual_completion_date DATE,status VARCHAR(40) NOT NULL DEFAULT 'INITIATED',
 final_owner_settlement_id BIGINT,tail_collection_verified_at TIMESTAMP,closed_by BIGINT,closed_at TIMESTAMP,version INT NOT NULL DEFAULT 0,
 created_by BIGINT,created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_by BIGINT,updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 deleted_flag TINYINT NOT NULL DEFAULT 0,remark VARCHAR(500),
 CONSTRAINT uk_project_closeout_project UNIQUE(tenant_id,project_id,deleted_flag),
 CONSTRAINT uk_project_closeout_code UNIQUE(tenant_id,closeout_code,deleted_flag),
 CONSTRAINT uk_project_closeout_settlement UNIQUE(tenant_id,final_owner_settlement_id),
 CONSTRAINT fk_project_closeout_project FOREIGN KEY(project_id) REFERENCES pm_project(id),
 CONSTRAINT fk_project_closeout_settlement FOREIGN KEY(final_owner_settlement_id) REFERENCES owner_settlement(id));

CREATE TABLE closeout_section_acceptance (
 id BIGINT PRIMARY KEY,tenant_id BIGINT NOT NULL DEFAULT 0,closeout_id BIGINT NOT NULL,project_id BIGINT NOT NULL,wbs_task_id BIGINT NOT NULL,
 quality_inspection_id BIGINT NOT NULL,acceptance_code VARCHAR(64) NOT NULL,acceptance_name VARCHAR(200) NOT NULL,acceptance_date DATE NOT NULL,
 conclusion VARCHAR(32) NOT NULL,status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',confirmed_by BIGINT,confirmed_at TIMESTAMP,version INT NOT NULL DEFAULT 0,
 created_by BIGINT,created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_by BIGINT,updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 deleted_flag TINYINT NOT NULL DEFAULT 0,remark VARCHAR(500),
 CONSTRAINT uk_closeout_section_code UNIQUE(tenant_id,acceptance_code,deleted_flag),
 CONSTRAINT uk_closeout_section_wbs UNIQUE(tenant_id,closeout_id,wbs_task_id,deleted_flag),
 CONSTRAINT fk_closeout_section_closeout FOREIGN KEY(closeout_id) REFERENCES project_closeout(id),
 CONSTRAINT fk_closeout_section_project FOREIGN KEY(project_id) REFERENCES pm_project(id),
 CONSTRAINT fk_closeout_section_wbs FOREIGN KEY(wbs_task_id) REFERENCES project_wbs_task(id),
 CONSTRAINT fk_closeout_section_quality FOREIGN KEY(quality_inspection_id) REFERENCES qs_inspection_record(id));

CREATE TABLE closeout_final_acceptance (
 id BIGINT PRIMARY KEY,tenant_id BIGINT NOT NULL DEFAULT 0,closeout_id BIGINT NOT NULL,project_id BIGINT NOT NULL,
 acceptance_code VARCHAR(64) NOT NULL,acceptance_date DATE NOT NULL,organizer VARCHAR(200) NOT NULL,participant_summary VARCHAR(1000) NOT NULL,
 conclusion VARCHAR(32) NOT NULL,acceptance_summary VARCHAR(2000) NOT NULL,status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',approval_instance_id BIGINT,
 approved_by BIGINT,approved_at TIMESTAMP,version INT NOT NULL DEFAULT 0,created_by BIGINT,created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_by BIGINT,updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,deleted_flag TINYINT NOT NULL DEFAULT 0,remark VARCHAR(500),
 CONSTRAINT uk_closeout_final_closeout UNIQUE(tenant_id,closeout_id,deleted_flag),
 CONSTRAINT uk_closeout_final_code UNIQUE(tenant_id,acceptance_code,deleted_flag),
 CONSTRAINT fk_closeout_final_closeout FOREIGN KEY(closeout_id) REFERENCES project_closeout(id),
 CONSTRAINT fk_closeout_final_project FOREIGN KEY(project_id) REFERENCES pm_project(id),
 CONSTRAINT fk_closeout_final_approval FOREIGN KEY(approval_instance_id) REFERENCES wf_instance(id));

CREATE TABLE closeout_warranty (
 id BIGINT PRIMARY KEY,tenant_id BIGINT NOT NULL DEFAULT 0,closeout_id BIGINT NOT NULL,project_id BIGINT NOT NULL,contract_id BIGINT NOT NULL,
 receivable_id BIGINT NOT NULL,warranty_code VARCHAR(64) NOT NULL,warranty_amount DECIMAL(18,2) NOT NULL,warranty_start_date DATE NOT NULL,
 warranty_end_date DATE NOT NULL,responsible_user_id BIGINT NOT NULL,status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',released_by BIGINT,released_at TIMESTAMP,
 version INT NOT NULL DEFAULT 0,created_by BIGINT,created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_by BIGINT,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,deleted_flag TINYINT NOT NULL DEFAULT 0,remark VARCHAR(500),
 CONSTRAINT uk_closeout_warranty_code UNIQUE(tenant_id,warranty_code,deleted_flag),
 CONSTRAINT uk_closeout_warranty_receivable UNIQUE(tenant_id,receivable_id,deleted_flag),
 CONSTRAINT fk_closeout_warranty_closeout FOREIGN KEY(closeout_id) REFERENCES project_closeout(id),
 CONSTRAINT fk_closeout_warranty_project FOREIGN KEY(project_id) REFERENCES pm_project(id),
 CONSTRAINT fk_closeout_warranty_contract FOREIGN KEY(contract_id) REFERENCES ct_contract(id),
 CONSTRAINT fk_closeout_warranty_receivable FOREIGN KEY(receivable_id) REFERENCES account_receivable(id),
 CONSTRAINT ck_closeout_warranty_amount CHECK(warranty_amount>0),CONSTRAINT ck_closeout_warranty_dates CHECK(warranty_end_date>=warranty_start_date));

CREATE TABLE closeout_defect (
 id BIGINT PRIMARY KEY,tenant_id BIGINT NOT NULL DEFAULT 0,closeout_id BIGINT NOT NULL,project_id BIGINT NOT NULL,warranty_id BIGINT NOT NULL,
 defect_code VARCHAR(64) NOT NULL,defect_title VARCHAR(200) NOT NULL,defect_description VARCHAR(2000) NOT NULL,responsible_user_id BIGINT NOT NULL,
 rectification_deadline DATE NOT NULL,status VARCHAR(32) NOT NULL DEFAULT 'OPEN',rectification_content VARCHAR(2000),rectified_by BIGINT,
 rectified_at TIMESTAMP,verified_by BIGINT,verified_at TIMESTAMP,verification_comment VARCHAR(1000),version INT NOT NULL DEFAULT 0,
 created_by BIGINT,created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_by BIGINT,updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 deleted_flag TINYINT NOT NULL DEFAULT 0,remark VARCHAR(500),CONSTRAINT uk_closeout_defect_code UNIQUE(tenant_id,defect_code,deleted_flag),
 CONSTRAINT fk_closeout_defect_closeout FOREIGN KEY(closeout_id) REFERENCES project_closeout(id),
 CONSTRAINT fk_closeout_defect_project FOREIGN KEY(project_id) REFERENCES pm_project(id),
 CONSTRAINT fk_closeout_defect_warranty FOREIGN KEY(warranty_id) REFERENCES closeout_warranty(id));

CREATE TABLE closeout_archive_transfer (
 id BIGINT PRIMARY KEY,tenant_id BIGINT NOT NULL DEFAULT 0,closeout_id BIGINT NOT NULL,project_id BIGINT NOT NULL,transfer_code VARCHAR(64) NOT NULL,
 transfer_date DATE NOT NULL,recipient_organization VARCHAR(200) NOT NULL,recipient_name VARCHAR(100) NOT NULL,archive_location VARCHAR(300) NOT NULL,
 transfer_scope VARCHAR(2000) NOT NULL,status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',accepted_by BIGINT,accepted_at TIMESTAMP,version INT NOT NULL DEFAULT 0,
 created_by BIGINT,created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_by BIGINT,updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 deleted_flag TINYINT NOT NULL DEFAULT 0,remark VARCHAR(500),CONSTRAINT uk_closeout_archive_code UNIQUE(tenant_id,transfer_code,deleted_flag),
 CONSTRAINT uk_closeout_archive_closeout UNIQUE(tenant_id,closeout_id,deleted_flag),
 CONSTRAINT fk_closeout_archive_closeout FOREIGN KEY(closeout_id) REFERENCES project_closeout(id),
 CONSTRAINT fk_closeout_archive_project FOREIGN KEY(project_id) REFERENCES pm_project(id));

INSERT INTO wf_template(id,tenant_id,template_code,template_name,business_type,enabled,amount_min,amount_max,created_by,remark)
SELECT 50038,0,'TPL-PROJECT-FINAL-ACCEPTANCE-001','项目竣工验收审批流程','PROJECT_FINAL_ACCEPTANCE',1,0,999999999999.99,1,'竣工收尾闭环：项目、工程、公司三级确认'
WHERE NOT EXISTS(SELECT 1 FROM wf_template WHERE id=50038);
INSERT INTO wf_template_node(id,tenant_id,template_id,node_code,node_name,node_order,node_type,approve_mode,approver_config,allow_transfer,allow_add_sign,timeout_hours)
SELECT 53801,0,50038,'N1','项目经理确认',1,'APPROVAL','SEQUENTIAL',JSON_OBJECT('type','USER','userId',1),1,1,48
WHERE NOT EXISTS(SELECT 1 FROM wf_template_node WHERE id=53801);
INSERT INTO wf_template_node(id,tenant_id,template_id,node_code,node_name,node_order,node_type,approve_mode,approver_config,allow_transfer,allow_add_sign,timeout_hours)
SELECT 53802,0,50038,'N2','工程管理确认',2,'APPROVAL','SEQUENTIAL',JSON_OBJECT('type','USER','userId',1),1,1,48
WHERE NOT EXISTS(SELECT 1 FROM wf_template_node WHERE id=53802);
INSERT INTO wf_template_node(id,tenant_id,template_id,node_code,node_name,node_order,node_type,approve_mode,approver_config,allow_transfer,allow_add_sign,timeout_hours)
SELECT 53803,0,50038,'N3','公司竣工确认',3,'APPROVAL','SEQUENTIAL',JSON_OBJECT('type','USER','userId',1),1,1,72
WHERE NOT EXISTS(SELECT 1 FROM wf_template_node WHERE id=53803);

INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1121,0,2,'项目竣工收尾','MENU','project-closeout','project-closeout/index','closeout:query','check-circle',13,'ENABLE',1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1121);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1122,0,1121,'发起项目收尾','BUTTON',NULL,NULL,'closeout:initiate',NULL,1,'ENABLE',1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1122);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1123,0,1121,'维护分项验收','BUTTON',NULL,NULL,'closeout:section:maintain',NULL,2,'ENABLE',1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1123);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1124,0,1121,'提交竣工验收','BUTTON',NULL,NULL,'closeout:acceptance:submit',NULL,3,'ENABLE',1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1124);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1125,0,1121,'绑定竣工结算','BUTTON',NULL,NULL,'closeout:settlement:bind',NULL,4,'ENABLE',1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1125);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1126,0,1121,'确认尾款回收','BUTTON',NULL,NULL,'closeout:collection:verify',NULL,5,'ENABLE',1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1126);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1127,0,1121,'维护质保责任','BUTTON',NULL,NULL,'closeout:warranty:maintain',NULL,6,'ENABLE',1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1127);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1128,0,1121,'整改缺陷','BUTTON',NULL,NULL,'closeout:defect:maintain',NULL,7,'ENABLE',1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1128);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1129,0,1121,'复验缺陷','BUTTON',NULL,NULL,'closeout:defect:verify',NULL,8,'ENABLE',1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1129);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1130,0,1121,'移交竣工档案','BUTTON',NULL,NULL,'closeout:archive:maintain',NULL,9,'ENABLE',1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1130);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1131,0,1121,'关闭项目','BUTTON',NULL,NULL,'closeout:close',NULL,10,'ENABLE',1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1131);
INSERT INTO sys_role_menu(id,role_id,menu_id)
SELECT 191000000+r.id*10000+m.id,r.id,m.id FROM sys_role r JOIN sys_menu m ON m.id BETWEEN 1121 AND 1131
WHERE r.role_code IN ('SUPER_ADMIN','ADMIN','PROJECT_MANAGER','FINANCE','COST_MANAGER','AUDITOR') AND r.deleted_flag=0
AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.id AND rm.menu_id=m.id);
