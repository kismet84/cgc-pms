-- V66__seed_payment_template.sql (H2 version)
-- 建筑工程总包项目全过程管理系统 - 付款申请审批模板
-- 幂等：INSERT SELECT WHERE NOT EXISTS

INSERT INTO wf_template (id, tenant_id, template_code, template_name, business_type, enabled, amount_min, amount_max, condition_rule, form_schema, created_by, remark)
SELECT 50005, 0, 'TPL-PAYMENT-APPROVAL-001', '付款申请审批流程', 'PAY_REQUEST', 1, 0.00, 999999999.99, NULL, NULL, 1, '付款申请审批标准流程：项目经理 → 部门经理 → 总经理'
WHERE NOT EXISTS (SELECT 1 FROM wf_template WHERE id = 50005 AND deleted_flag = 0);

INSERT INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours)
SELECT 50501, 0, 50005, 'N1', '项目经理审批', 1, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type', 'USER', 'userId', 1), NULL, 1, 1, 48
WHERE NOT EXISTS (SELECT 1 FROM wf_template_node WHERE id = 50501 AND deleted_flag = 0);

INSERT INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours)
SELECT 50502, 0, 50005, 'N2', '部门经理审批', 2, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type', 'USER', 'userId', 1), NULL, 1, 1, 48
WHERE NOT EXISTS (SELECT 1 FROM wf_template_node WHERE id = 50502 AND deleted_flag = 0);

INSERT INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours)
SELECT 50503, 0, 50005, 'N3', '总经理审批', 3, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type', 'USER', 'userId', 1), NULL, 1, 1, 72
WHERE NOT EXISTS (SELECT 1 FROM wf_template_node WHERE id = 50503 AND deleted_flag = 0);
