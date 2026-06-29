-- V96: Phase 2 dashboard role permissions (H2)

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
SELECT 813, 0, 1, '采购经理驾驶舱', 'BUTTON', NULL, NULL, 'dashboard:purchase-manager:view', NULL, 7, 'ENABLE', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 813);

INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
SELECT 814, 0, 1, '生产经理驾驶舱', 'BUTTON', NULL, NULL, 'dashboard:production-manager:view', NULL, 8, 'ENABLE', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 814);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 10843, 1, 813 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 1 AND menu_id = 813);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 10844, 1, 814 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 1 AND menu_id = 814);
