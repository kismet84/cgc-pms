-- M7 system management: expose operation audit to the platform administrator.

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon,
     order_num, status, visible, created_by, updated_by, remark, created_at, updated_at, deleted_flag)
SELECT 23401, 0, 909, '操作审计', 'MENU', '/system/audit', NULL, 'audit:query', NULL,
       4, 'ENABLE', 1, 1, 1, 'ISSUE-053-042', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE tenant_id = 0 AND perms = 'audit:query' AND deleted_flag = 0
);

INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT 234000000000000 + ROW_NUMBER() OVER (ORDER BY r.id, m.id), r.tenant_id, r.id, m.id
FROM sys_role r
JOIN sys_menu m
  ON m.tenant_id = r.tenant_id
 AND m.perms = 'audit:query'
 AND m.deleted_flag = 0
WHERE r.tenant_id = 0
  AND r.role_code = 'SUPER_ADMIN'
  AND r.deleted_flag = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm
      WHERE rm.tenant_id = r.tenant_id AND rm.role_id = r.id AND rm.menu_id = m.id
  );
