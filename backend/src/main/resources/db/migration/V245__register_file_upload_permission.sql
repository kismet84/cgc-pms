-- Expose the shared business-attachment upload authority in the permission catalog.

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon,
     order_num, status, visible, created_by, updated_by, remark, created_at, updated_at, deleted_flag)
SELECT 24501, 0, 0, '上传业务附件', 'BUTTON', NULL, NULL, 'file:upload', NULL,
       30, 'ENABLE', 0, 1, 1, 'Shared business attachment authority', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE tenant_id = 0 AND perms = 'file:upload' AND deleted_flag = 0
);
