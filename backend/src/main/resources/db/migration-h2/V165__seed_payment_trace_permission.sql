INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
SELECT 1040, 0, 2, '付款全链路追溯', 'MENU', 'payment-trace', 'payment/trace', 'payment:trace:query', 'connection', 10, 'ENABLE', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 1040);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 165000000 + r.id * 10000 + 1040, r.id, 1040
FROM sys_role r
WHERE r.role_code IN ('SUPER_ADMIN','ADMIN','PROJECT_MANAGER','COST_MANAGER','FINANCE','AUDITOR')
  AND r.deleted_flag = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = 1040);
