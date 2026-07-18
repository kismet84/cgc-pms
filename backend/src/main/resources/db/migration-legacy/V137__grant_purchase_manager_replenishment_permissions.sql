-- Enable the existing stock -> purchase request flow for PURCHASE_MANAGER.

SET NAMES utf8mb4;

INSERT IGNORE INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
VALUES
    (956, 0, 734, '编辑采购申请', 'BUTTON', NULL, NULL, 'purchase:request:edit', NULL, 3, 'ENABLE', 1),
    (957, 0, 734, '删除采购申请', 'BUTTON', NULL, NULL, 'purchase:request:delete', NULL, 4, 'ENABLE', 1);

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id)
SELECT 137001 + grants.seq, role.id, grants.menu_id
FROM sys_role role
JOIN (
    SELECT 0 AS seq, 904 AS menu_id UNION ALL
    SELECT 1, 732 UNION ALL
    SELECT 2, 734 UNION ALL
    SELECT 3, 739 UNION ALL
    SELECT 4, 740 UNION ALL
    SELECT 5, 956 UNION ALL
    SELECT 6, 957
) grants
WHERE role.tenant_id = 0
  AND role.role_code = 'PURCHASE_MANAGER'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu existing
      WHERE existing.role_id = role.id AND existing.menu_id = grants.menu_id
  );
