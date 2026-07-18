INSERT INTO wf_template
    (id, tenant_id, template_code, template_name, business_type, enabled, amount_min, amount_max, created_by, remark)
SELECT 50020, 0, 'TPL-PROJECT-BUDGET-001', '项目预算审批流程', 'PROJECT_BUDGET', 1, 0.01, 999999999999.99, 1,
       '项目资金闭环：项目经理、成本经理、总经理三级审批'
WHERE NOT EXISTS (SELECT 1 FROM wf_template WHERE id = 50020);

INSERT INTO wf_template_node
    (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, allow_transfer, allow_add_sign, timeout_hours)
SELECT 52001, 0, 50020, 'N1', '项目经理审批', 1, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type','USER','userId',1), 1, 1, 48
WHERE NOT EXISTS (SELECT 1 FROM wf_template_node WHERE id = 52001);
INSERT INTO wf_template_node
    (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, allow_transfer, allow_add_sign, timeout_hours)
SELECT 52002, 0, 50020, 'N2', '成本经理审批', 2, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type','USER','userId',1), 1, 1, 48
WHERE NOT EXISTS (SELECT 1 FROM wf_template_node WHERE id = 52002);
INSERT INTO wf_template_node
    (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, allow_transfer, allow_add_sign, timeout_hours)
SELECT 52003, 0, 50020, 'N3', '总经理审批', 3, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type','USER','userId',1), 1, 1, 72
WHERE NOT EXISTS (SELECT 1 FROM wf_template_node WHERE id = 52003);

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
SELECT 1020, 0, 2, '项目预算', 'MENU', 'budget', 'project/budget/index', 'budget:query', 'fund', 8, 'ENABLE', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 1020);
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
SELECT 1021, 0, 1020, '新增预算', 'BUTTON', NULL, NULL, 'budget:add', NULL, 1, 'ENABLE', 1 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 1021);
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
SELECT 1022, 0, 1020, '编辑预算', 'BUTTON', NULL, NULL, 'budget:edit', NULL, 2, 'ENABLE', 1 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 1022);
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
SELECT 1023, 0, 1020, '删除预算', 'BUTTON', NULL, NULL, 'budget:delete', NULL, 3, 'ENABLE', 1 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 1023);
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
SELECT 1024, 0, 1020, '提交预算', 'BUTTON', NULL, NULL, 'budget:submit', NULL, 4, 'ENABLE', 1 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 1024);
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
SELECT 1025, 0, 201, '项目状态变更', 'BUTTON', NULL, NULL, 'project:status', NULL, 5, 'ENABLE', 0 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 1025);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 158000000 + r.id * 10000 + m.id, r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN (1020,1021,1022,1023,1024,1025) AND m.deleted_flag = 0
WHERE r.role_code IN ('SUPER_ADMIN','ADMIN','PROJECT_MANAGER','COST_MANAGER') AND r.deleted_flag = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
