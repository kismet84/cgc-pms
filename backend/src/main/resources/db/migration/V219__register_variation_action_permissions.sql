-- Register missing variation CRUD authorities and complete management-role access.
-- Generic file:* permissions stay unassigned; variation evidence uses business-scoped authorities.

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon,
     order_num, status, visible, created_by, updated_by, remark, created_at, updated_at, deleted_flag)
VALUES
    (21901, 0, 921, '新建变更签证', 'BUTTON', NULL, NULL, 'variation:order:add', NULL,
     1, 'ENABLE', 0, 1, 1, 'ISSUE-053-019', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (21902, 0, 921, '编辑变更签证', 'BUTTON', NULL, NULL, 'variation:order:edit', NULL,
     2, 'ENABLE', 0, 1, 1, 'ISSUE-053-019', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (21903, 0, 921, '删除变更签证', 'BUTTON', NULL, NULL, 'variation:order:delete', NULL,
     3, 'ENABLE', 0, 1, 1, 'ISSUE-053-019', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (21904, 0, 921, '编辑变更签证明细', 'BUTTON', NULL, NULL, 'variation:order:item:edit', NULL,
     4, 'ENABLE', 0, 1, 1, 'ISSUE-053-019', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON DUPLICATE KEY UPDATE
    menu_name = VALUES(menu_name),
    parent_id = VALUES(parent_id),
    menu_type = VALUES(menu_type),
    perms = VALUES(perms),
    status = VALUES(status),
    visible = VALUES(visible),
    updated_at = CURRENT_TIMESTAMP;

INSERT IGNORE INTO sys_role_menu (id, tenant_id, role_id, menu_id) VALUES
    (219020921, 0, 2, 921),
    (219022901, 0, 2, 21901),
    (219022902, 0, 2, 21902),
    (219022903, 0, 2, 21903),
    (219022904, 0, 2, 21904),
    (219020605, 0, 2, 605),
    (219040921, 0, 4, 921),
    (219042901, 0, 4, 21901),
    (219042902, 0, 4, 21902),
    (219042903, 0, 4, 21903),
    (219042904, 0, 4, 21904),
    (219040605, 0, 4, 605),
    (219041090, 0, 4, 1090),
    (219041091, 0, 4, 1091),
    (219041092, 0, 4, 1092),
    (219040962, 0, 4, 962),
    (219040963, 0, 4, 963),
    (219040964, 0, 4, 964),
    (219040965, 0, 4, 965),
    (219040966, 0, 4, 966);
