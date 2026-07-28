-- Register authority required by the requisition create/edit form and controller.

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon,
     order_num, status, visible, created_by, updated_by, remark, created_at, updated_at, deleted_flag)
SELECT 24701, 0, 917, '编辑领料申请', 'BUTTON', NULL, NULL, 'requisition:edit', NULL,
       2, 'ENABLE', 0, 1, 1, 'Requisition create/edit contract alignment', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE tenant_id = 0 AND perms = 'requisition:edit' AND deleted_flag = 0
);
