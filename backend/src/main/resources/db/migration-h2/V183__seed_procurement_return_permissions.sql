INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
SELECT 1041, 0, 941, '供应商退货', 'BUTTON', NULL, NULL, 'receipt:return', NULL, 20, 'ENABLE', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 1041);
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
SELECT 1042, 0, 917, '材料退料', 'BUTTON', NULL, NULL, 'requisition:return', NULL, 20, 'ENABLE', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 1042);
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
SELECT 1043, 0, 917, '领料实际出库', 'BUTTON', NULL, NULL, 'requisition:stock-out', NULL, 21, 'ENABLE', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 1043);
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
SELECT 1044, 0, 941, '采购全链追溯', 'BUTTON', NULL, NULL, 'procurement:trace:query', NULL, 22, 'ENABLE', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 1044);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 183000000 + r.id * 10000 + m.id, r.id, m.id
FROM sys_role r CROSS JOIN sys_menu m
WHERE m.id IN (1041, 1042, 1043, 1044)
  AND r.role_code IN ('SUPER_ADMIN','ADMIN','PROJECT_MANAGER','PURCHASE_MANAGER','MATERIAL_MANAGER')
  AND r.deleted_flag = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
