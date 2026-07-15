-- V152: H2 parity for the controlled bid-cost edit permission.
INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
SELECT 964, 0, 962, '编辑投标项目', 'BUTTON', NULL, NULL, 'bid:edit', NULL, 2, 'ENABLE', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 964);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 152000000 + r.id * 1000 + m.id, r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.id = 964 AND m.deleted_flag = 0
WHERE r.role_code IN ('SUPER_ADMIN', 'ADMIN', 'COST_MANAGER')
  AND r.deleted_flag = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id
  );
