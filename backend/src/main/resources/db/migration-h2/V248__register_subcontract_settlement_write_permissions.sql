-- Register existing subcontract and settlement controller authorities in the permission catalog.

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon,
     order_num, status, visible, created_by, updated_by, remark, created_at, updated_at, deleted_flag)
SELECT 24801, 0, 942, '新增分包任务', 'BUTTON', NULL, NULL, 'subtask:add', NULL,
       1, 'ENABLE', 0, 1, 1, 'Subcontract task controller authority', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 0 AND perms = 'subtask:add' AND deleted_flag = 0);

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon,
     order_num, status, visible, created_by, updated_by, remark, created_at, updated_at, deleted_flag)
SELECT 24802, 0, 942, '编辑分包任务', 'BUTTON', NULL, NULL, 'subtask:edit', NULL,
       2, 'ENABLE', 0, 1, 1, 'Subcontract task controller authority', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 0 AND perms = 'subtask:edit' AND deleted_flag = 0);

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon,
     order_num, status, visible, created_by, updated_by, remark, created_at, updated_at, deleted_flag)
SELECT 24803, 0, 942, '删除分包任务', 'BUTTON', NULL, NULL, 'subtask:delete', NULL,
       3, 'ENABLE', 0, 1, 1, 'Subcontract task controller authority', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 0 AND perms = 'subtask:delete' AND deleted_flag = 0);

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon,
     order_num, status, visible, created_by, updated_by, remark, created_at, updated_at, deleted_flag)
SELECT 24804, 0, 943, '新增分包计量', 'BUTTON', NULL, NULL, 'subcontract:measure:add', NULL,
       1, 'ENABLE', 0, 1, 1, 'Subcontract measure controller authority', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 0 AND perms = 'subcontract:measure:add' AND deleted_flag = 0);

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon,
     order_num, status, visible, created_by, updated_by, remark, created_at, updated_at, deleted_flag)
SELECT 24805, 0, 943, '编辑分包计量', 'BUTTON', NULL, NULL, 'subcontract:measure:edit', NULL,
       2, 'ENABLE', 0, 1, 1, 'Subcontract measure controller authority', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 0 AND perms = 'subcontract:measure:edit' AND deleted_flag = 0);

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon,
     order_num, status, visible, created_by, updated_by, remark, created_at, updated_at, deleted_flag)
SELECT 24806, 0, 943, '删除分包计量', 'BUTTON', NULL, NULL, 'subcontract:measure:delete', NULL,
       3, 'ENABLE', 0, 1, 1, 'Subcontract measure controller authority', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 0 AND perms = 'subcontract:measure:delete' AND deleted_flag = 0);

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon,
     order_num, status, visible, created_by, updated_by, remark, created_at, updated_at, deleted_flag)
SELECT 24807, 0, 945, '新增结算', 'BUTTON', NULL, NULL, 'settlement:add', NULL,
       1, 'ENABLE', 0, 1, 1, 'Settlement controller authority', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 0 AND perms = 'settlement:add' AND deleted_flag = 0);

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon,
     order_num, status, visible, created_by, updated_by, remark, created_at, updated_at, deleted_flag)
SELECT 24808, 0, 945, '编辑结算', 'BUTTON', NULL, NULL, 'settlement:edit', NULL,
       2, 'ENABLE', 0, 1, 1, 'Settlement controller authority', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 0 AND perms = 'settlement:edit' AND deleted_flag = 0);

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon,
     order_num, status, visible, created_by, updated_by, remark, created_at, updated_at, deleted_flag)
SELECT 24809, 0, 945, '删除结算', 'BUTTON', NULL, NULL, 'settlement:delete', NULL,
       3, 'ENABLE', 0, 1, 1, 'Settlement controller authority', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 0 AND perms = 'settlement:delete' AND deleted_flag = 0);
