-- Align permission catalog facts with the V2 navigation without changing role assignments.

UPDATE sys_menu
SET parent_id = 909,
    menu_name = '权限清单',
    menu_type = 'MENU',
    path = '/system/permissions',
    component = NULL,
    visible = 1,
    updated_by = 1,
    updated_at = CURRENT_TIMESTAMP,
    remark = 'V2 navigation alignment'
WHERE id = 503 AND tenant_id = 0 AND deleted_flag = 0;

UPDATE sys_menu
SET parent_id = 503, updated_by = 1, updated_at = CURRENT_TIMESTAMP
WHERE id = 802 AND tenant_id = 0 AND deleted_flag = 0;

UPDATE sys_menu
SET menu_name = '顶栏通知中心',
    menu_type = 'DIR',
    path = NULL,
    component = NULL,
    visible = 0,
    updated_by = 1,
    updated_at = CURRENT_TIMESTAMP,
    remark = 'Global notification permission container'
WHERE id = 761 AND tenant_id = 0 AND deleted_flag = 0;

UPDATE sys_menu
SET parent_id = 1060,
    menu_type = 'BUTTON',
    path = NULL,
    component = NULL,
    visible = 0,
    updated_by = 1,
    updated_at = CURRENT_TIMESTAMP,
    remark = 'Finance operations contextual permission'
WHERE id = 1040 AND tenant_id = 0 AND deleted_flag = 0;

UPDATE sys_menu
SET menu_type = 'MENU',
    path = '/cost/subject/rules',
    component = NULL,
    updated_by = 1,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 2138 AND tenant_id = 0 AND deleted_flag = 0;

UPDATE sys_menu
SET menu_type = 'MENU',
    path = '/cost/subject/scope',
    component = NULL,
    updated_by = 1,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 2139 AND tenant_id = 0 AND deleted_flag = 0;

UPDATE sys_menu
SET menu_type = 'MENU',
    path = '/cost/subject/trace',
    component = NULL,
    updated_by = 1,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 2134 AND tenant_id = 0 AND deleted_flag = 0;

UPDATE sys_menu SET parent_id = 2138, updated_by = 1, updated_at = CURRENT_TIMESTAMP
WHERE id = 2132 AND tenant_id = 0 AND deleted_flag = 0;
UPDATE sys_menu SET parent_id = 2139, updated_by = 1, updated_at = CURRENT_TIMESTAMP
WHERE id = 2133 AND tenant_id = 0 AND deleted_flag = 0;
UPDATE sys_menu SET parent_id = 2134, updated_by = 1, updated_at = CURRENT_TIMESTAMP
WHERE id IN (2135, 2136, 2137) AND tenant_id = 0 AND deleted_flag = 0;

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon,
     order_num, status, visible, created_by, updated_by, remark, created_at, updated_at, deleted_flag)
SELECT 23601, 0, 908, '我发起', 'MENU', '/approval/mine', NULL, NULL, NULL,
       4, 'ENABLE', 1, 1, 1, 'V2 navigation alignment', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE tenant_id = 0 AND path = '/approval/mine' AND deleted_flag = 0
);

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon,
     order_num, status, visible, created_by, updated_by, remark, created_at, updated_at, deleted_flag)
SELECT 23602, 0, 900, '报表目录', 'MENU', '/dashboard/reports', NULL, NULL, NULL,
       3, 'ENABLE', 1, 1, 1, 'V2 navigation alignment', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE tenant_id = 0 AND path = '/dashboard/reports' AND deleted_flag = 0
);
