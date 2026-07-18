SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

ALTER TABLE cost_target
    ADD COLUMN total_bid_cost_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '投标成本基准总额',
    ADD COLUMN total_responsibility_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '责任预算总额',
    ADD COLUMN approval_instance_id BIGINT NULL COMMENT '审批实例',
    ADD COLUMN version INT NOT NULL DEFAULT 0;

UPDATE cost_target
SET total_bid_cost_amount = CASE WHEN total_bid_cost_amount = 0 THEN total_target_amount ELSE total_bid_cost_amount END,
    total_responsibility_amount = CASE WHEN total_responsibility_amount = 0 THEN total_target_amount ELSE total_responsibility_amount END;

ALTER TABLE cost_target_item
    ADD COLUMN bid_cost_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '投标成本科目快照',
    ADD COLUMN responsibility_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '责任预算科目金额',
    ADD COLUMN responsible_user_id BIGINT NULL COMMENT '责任人',
    ADD COLUMN responsibility_unit VARCHAR(200) NULL COMMENT '责任单位/部门',
    ADD COLUMN sort_order INT NOT NULL DEFAULT 0;

UPDATE cost_target_item
SET bid_cost_amount = CASE WHEN bid_cost_amount = 0 THEN target_amount ELSE bid_cost_amount END,
    responsibility_amount = CASE WHEN responsibility_amount = 0 THEN target_amount ELSE responsibility_amount END;

CREATE TABLE cost_forecast (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL,
    cost_target_id BIGINT NOT NULL,
    forecast_code VARCHAR(64) NOT NULL,
    forecast_name VARCHAR(200) NOT NULL,
    version_no INT NOT NULL,
    forecast_date DATE NOT NULL,
    bid_cost_amount DECIMAL(18,2) NOT NULL,
    target_cost_amount DECIMAL(18,2) NOT NULL,
    responsibility_amount DECIMAL(18,2) NOT NULL,
    committed_cost_amount DECIMAL(18,2) NOT NULL,
    actual_cost_amount DECIMAL(18,2) NOT NULL,
    estimated_remaining_amount DECIMAL(18,2) NOT NULL,
    forecast_at_completion_amount DECIMAL(18,2) NOT NULL,
    contract_income_amount DECIMAL(18,2) NOT NULL,
    forecast_profit_amount DECIMAL(18,2) NOT NULL,
    cost_variance_amount DECIMAL(18,2) NOT NULL,
    profit_margin DECIMAL(9,6) NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    formula_version VARCHAR(40) NOT NULL DEFAULT 'COST_EAC_V1',
    confirmed_at DATETIME NULL,
    confirmed_by BIGINT NULL,
    version INT NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cost_forecast_code (tenant_id, project_id, forecast_code, deleted_flag),
    UNIQUE KEY uk_cost_forecast_version (tenant_id, project_id, version_no, deleted_flag),
    KEY idx_cost_forecast_project_status (tenant_id, project_id, status, forecast_date),
    CONSTRAINT fk_cost_forecast_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cost_forecast_target FOREIGN KEY (cost_target_id) REFERENCES cost_target(id) ON DELETE RESTRICT,
    CONSTRAINT ck_cost_forecast_amount CHECK (bid_cost_amount >= 0 AND target_cost_amount >= 0 AND responsibility_amount >= 0 AND committed_cost_amount >= 0 AND actual_cost_amount >= 0 AND estimated_remaining_amount >= 0 AND forecast_at_completion_amount >= 0),
    CONSTRAINT ck_cost_forecast_status CHECK (status IN ('DRAFT','ACTION_REQUIRED','CONTROLLED','SUPERSEDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目完工成本与利润预测版本';

CREATE TABLE cost_forecast_item (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    forecast_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    cost_subject_id BIGINT NOT NULL,
    bid_cost_amount DECIMAL(18,2) NOT NULL,
    target_cost_amount DECIMAL(18,2) NOT NULL,
    responsibility_amount DECIMAL(18,2) NOT NULL,
    committed_cost_amount DECIMAL(18,2) NOT NULL,
    actual_cost_amount DECIMAL(18,2) NOT NULL,
    estimated_remaining_amount DECIMAL(18,2) NOT NULL,
    forecast_at_completion_amount DECIMAL(18,2) NOT NULL,
    cost_variance_amount DECIMAL(18,2) NOT NULL,
    responsible_user_id BIGINT NULL,
    responsibility_unit VARCHAR(200) NULL,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cost_forecast_item (tenant_id, forecast_id, cost_subject_id),
    KEY idx_cost_forecast_item_project (tenant_id, project_id, cost_subject_id),
    CONSTRAINT fk_cost_forecast_item_forecast FOREIGN KEY (forecast_id) REFERENCES cost_forecast(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cost_forecast_item_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cost_forecast_item_subject FOREIGN KEY (cost_subject_id) REFERENCES cost_subject(id) ON DELETE RESTRICT,
    CONSTRAINT ck_cost_forecast_item_amount CHECK (bid_cost_amount >= 0 AND target_cost_amount >= 0 AND responsibility_amount >= 0 AND committed_cost_amount >= 0 AND actual_cost_amount >= 0 AND estimated_remaining_amount >= 0 AND forecast_at_completion_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='完工成本预测科目快照';

CREATE TABLE cost_corrective_action (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL,
    forecast_id BIGINT NOT NULL,
    action_code VARCHAR(64) NOT NULL,
    action_title VARCHAR(200) NOT NULL,
    root_cause VARCHAR(500) NOT NULL,
    action_plan VARCHAR(1000) NOT NULL,
    expected_saving_amount DECIMAL(18,2) NOT NULL,
    actual_saving_amount DECIMAL(18,2) NULL,
    responsible_user_id BIGINT NOT NULL,
    due_date DATE NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    approval_instance_id BIGINT NULL,
    result_description VARCHAR(1000) NULL,
    completed_at DATETIME NULL,
    version INT NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cost_corrective_code (tenant_id, project_id, action_code, deleted_flag),
    KEY idx_cost_corrective_forecast (tenant_id, forecast_id, status, due_date),
    CONSTRAINT fk_cost_corrective_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cost_corrective_forecast FOREIGN KEY (forecast_id) REFERENCES cost_forecast(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cost_corrective_approval FOREIGN KEY (approval_instance_id) REFERENCES wf_instance(id) ON DELETE RESTRICT,
    CONSTRAINT ck_cost_corrective_amount CHECK (expected_saving_amount > 0 AND (actual_saving_amount IS NULL OR actual_saving_amount >= 0)),
    CONSTRAINT ck_cost_corrective_status CHECK (status IN ('DRAFT','PENDING','APPROVED','REJECTED','CLOSED','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='成本偏差纠偏措施';

ALTER TABLE cost_target
    ADD CONSTRAINT fk_cost_target_project FOREIGN KEY (project_id) REFERENCES pm_project(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_cost_target_approval FOREIGN KEY (approval_instance_id) REFERENCES wf_instance(id) ON DELETE RESTRICT,
    ADD UNIQUE KEY uk_cost_target_version (tenant_id, project_id, version_no, deleted_flag),
    ADD CONSTRAINT ck_cost_target_closed_loop_amount CHECK (total_bid_cost_amount >= 0 AND total_target_amount >= 0 AND total_responsibility_amount >= 0);

ALTER TABLE cost_target_item
    ADD CONSTRAINT fk_cost_target_item_target FOREIGN KEY (target_id) REFERENCES cost_target(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_cost_target_item_subject FOREIGN KEY (cost_subject_id) REFERENCES cost_subject(id) ON DELETE RESTRICT,
    ADD UNIQUE KEY uk_cost_target_item_subject (tenant_id, target_id, cost_subject_id, deleted_flag),
    ADD CONSTRAINT ck_cost_target_item_closed_loop_amount CHECK (bid_cost_amount >= 0 AND target_amount >= 0 AND responsibility_amount >= 0);

ALTER TABLE cost_summary
    ADD COLUMN cost_forecast_id BIGINT NULL COMMENT '采用的完工预测版本',
    ADD COLUMN responsibility_cost DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '责任预算',
    ADD COLUMN forecast_at_completion_cost DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '完工预测成本',
    ADD COLUMN forecast_profit DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '预测利润',
    ADD COLUMN profit_margin DECIMAL(9,6) NOT NULL DEFAULT 0 COMMENT '预测利润率',
    ADD CONSTRAINT fk_cost_summary_forecast FOREIGN KEY (cost_forecast_id) REFERENCES cost_forecast(id) ON DELETE RESTRICT;

INSERT IGNORE INTO wf_template(id,tenant_id,template_code,template_name,business_type,enabled,created_by,remark)
VALUES(50036,0,'TPL-COST-CORRECTIVE-001','成本偏差纠偏审批','COST_CORRECTIVE_ACTION',1,1,'目标成本与动态利润闭环：项目经理、成本经理、分管领导三级审批');

INSERT IGNORE INTO wf_template_node(id,tenant_id,template_id,node_code,node_name,node_order,node_type,approve_mode,approver_config,allow_transfer,allow_add_sign,timeout_hours) VALUES
(53601,0,50036,'N1','项目经理审批',1,'APPROVAL','SEQUENTIAL',JSON_OBJECT('type','USER','userId',1),1,1,48),
(53602,0,50036,'N2','成本经理审批',2,'APPROVAL','SEQUENTIAL',JSON_OBJECT('type','USER','userId',1),1,1,48),
(53603,0,50036,'N3','分管领导审批',3,'APPROVAL','SEQUENTIAL',JSON_OBJECT('type','USER','userId',1),1,1,72);

INSERT IGNORE INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible) VALUES
(1093,0,903,'动态利润控制','MENU','/cost/control','cost/control','cost:control:query','fund',5,'ENABLE',1),
(1094,0,1093,'维护完工预测','BUTTON',NULL,NULL,'cost:forecast:maintain',NULL,1,'ENABLE',1),
(1095,0,1093,'确认完工预测','BUTTON',NULL,NULL,'cost:forecast:confirm',NULL,2,'ENABLE',1),
(1096,0,1093,'维护纠偏措施','BUTTON',NULL,NULL,'cost:corrective:maintain',NULL,3,'ENABLE',1),
(1097,0,1093,'提交与关闭纠偏','BUTTON',NULL,NULL,'cost:corrective:submit',NULL,4,'ENABLE',1);

INSERT IGNORE INTO sys_role_menu(id,role_id,menu_id)
SELECT 187000000+r.id*10000+m.id,r.id,m.id
FROM sys_role r JOIN sys_menu m ON m.id BETWEEN 1093 AND 1097
WHERE r.role_code IN ('SUPER_ADMIN','ADMIN','PROJECT_MANAGER','COST_MANAGER','FINANCE','AUDITOR') AND r.deleted_flag=0;

SET FOREIGN_KEY_CHECKS = 1;
