-- V135 H2: FINANCE minimal readonly grants for payment records, payment applications, and settlements.

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
SELECT 951, 0, 906, '付款记录查询', 'BUTTON', NULL, NULL, 'payment:record:query', NULL, 2, 'ENABLE', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 951);

DELETE FROM sys_role_menu
WHERE role_id = 6
  AND menu_id IN (
      SELECT id
      FROM sys_menu
      WHERE perms IN (
          'payment:record:writeback',
          'payment:app:add',
          'payment:app:create',
          'payment:app:update',
          'payment:app:edit',
          'payment:app:delete',
          'payment:app:submit',
          'settlement:add',
          'settlement:create',
          'settlement:update',
          'settlement:edit',
          'settlement:delete',
          'settlement:submit'
      )
  );

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 135000 + m.id, 6, m.id
FROM sys_menu m
WHERE m.id IN (906, 907, 944, 945, 951)
  AND m.deleted_flag = 0
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_menu rm
      WHERE rm.role_id = 6
        AND rm.menu_id = m.id
  );
