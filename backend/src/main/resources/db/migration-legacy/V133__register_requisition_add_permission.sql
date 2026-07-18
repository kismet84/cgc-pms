-- V130: register MATERIAL_REQUISITION create permission for dev/super-admin test data setup.
-- Keep business role grants unchanged; SUPER_ADMIN already has backend role bypass.

SET NAMES utf8mb4;

INSERT IGNORE INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
VALUES
    (919, 0, 917, '新增领料申请', 'BUTTON', NULL, NULL, 'requisition:add', NULL, 2, 'ENABLE', 1);

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id)
SELECT 130001, 1, 919
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_menu WHERE role_id = 1 AND menu_id = 919
);
