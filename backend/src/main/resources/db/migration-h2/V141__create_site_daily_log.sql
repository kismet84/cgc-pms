-- H2 mirror: 现场日报最小闭环。

CREATE TABLE site_daily_log (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    report_date DATE NOT NULL,
    construction_content CLOB NOT NULL,
    issues_delays CLOB NULL,
    next_day_plan CLOB NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    submitted_by BIGINT NULL,
    submitted_at TIMESTAMP NULL,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_site_daily_project_date UNIQUE (tenant_id, project_id, report_date),
    CONSTRAINT ck_site_daily_status CHECK (status IN ('DRAFT', 'SUBMITTED'))
);
CREATE INDEX idx_site_daily_project_date ON site_daily_log(tenant_id, project_id, report_date, status);

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
SELECT 958, 0, 201, '现场日报', 'MENU', '/site/daily-log', 'site/daily-log', 'site:daily:query', 'file-text', 8, 'ENABLE', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 958);
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
SELECT 959, 0, 958, '维护现场日报', 'BUTTON', NULL, NULL, 'site:daily:edit', NULL, 1, 'ENABLE', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 959);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 141000 + m.id, 1, m.id FROM sys_menu m WHERE m.id IN (958, 959)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 142000 + m.id, 8, m.id FROM sys_menu m WHERE m.id IN (958, 959)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 8 AND rm.menu_id = m.id);
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 143000 + m.id, 2, m.id FROM sys_menu m WHERE m.id = 958
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 2 AND rm.menu_id = m.id);
