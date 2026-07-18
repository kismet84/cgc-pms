-- 现场日报最小闭环：项目/日期唯一、草稿到提交、附件复用 SITE_DAILY_LOG。

CREATE TABLE site_daily_log (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    report_date DATE NOT NULL,
    construction_content TEXT NOT NULL,
    issues_delays TEXT NULL,
    next_day_plan TEXT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    submitted_by BIGINT NULL,
    submitted_at DATETIME NULL,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_site_daily_project_date (tenant_id, project_id, report_date),
    KEY idx_site_daily_project_date (tenant_id, project_id, report_date, status),
    CONSTRAINT ck_site_daily_status CHECK (status IN ('DRAFT', 'SUBMITTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目现场日报';

INSERT IGNORE INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
VALUES
    (958, 0, 201, '现场日报', 'MENU', '/site/daily-log', 'site/daily-log', 'site:daily:query', 'file-text', 8, 'ENABLE', 1),
    (959, 0, 958, '维护现场日报', 'BUTTON', NULL, NULL, 'site:daily:edit', NULL, 1, 'ENABLE', 0);

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id)
SELECT 141000 + id, 1, id FROM sys_menu WHERE id IN (958, 959) AND deleted_flag = 0;

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id)
SELECT 142000 + id, 8, id FROM sys_menu WHERE id IN (958, 959) AND deleted_flag = 0;

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id)
SELECT 143000 + id, 2, id FROM sys_menu WHERE id = 958 AND deleted_flag = 0;
