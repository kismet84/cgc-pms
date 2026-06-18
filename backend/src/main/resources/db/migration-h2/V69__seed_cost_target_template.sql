-- V69__seed_cost_target_template.sql (H2 version)
-- 建筑工程总包项目全过程管理系统 - 目标成本审批模板
-- 幂等：INSERT SELECT WHERE NOT EXISTS

INSERT INTO wf_template (id, tenant_id, template_code, template_name, business_type, enabled, amount_min, amount_max, condition_rule, form_schema, created_by, remark)
SELECT 50009, 0, 'TPL-COST-TARGET-001', '目标成本审批流程', 'COST_TARGET', 1, 0.00, 999999999.99, NULL, NULL, 1, '目标成本审批标准流程：项目经理 → 部门经理 → 总经理'
WHERE NOT EXISTS (SELECT 1 FROM wf_template WHERE id = 50009 AND deleted_flag = 0);

INSERT INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours)
SELECT 50901, 0, 50009, 'N1', '项目经理审批', 1, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type', 'USER', 'userId', 1), NULL, 1, 1, 48
WHERE NOT EXISTS (SELECT 1 FROM wf_template_node WHERE id = 50901 AND deleted_flag = 0);

INSERT INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours)
SELECT 50902, 0, 50009, 'N2', '部门经理审批', 2, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type', 'USER', 'userId', 1), NULL, 1, 1, 48
WHERE NOT EXISTS (SELECT 1 FROM wf_template_node WHERE id = 50902 AND deleted_flag = 0);

INSERT INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours)
SELECT 50903, 0, 50009, 'N3', '总经理审批', 3, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type', 'USER', 'userId', 1), NULL, 1, 1, 72
WHERE NOT EXISTS (SELECT 1 FROM wf_template_node WHERE id = 50903 AND deleted_flag = 0);
