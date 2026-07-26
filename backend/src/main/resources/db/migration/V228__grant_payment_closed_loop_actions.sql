-- 第55条资金支出闭环：允许项目经理提交付款申请、财务执行权威付款回写。

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon,
     order_num, status, visible, created_by, updated_by, remark, created_at, updated_at, deleted_flag)
SELECT 22801, 0, 951, '付款执行回写', 'BUTTON', NULL, NULL, 'payment:record:writeback', NULL,
       10, 'ENABLE', 0, 1, 1, 'PROJECT-PAYMENT-CLOSED-LOOP', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE tenant_id = 0 AND perms = 'payment:record:writeback' AND deleted_flag = 0
);

INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT 228000100000000 + ROW_NUMBER() OVER (ORDER BY r.id, m.id), r.tenant_id, r.id, m.id
FROM sys_role r
JOIN sys_menu m
  ON m.tenant_id = r.tenant_id
 AND m.perms IN ('payment:app:add', 'payment:app:edit', 'payment:app:query', 'payment:app:submit')
 AND m.deleted_flag = 0
WHERE r.tenant_id = 0
  AND r.role_code = 'PROJECT_MANAGER'
  AND r.deleted_flag = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm
      WHERE rm.tenant_id = r.tenant_id AND rm.role_id = r.id AND rm.menu_id = m.id
  );

INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT 228000200000000 + ROW_NUMBER() OVER (ORDER BY r.id, m.id), r.tenant_id, r.id, m.id
FROM sys_role r
JOIN sys_menu m
  ON m.tenant_id = r.tenant_id
 AND m.perms = 'payment:record:writeback'
 AND m.deleted_flag = 0
WHERE r.tenant_id = 0
  AND r.role_code = 'FINANCE'
  AND r.deleted_flag = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm
      WHERE rm.tenant_id = r.tenant_id AND rm.role_id = r.id AND rm.menu_id = m.id
  );
