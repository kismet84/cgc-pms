-- Reconcile H2 with the MySQL permission and retired cost-subject facts from V219-V221 and V263.

MERGE INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon,
     order_num, status, visible, created_by, updated_by, remark, created_at, updated_at, deleted_flag)
KEY (id)
VALUES
    (21901, 0, 921, '新建变更签证', 'BUTTON', NULL, NULL, 'variation:order:add', NULL, 1, 'ENABLE', 0, 1, 1, 'MAINLINE-88', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (21902, 0, 921, '编辑变更签证', 'BUTTON', NULL, NULL, 'variation:order:edit', NULL, 2, 'ENABLE', 0, 1, 1, 'MAINLINE-88', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (21903, 0, 921, '删除变更签证', 'BUTTON', NULL, NULL, 'variation:order:delete', NULL, 3, 'ENABLE', 0, 1, 1, 'MAINLINE-88', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (21904, 0, 921, '编辑变更签证明细', 'BUTTON', NULL, NULL, 'variation:order:item:edit', NULL, 4, 'ENABLE', 0, 1, 1, 'MAINLINE-88', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (22001, 0, 933, '新建目标成本', 'BUTTON', NULL, NULL, 'cost:target:add', NULL, 1, 'ENABLE', 0, 1, 1, 'MAINLINE-88', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (22002, 0, 933, '编辑目标成本', 'BUTTON', NULL, NULL, 'cost:target:edit', NULL, 2, 'ENABLE', 0, 1, 1, 'MAINLINE-88', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (22003, 0, 933, '删除目标成本', 'BUTTON', NULL, NULL, 'cost:target:delete', NULL, 3, 'ENABLE', 0, 1, 1, 'MAINLINE-88', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (22004, 0, 933, '激活目标成本', 'BUTTON', NULL, NULL, 'cost:target:activate', NULL, 5, 'ENABLE', 0, 1, 1, 'MAINLINE-88', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (22101, 0, 932, '刷新成本汇总', 'BUTTON', NULL, NULL, 'cost:summary:refresh', NULL, 1, 'ENABLE', 0, 1, 1, 'MAINLINE-88', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

UPDATE sys_menu
SET parent_id = 933, order_num = 4, status = 'ENABLE', visible = 0, updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = 0 AND id = 608 AND perms = 'cost:target:submit' AND deleted_flag = 0;

MERGE INTO sys_role_menu (id, tenant_id, role_id, menu_id)
KEY (tenant_id, role_id, menu_id)
VALUES
    (219020921, 0, 2, 921), (219022901, 0, 2, 21901), (219022902, 0, 2, 21902),
    (219022903, 0, 2, 21903), (219022904, 0, 2, 21904), (219020605, 0, 2, 605),
    (219040921, 0, 4, 921), (219042901, 0, 4, 21901), (219042902, 0, 4, 21902),
    (219042903, 0, 4, 21903), (219042904, 0, 4, 21904), (219040605, 0, 4, 605),
    (219041090, 0, 4, 1090), (219041091, 0, 4, 1091), (219041092, 0, 4, 1092),
    (219040962, 0, 4, 962), (219040963, 0, 4, 963), (219040964, 0, 4, 964),
    (219040965, 0, 4, 965), (219040966, 0, 4, 966),
    (220020933, 0, 2, 933), (220020608, 0, 2, 608), (220022001, 0, 2, 22001),
    (220022002, 0, 2, 22002), (220022003, 0, 2, 22003), (220022004, 0, 2, 22004),
    (221020932, 0, 2, 932), (221022101, 0, 2, 22101);

DELETE FROM cost_subject
WHERE subject_code IN (
    '5401.02.05', '5401.02.06', '5401.04.06', '5401.04.10', '5401.04.11',
    '5401.04.12', '5401.04.13', '5401.04.15', '5401.04.16', '5401.04.17', '5401.04.18'
);
