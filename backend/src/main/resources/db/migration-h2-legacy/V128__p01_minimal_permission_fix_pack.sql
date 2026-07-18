-- V128 H2: P0-1 minimal permission fix pack.

UPDATE sys_menu
SET perms = NULL
WHERE id = 751
  AND perms = 'invoice:list';

UPDATE sys_menu
SET perms = NULL
WHERE id = 201
  AND perms = 'project:list';

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
SELECT 917, 0, 904, '领料申请', 'MENU', '/inventory/material-requisition', 'requisition/index', 'requisition:query', 'profile', 7, 'ENABLE', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 917);

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
SELECT 918, 0, 917, '提交领料审批', 'BUTTON', NULL, NULL, 'requisition:submit', NULL, 1, 'ENABLE', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 918);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 128001, 1, 917
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 1 AND menu_id = 917);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 128002, 1, 918
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 1 AND menu_id = 918);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 128003, 5, 917
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 5 AND menu_id = 917);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 128004, 5, 918
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 5 AND menu_id = 918);
