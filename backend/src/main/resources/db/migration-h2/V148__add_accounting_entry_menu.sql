-- V148: expose the existing accounting-entry query and controlled state transitions.
-- accounting:add is intentionally not granted: no production EntryGenerationStrategy exists yet.
INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
SELECT 960, 0, 906, '会计凭证', 'MENU', '/accounting-entry', 'accounting-entry/index', 'accounting:query', 'account-book', 5, 'ENABLE', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 960);

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
SELECT 961, 0, 960, '过账与冲销', 'BUTTON', NULL, NULL, 'accounting:edit', NULL, 1, 'ENABLE', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 961);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 148000000 + r.id * 1000 + m.id, r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN (960, 961) AND m.deleted_flag = 0
WHERE r.role_code IN ('SUPER_ADMIN', 'ADMIN', 'FINANCE')
  AND r.deleted_flag = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id
  );
