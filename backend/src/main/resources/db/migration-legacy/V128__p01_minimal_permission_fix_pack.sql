-- V128: P0-1 minimal permission fix pack.
-- Keep this pointed: legacy list-code cleanup plus MATERIAL_REQUISITION submit grant.

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

UPDATE sys_menu
SET perms = NULL
WHERE id = 751
  AND perms = 'invoice:list';

UPDATE sys_menu
SET perms = NULL
WHERE id = 201
  AND perms = 'project:list';

INSERT IGNORE INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
VALUES
    (917, 0, 904, '领料申请', 'MENU', '/inventory/material-requisition', 'requisition/index', 'requisition:query', 'profile', 7, 'ENABLE', 1),
    (918, 0, 917, '提交领料审批', 'BUTTON', NULL, NULL, 'requisition:submit', NULL, 1, 'ENABLE', 1);

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id)
VALUES
    (128001, 1, 917),
    (128002, 1, 918),
    (128003, 5, 917),
    (128004, 5, 918);

SET FOREIGN_KEY_CHECKS = 1;
