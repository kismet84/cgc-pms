-- Register the independently assignable material dictionary delete authority for every tenant
-- that already owns the material dictionary menu.

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon,
     order_num, status, visible, created_by, updated_by, remark, created_at, updated_at, deleted_flag)
SELECT bounds.max_id + ROW_NUMBER() OVER (ORDER BY parent.tenant_id, parent.id),
       parent.tenant_id, parent.id, '删除材料', 'BUTTON', NULL, NULL,
       'material:dict:delete', NULL, 3, 'ENABLE', 0, 1, 1,
       'Material dictionary delete authority', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM sys_menu parent
CROSS JOIN (SELECT COALESCE(MAX(id), 0) AS max_id FROM sys_menu) bounds
WHERE parent.perms = 'material:dict:list' AND parent.deleted_flag = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu existing
      WHERE existing.tenant_id = parent.tenant_id
        AND existing.perms = 'material:dict:delete'
        AND existing.deleted_flag = 0
  );
