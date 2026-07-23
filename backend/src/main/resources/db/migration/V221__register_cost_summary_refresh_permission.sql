-- Separate read and refresh capabilities for immutable cost-summary snapshots.

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon,
     order_num, status, visible, created_by, updated_by, remark, created_at, updated_at, deleted_flag)
VALUES
    (22101, 0, 932, '刷新成本汇总', 'BUTTON', NULL, NULL, 'cost:summary:refresh', NULL,
     1, 'ENABLE', 0, 1, 1, 'ISSUE-053-021', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON DUPLICATE KEY UPDATE
    menu_name = VALUES(menu_name),
    parent_id = VALUES(parent_id),
    menu_type = VALUES(menu_type),
    perms = VALUES(perms),
    status = VALUES(status),
    visible = VALUES(visible),
    updated_at = CURRENT_TIMESTAMP;

INSERT IGNORE INTO sys_role_menu (id, tenant_id, role_id, menu_id) VALUES
    (221020932, 0, 2, 932),
    (221022101, 0, 2, 22101);
