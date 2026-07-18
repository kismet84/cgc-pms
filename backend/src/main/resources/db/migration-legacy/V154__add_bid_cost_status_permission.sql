-- V154: register the controlled bid-cost status permission under the existing query menu.
INSERT IGNORE INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
VALUES
    (966, 0, 962, '变更投标状态', 'BUTTON', NULL, NULL, 'bid:status', NULL, 4, 'ENABLE', 0);

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id)
SELECT 154000000 + r.id * 1000 + m.id, r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.id = 966 AND m.deleted_flag = 0
WHERE r.role_code IN ('SUPER_ADMIN', 'ADMIN', 'COST_MANAGER')
  AND r.deleted_flag = 0;
