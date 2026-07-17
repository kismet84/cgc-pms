INSERT IGNORE INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
VALUES
    (1041, 0, 941, '供应商退货', 'BUTTON', NULL, NULL, 'receipt:return', NULL, 20, 'ENABLE', 0),
    (1042, 0, 917, '材料退料', 'BUTTON', NULL, NULL, 'requisition:return', NULL, 20, 'ENABLE', 0),
    (1043, 0, 917, '领料实际出库', 'BUTTON', NULL, NULL, 'requisition:stock-out', NULL, 21, 'ENABLE', 0),
    (1044, 0, 941, '采购全链追溯', 'BUTTON', NULL, NULL, 'procurement:trace:query', NULL, 22, 'ENABLE', 0);

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id)
SELECT 183000000 + r.id * 10000 + m.id, r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN (1041, 1042, 1043, 1044)
WHERE r.role_code IN ('SUPER_ADMIN','ADMIN','PROJECT_MANAGER','PURCHASE_MANAGER','MATERIAL_MANAGER')
  AND r.deleted_flag = 0;
