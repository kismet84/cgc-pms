-- 第55条资金支出闭环：注册付款申请新增、编辑权限并授予项目经理。

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon,
     order_num, status, visible, created_by, updated_by, remark, created_at, updated_at, deleted_flag)
SELECT 23101, 0, 944, '付款申请新增', 'BUTTON', NULL, NULL, 'payment:app:add', NULL,
       10, 'ENABLE', 0, 1, 1, 'PROJECT-PAYMENT-CLOSED-LOOP', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE tenant_id = 0 AND perms = 'payment:app:add' AND deleted_flag = 0
);

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon,
     order_num, status, visible, created_by, updated_by, remark, created_at, updated_at, deleted_flag)
SELECT 23102, 0, 944, '付款申请编辑', 'BUTTON', NULL, NULL, 'payment:app:edit', NULL,
       11, 'ENABLE', 0, 1, 1, 'PROJECT-PAYMENT-CLOSED-LOOP', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE tenant_id = 0 AND perms = 'payment:app:edit' AND deleted_flag = 0
);

INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT 231000100000000 + ROW_NUMBER() OVER (ORDER BY r.id, m.id), r.tenant_id, r.id, m.id
FROM sys_role r
JOIN sys_menu m
  ON m.tenant_id = r.tenant_id
 AND m.perms IN ('payment:app:add', 'payment:app:edit')
 AND m.deleted_flag = 0
WHERE r.tenant_id = 0
  AND r.role_code = 'PROJECT_MANAGER'
  AND r.deleted_flag = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm
      WHERE rm.tenant_id = r.tenant_id AND rm.role_id = r.id AND rm.menu_id = m.id
  );
