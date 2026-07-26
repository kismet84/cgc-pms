-- 第55条资金支出闭环：业务角色必须能在项目数据范围内读取付款所属项目。

INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT 232000100000000 + ROW_NUMBER() OVER (ORDER BY r.id, m.id), r.tenant_id, r.id, m.id
FROM sys_role r
JOIN sys_menu m
  ON m.tenant_id = r.tenant_id
 AND m.perms = 'project:query'
 AND m.deleted_flag = 0
WHERE r.tenant_id = 0
  AND r.role_code IN ('PROJECT_MANAGER', 'COST_MANAGER', 'DEPARTMENT_MANAGER', 'GENERAL_MANAGER', 'FINANCE')
  AND r.deleted_flag = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm
      WHERE rm.tenant_id = r.tenant_id AND rm.role_id = r.id AND rm.menu_id = m.id
  );
