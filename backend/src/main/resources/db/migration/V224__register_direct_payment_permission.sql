-- Direct payment is an enhanced capability, including for administrators.

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon,
     order_num, status, visible, created_by, updated_by, remark, created_at, updated_at, deleted_flag)
VALUES
    (22401, 0, 944, '直接付款', 'BUTTON', NULL, NULL, 'payment:direct', NULL,
     20, 'ENABLE', 0, 1, 1, 'PROJECT-PAYMENT-CLOSED-LOOP', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON DUPLICATE KEY UPDATE
    menu_name = VALUES(menu_name),
    parent_id = VALUES(parent_id),
    menu_type = VALUES(menu_type),
    perms = VALUES(perms),
    status = VALUES(status),
    visible = VALUES(visible),
    updated_at = CURRENT_TIMESTAMP;

INSERT IGNORE INTO sys_role_menu (id, tenant_id, role_id, menu_id) VALUES
    (224010001, 0, 1, 22401);
