SET NAMES utf8mb4;

CREATE TABLE cash_forecast_cycle (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, project_id BIGINT NOT NULL,
    cycle_code VARCHAR(64) NOT NULL, forecast_name VARCHAR(200) NOT NULL,
    as_of_date DATE NOT NULL, horizon_start DATE NOT NULL, horizon_end DATE NOT NULL,
    scenario VARCHAR(32) NOT NULL, opening_balance DECIMAL(18,2) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT', version_no INT NOT NULL,
    previous_cycle_id BIGINT NULL, source_cutoff_at DATETIME NOT NULL,
    submitted_by BIGINT NULL, submitted_at DATETIME NULL,
    approved_by BIGINT NULL, approved_at DATETIME NULL, approval_comment VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0, created_by BIGINT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY(id), UNIQUE KEY uk_cash_forecast_cycle_code(tenant_id,cycle_code),
    UNIQUE KEY uk_cash_forecast_cycle_version(tenant_id,project_id,scenario,version_no),
    KEY idx_cash_forecast_cycle_active(tenant_id,project_id,scenario,status,as_of_date),
    CONSTRAINT fk_cash_forecast_cycle_project FOREIGN KEY(project_id) REFERENCES pm_project(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cash_forecast_cycle_previous FOREIGN KEY(previous_cycle_id) REFERENCES cash_forecast_cycle(id) ON DELETE RESTRICT,
    CONSTRAINT ck_cash_forecast_cycle_dates CHECK(as_of_date<=horizon_start AND horizon_start<=horizon_end),
    CONSTRAINT ck_cash_forecast_cycle_opening CHECK(opening_balance>=0),
    CONSTRAINT ck_cash_forecast_cycle_status CHECK(status IN ('DRAFT','SUBMITTED','APPROVED','SUPERSEDED')),
    CONSTRAINT ck_cash_forecast_cycle_scenario CHECK(scenario IN ('BASE','OPTIMISTIC','CONSERVATIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE cash_forecast_line (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, cycle_id BIGINT NOT NULL,
    forecast_date DATE NOT NULL, planned_inflow DECIMAL(18,2) NOT NULL DEFAULT 0,
    planned_outflow DECIMAL(18,2) NOT NULL DEFAULT 0, financing_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    projected_balance DECIMAL(18,2) NOT NULL, gap_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    actual_inflow DECIMAL(18,2) NOT NULL DEFAULT 0, actual_outflow DECIMAL(18,2) NOT NULL DEFAULT 0,
    inflow_variance DECIMAL(18,2) NOT NULL DEFAULT 0, outflow_variance DECIMAL(18,2) NOT NULL DEFAULT 0,
    source_summary_json JSON NULL, actual_refreshed_at DATETIME NULL,
    PRIMARY KEY(id), UNIQUE KEY uk_cash_forecast_line(tenant_id,cycle_id,forecast_date),
    CONSTRAINT fk_cash_forecast_line_cycle FOREIGN KEY(cycle_id) REFERENCES cash_forecast_cycle(id) ON DELETE RESTRICT,
    CONSTRAINT ck_cash_forecast_line_amounts CHECK(planned_inflow>=0 AND planned_outflow>=0 AND financing_amount>=0 AND gap_amount>=0 AND actual_inflow>=0 AND actual_outflow>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE cash_funding_action (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, cycle_id BIGINT NOT NULL,
    line_id BIGINT NOT NULL, project_id BIGINT NOT NULL, action_type VARCHAR(32) NOT NULL,
    planned_date DATE NOT NULL, amount DECIMAL(18,2) NOT NULL, reason VARCHAR(500) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PROPOSED', source_type VARCHAR(32) NULL, source_id BIGINT NULL,
    requested_by BIGINT NOT NULL, submitted_at DATETIME NULL, approved_by BIGINT NULL, approved_at DATETIME NULL,
    completed_by BIGINT NULL, completed_at DATETIME NULL, actual_amount DECIMAL(18,2) NULL,
    completion_reference VARCHAR(128) NULL, version INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY(id), KEY idx_cash_funding_action(tenant_id,cycle_id,status,planned_date),
    CONSTRAINT fk_cash_funding_action_cycle FOREIGN KEY(cycle_id) REFERENCES cash_forecast_cycle(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cash_funding_action_line FOREIGN KEY(line_id) REFERENCES cash_forecast_line(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cash_funding_action_project FOREIGN KEY(project_id) REFERENCES pm_project(id) ON DELETE RESTRICT,
    CONSTRAINT ck_cash_funding_action_amount CHECK(amount>0 AND (actual_amount IS NULL OR actual_amount>0)),
    CONSTRAINT ck_cash_funding_action_type CHECK(action_type IN ('ACCELERATE_COLLECTION','DEFER_PAYMENT','FUND_TRANSFER','FINANCING')),
    CONSTRAINT ck_cash_funding_action_status CHECK(status IN ('PROPOSED','SUBMITTED','APPROVED','COMPLETED','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1140,0,906,'资金计划与现金预测','MENU','/cash-forecast','cash-forecast/index','finance:forecast:query','fund',7,'ENABLE',1
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1140);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1141,0,1140,'维护资金预测','BUTTON',NULL,NULL,'finance:forecast:maintain',NULL,1,'ENABLE',0 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1141);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1142,0,1140,'提交资金预测','BUTTON',NULL,NULL,'finance:forecast:submit',NULL,2,'ENABLE',0 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1142);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1143,0,1140,'审批资金预测','BUTTON',NULL,NULL,'finance:forecast:approve',NULL,3,'ENABLE',0 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1143);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1144,0,1140,'刷新实际偏差','BUTTON',NULL,NULL,'finance:forecast:refresh',NULL,4,'ENABLE',0 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1144);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1145,0,1140,'维护缺口措施','BUTTON',NULL,NULL,'finance:forecast:action',NULL,5,'ENABLE',0 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1145);
INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible)
SELECT 1146,0,1140,'审批缺口措施','BUTTON',NULL,NULL,'finance:forecast:action:approve',NULL,6,'ENABLE',0 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE id=1146);

INSERT INTO sys_role_menu(id,role_id,menu_id)
SELECT 193000000+r.id*10000+m.id,r.id,m.id FROM sys_role r JOIN sys_menu m ON m.id BETWEEN 1140 AND 1146
WHERE r.role_code IN ('SUPER_ADMIN','ADMIN','FINANCE') AND r.deleted_flag=0
AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.id AND rm.menu_id=m.id);
INSERT INTO sys_role_menu(id,role_id,menu_id)
SELECT 193900000+r.id*10000+1140,r.id,1140 FROM sys_role r WHERE r.role_code IN ('PROJECT_MANAGER','AUDITOR') AND r.deleted_flag=0
AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.id AND rm.menu_id=1140);
