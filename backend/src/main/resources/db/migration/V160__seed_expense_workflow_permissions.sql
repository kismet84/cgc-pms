INSERT IGNORE INTO wf_template
    (id, tenant_id, template_code, template_name, business_type, enabled, amount_min, amount_max, created_by, remark)
VALUES
    (50021, 0, 'TPL-EXPENSE-001', '费用申请审批流程', 'EXPENSE', 1, 0.01, 999999999999.99, 1,
     '项目资金闭环：项目经理、成本经理、财务经理三级审批');

INSERT IGNORE INTO wf_template_node
    (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, allow_transfer, allow_add_sign, timeout_hours)
VALUES
    (52101, 0, 50021, 'N1', '项目经理审批', 1, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type','USER','userId',1), 1, 1, 48),
    (52102, 0, 50021, 'N2', '成本经理审批', 2, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type','USER','userId',1), 1, 1, 48),
    (52103, 0, 50021, 'N3', '财务经理审批', 3, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type','USER','userId',1), 1, 1, 48);

INSERT IGNORE INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
VALUES
    (1030, 0, 2, '费用申请', 'MENU', 'expense', 'expense/index', 'expense:query', 'receipt', 9, 'ENABLE', 1),
    (1031, 0, 1030, '新增费用申请', 'BUTTON', NULL, NULL, 'expense:add', NULL, 1, 'ENABLE', 1),
    (1032, 0, 1030, '编辑费用申请', 'BUTTON', NULL, NULL, 'expense:edit', NULL, 2, 'ENABLE', 1),
    (1033, 0, 1030, '删除费用申请', 'BUTTON', NULL, NULL, 'expense:delete', NULL, 3, 'ENABLE', 1),
    (1034, 0, 1030, '提交费用申请', 'BUTTON', NULL, NULL, 'expense:submit', NULL, 4, 'ENABLE', 1);

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id)
SELECT 160000000 + r.id * 10000 + m.id, r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN (1030,1031,1032,1033,1034) AND m.deleted_flag = 0
WHERE r.role_code IN ('SUPER_ADMIN','ADMIN','PROJECT_MANAGER','COST_MANAGER','FINANCE') AND r.deleted_flag = 0;
