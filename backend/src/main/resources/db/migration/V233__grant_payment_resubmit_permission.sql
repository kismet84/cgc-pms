-- 第55条资金支出闭环：付款申请驳回后由项目经理修订并重提原流程实例。

INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT 233000100000000 + ROW_NUMBER() OVER (ORDER BY r.id, m.id), r.tenant_id, r.id, m.id
FROM sys_role r
JOIN sys_menu m
  ON m.tenant_id = r.tenant_id
 AND m.perms = 'workflow:resubmit'
 AND m.deleted_flag = 0
WHERE r.tenant_id = 0
  AND r.role_code = 'PROJECT_MANAGER'
  AND r.deleted_flag = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm
      WHERE rm.tenant_id = r.tenant_id AND rm.role_id = r.id AND rm.menu_id = m.id
  );
