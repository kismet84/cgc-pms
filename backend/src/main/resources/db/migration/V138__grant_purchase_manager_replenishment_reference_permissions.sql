-- Read-only reference data required by the stock replenishment entry.

SET NAMES utf8mb4;

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id)
SELECT 138001 + grants.seq, role.id, grants.menu_id
FROM sys_role role
JOIN (
    SELECT 0 AS seq, 731 AS menu_id UNION ALL
    SELECT 1, 920
) grants
WHERE role.tenant_id = 0
  AND role.role_code = 'PURCHASE_MANAGER'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu existing
      WHERE existing.role_id = role.id AND existing.menu_id = grants.menu_id
  );
