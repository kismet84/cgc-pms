MERGE INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon,
     order_num, status, visible, created_by, updated_by, remark, created_at, updated_at, deleted_flag)
KEY (id)
VALUES
    (22401, 0, 944, '直接付款', 'BUTTON', NULL, NULL, 'payment:direct', NULL,
     20, 'ENABLE', 0, 1, 1, 'PROJECT-PAYMENT-CLOSED-LOOP', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

MERGE INTO sys_role_menu (id, tenant_id, role_id, menu_id)
KEY (id)
VALUES (224010001, 0, 1, 22401);
