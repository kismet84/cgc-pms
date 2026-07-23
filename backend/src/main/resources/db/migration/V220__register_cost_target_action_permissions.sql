-- Register target-cost actions and grant the ordinary project-manager role the complete lifecycle.

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon,
     order_num, status, visible, created_by, updated_by, remark, created_at, updated_at, deleted_flag)
VALUES
    (22001, 0, 933, '新建目标成本', 'BUTTON', NULL, NULL, 'cost:target:add', NULL,
     1, 'ENABLE', 0, 1, 1, 'ISSUE-053-020', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (22002, 0, 933, '编辑目标成本', 'BUTTON', NULL, NULL, 'cost:target:edit', NULL,
     2, 'ENABLE', 0, 1, 1, 'ISSUE-053-020', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (22003, 0, 933, '删除目标成本', 'BUTTON', NULL, NULL, 'cost:target:delete', NULL,
     3, 'ENABLE', 0, 1, 1, 'ISSUE-053-020', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (22004, 0, 933, '激活目标成本', 'BUTTON', NULL, NULL, 'cost:target:activate', NULL,
     5, 'ENABLE', 0, 1, 1, 'ISSUE-053-020', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON DUPLICATE KEY UPDATE
    menu_name = VALUES(menu_name),
    parent_id = VALUES(parent_id),
    menu_type = VALUES(menu_type),
    perms = VALUES(perms),
    status = VALUES(status),
    visible = VALUES(visible),
    updated_at = CURRENT_TIMESTAMP;

UPDATE sys_menu
SET parent_id=933, order_num=4, status='ENABLE', visible=0, updated_at=CURRENT_TIMESTAMP
WHERE tenant_id=0 AND id=608 AND perms='cost:target:submit' AND deleted_flag=0;

INSERT IGNORE INTO sys_role_menu (id, tenant_id, role_id, menu_id) VALUES
    (220020933, 0, 2, 933),
    (220020608, 0, 2, 608),
    (220022001, 0, 2, 22001),
    (220022002, 0, 2, 22002),
    (220022003, 0, 2, 22003),
    (220022004, 0, 2, 22004);
