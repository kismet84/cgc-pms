-- Expose the shared business-attachment read authority in the permission catalog.

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon,
     order_num, status, visible, created_by, updated_by, remark, created_at, updated_at, deleted_flag)
SELECT 24601, 0, 0, '查看业务附件', 'BUTTON', NULL, NULL, 'file:query', NULL,
       31, 'ENABLE', 0, 1, 1, 'Shared business attachment read authority', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE tenant_id = 0 AND perms = 'file:query' AND deleted_flag = 0
);
