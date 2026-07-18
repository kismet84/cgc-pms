-- V135: FINANCE minimal readonly grants for payment records, payment applications, and settlements.

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

INSERT IGNORE INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
VALUES
    (951, 0, 906, '付款记录查询', 'BUTTON', NULL, NULL, 'payment:record:query', NULL, 2, 'ENABLE', 0);

DELETE rm
FROM sys_role_menu rm
JOIN sys_menu m ON m.id = rm.menu_id
WHERE rm.role_id = 6
  AND m.perms IN (
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
  );

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id)
SELECT 135000 + id, 6, id
FROM sys_menu
WHERE id IN (906, 907, 944, 945, 951)
  AND deleted_flag = 0;

SET FOREIGN_KEY_CHECKS = 1;
