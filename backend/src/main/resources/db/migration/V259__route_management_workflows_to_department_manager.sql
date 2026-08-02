UPDATE wf_template_node
SET approver_config = REPLACE(approver_config, 'MANAGEMENT_EXECUTIVE', 'DEPARTMENT_MANAGER')
WHERE approver_config LIKE '%MANAGEMENT_EXECUTIVE%'
  AND deleted_flag = 0;

INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT 259000100000000 + ROW_NUMBER() OVER (ORDER BY r.id, m.id),
       r.tenant_id,
       r.id,
       m.id
FROM sys_role r
JOIN sys_menu m
  ON m.tenant_id = r.tenant_id
 AND m.perms IN ('workflow:approve', 'workflow:reject')
 AND m.deleted_flag = 0
WHERE r.role_code = 'DEPARTMENT_MANAGER'
  AND r.deleted_flag = 0
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_menu rm
      WHERE rm.tenant_id = r.tenant_id
        AND rm.role_id = r.id
        AND rm.menu_id = m.id
  );
