-- V71__repair_missing_approval_templates.sql (H2 version)
-- 修复：重新创建 7 个审批模板（PURCHASE_ORDER、MATERIAL_RECEIPT、SUB_MEASURE、SETTLEMENT、COST_TARGET、PURCHASE_REQUEST、PAY_REQUEST）
-- 说明：V63-V70 的累积修复，此处一次性重新确保存在
-- 幂等：INSERT SELECT WHERE NOT EXISTS，不包含 UPDATE

-- PURCHASE_ORDER 采购订单审批
INSERT INTO wf_template (id, tenant_id, template_code, template_name, business_type, enabled, amount_min, amount_max, condition_rule, form_schema, created_by, remark)
SELECT 50002, 0, 'TPL-PURCHASE-ORDER-001', '采购订单审批流程', 'PURCHASE_ORDER', 1, 0.00, 999999999.99, NULL, NULL, 1, '采购订单审批标准流程：项目经理 → 部门经理 → 总经理'
WHERE NOT EXISTS (SELECT 1 FROM wf_template WHERE id = 50002 AND deleted_flag = 0);
INSERT INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours)
SELECT 50201, 0, 50002, 'N1', '项目经理审批', 1, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type', 'USER', 'userId', 1), NULL, 1, 1, 48
WHERE NOT EXISTS (SELECT 1 FROM wf_template_node WHERE id = 50201 AND deleted_flag = 0);
INSERT INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours)
SELECT 50202, 0, 50002, 'N2', '部门经理审批', 2, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type', 'USER', 'userId', 1), NULL, 1, 1, 48
WHERE NOT EXISTS (SELECT 1 FROM wf_template_node WHERE id = 50202 AND deleted_flag = 0);
INSERT INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours)
SELECT 50203, 0, 50002, 'N3', '总经理审批', 3, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type', 'USER', 'userId', 1), NULL, 1, 1, 72
WHERE NOT EXISTS (SELECT 1 FROM wf_template_node WHERE id = 50203 AND deleted_flag = 0);

-- MATERIAL_RECEIPT 物料收货审批
INSERT INTO wf_template (id, tenant_id, template_code, template_name, business_type, enabled, amount_min, amount_max, condition_rule, form_schema, created_by, remark)
SELECT 50003, 0, 'TPL-MATERIAL-RECEIPT-001', '物料收货审批流程', 'MATERIAL_RECEIPT', 1, 0.00, 999999999.99, NULL, NULL, 1, '物料收货审批标准流程：项目经理 → 部门经理 → 总经理'
WHERE NOT EXISTS (SELECT 1 FROM wf_template WHERE id = 50003 AND deleted_flag = 0);
INSERT INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours)
SELECT 50301, 0, 50003, 'N1', '项目经理审批', 1, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type', 'USER', 'userId', 1), NULL, 1, 1, 48
WHERE NOT EXISTS (SELECT 1 FROM wf_template_node WHERE id = 50301 AND deleted_flag = 0);
INSERT INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours)
SELECT 50302, 0, 50003, 'N2', '部门经理审批', 2, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type', 'USER', 'userId', 1), NULL, 1, 1, 48
WHERE NOT EXISTS (SELECT 1 FROM wf_template_node WHERE id = 50302 AND deleted_flag = 0);
INSERT INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours)
SELECT 50303, 0, 50003, 'N3', '总经理审批', 3, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type', 'USER', 'userId', 1), NULL, 1, 1, 72
WHERE NOT EXISTS (SELECT 1 FROM wf_template_node WHERE id = 50303 AND deleted_flag = 0);

-- SUB_MEASURE 分包计量审批
INSERT INTO wf_template (id, tenant_id, template_code, template_name, business_type, enabled, amount_min, amount_max, condition_rule, form_schema, created_by, remark)
SELECT 50004, 0, 'TPL-SUB-MEASURE-001', '分包计量审批流程', 'SUB_MEASURE', 1, 0.00, 999999999.99, NULL, NULL, 1, '分包计量审批标准流程：项目经理 → 部门经理 → 总经理'
WHERE NOT EXISTS (SELECT 1 FROM wf_template WHERE id = 50004 AND deleted_flag = 0);
INSERT INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours)
SELECT 50401, 0, 50004, 'N1', '项目经理审批', 1, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type', 'USER', 'userId', 1), NULL, 1, 1, 48
WHERE NOT EXISTS (SELECT 1 FROM wf_template_node WHERE id = 50401 AND deleted_flag = 0);
INSERT INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours)
SELECT 50402, 0, 50004, 'N2', '部门经理审批', 2, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type', 'USER', 'userId', 1), NULL, 1, 1, 48
WHERE NOT EXISTS (SELECT 1 FROM wf_template_node WHERE id = 50402 AND deleted_flag = 0);
INSERT INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours)
SELECT 50403, 0, 50004, 'N3', '总经理审批', 3, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type', 'USER', 'userId', 1), NULL, 1, 1, 72
WHERE NOT EXISTS (SELECT 1 FROM wf_template_node WHERE id = 50403 AND deleted_flag = 0);

-- PAYMENT / PAY_REQUEST 付款申请审批
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

-- CT_CHANGE 合同变更审批
INSERT INTO wf_template (id, tenant_id, template_code, template_name, business_type, enabled, amount_min, amount_max, condition_rule, form_schema, created_by, remark)
SELECT 50007, 0, 'TPL-CONTRACT-CHANGE-001', '合同变更审批流程', 'CT_CHANGE', 1, 0.00, 999999999.99, NULL, NULL, 1, '合同变更审批标准流程：项目经理 → 部门经理 → 总经理'
WHERE NOT EXISTS (SELECT 1 FROM wf_template WHERE id = 50007 AND deleted_flag = 0);
INSERT INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours)
SELECT 50701, 0, 50007, 'N1', '项目经理审批', 1, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type', 'USER', 'userId', 1), NULL, 1, 1, 48
WHERE NOT EXISTS (SELECT 1 FROM wf_template_node WHERE id = 50701 AND deleted_flag = 0);
INSERT INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours)
SELECT 50702, 0, 50007, 'N2', '部门经理审批', 2, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type', 'USER', 'userId', 1), NULL, 1, 1, 48
WHERE NOT EXISTS (SELECT 1 FROM wf_template_node WHERE id = 50702 AND deleted_flag = 0);
INSERT INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours)
SELECT 50703, 0, 50007, 'N3', '总经理审批', 3, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type', 'USER', 'userId', 1), NULL, 1, 1, 72
WHERE NOT EXISTS (SELECT 1 FROM wf_template_node WHERE id = 50703 AND deleted_flag = 0);

-- SETTLEMENT 结算审批
INSERT INTO wf_template (id, tenant_id, template_code, template_name, business_type, enabled, amount_min, amount_max, condition_rule, form_schema, created_by, remark)
SELECT 50008, 0, 'TPL-SETTLEMENT-001', '结算审批流程', 'SETTLEMENT', 1, 0.00, 999999999.99, NULL, NULL, 1, '结算审批标准流程：项目经理 → 部门经理 → 总经理。审批通过后锁定结算单并回写合同结算金额。'
WHERE NOT EXISTS (SELECT 1 FROM wf_template WHERE id = 50008 AND deleted_flag = 0);
INSERT INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours)
SELECT 50801, 0, 50008, 'N1', '项目经理审批', 1, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type', 'USER', 'userId', 1), NULL, 1, 1, 48
WHERE NOT EXISTS (SELECT 1 FROM wf_template_node WHERE id = 50801 AND deleted_flag = 0);
INSERT INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours)
SELECT 50802, 0, 50008, 'N2', '部门经理审批', 2, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type', 'USER', 'userId', 1), NULL, 1, 1, 48
WHERE NOT EXISTS (SELECT 1 FROM wf_template_node WHERE id = 50802 AND deleted_flag = 0);
INSERT INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours)
SELECT 50803, 0, 50008, 'N3', '总经理审批', 3, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type', 'USER', 'userId', 1), NULL, 1, 1, 72
WHERE NOT EXISTS (SELECT 1 FROM wf_template_node WHERE id = 50803 AND deleted_flag = 0);

-- COST_TARGET 目标成本审批
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

-- PURCHASE_REQUEST 采购申请审批
INSERT INTO wf_template (id, tenant_id, template_code, template_name, business_type, enabled, amount_min, amount_max, condition_rule, form_schema, created_by, remark)
SELECT 50010, 0, 'TPL-PURCHASE-REQUEST-001', '采购申请审批流程', 'PURCHASE_REQUEST', 1, 0.00, 999999999.99, NULL, NULL, 1, '采购申请审批标准流程：项目经理 → 部门经理 → 总经理'
WHERE NOT EXISTS (SELECT 1 FROM wf_template WHERE id = 50010 AND deleted_flag = 0);
INSERT INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours)
SELECT 51001, 0, 50010, 'N1', '项目经理审批', 1, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type', 'USER', 'userId', 1), NULL, 1, 1, 48
WHERE NOT EXISTS (SELECT 1 FROM wf_template_node WHERE id = 51001 AND deleted_flag = 0);
INSERT INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours)
SELECT 51002, 0, 50010, 'N2', '部门经理审批', 2, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type', 'USER', 'userId', 1), NULL, 1, 1, 48
WHERE NOT EXISTS (SELECT 1 FROM wf_template_node WHERE id = 51002 AND deleted_flag = 0);
INSERT INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours)
SELECT 51003, 0, 50010, 'N3', '总经理审批', 3, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type', 'USER', 'userId', 1), NULL, 1, 1, 72
WHERE NOT EXISTS (SELECT 1 FROM wf_template_node WHERE id = 51003 AND deleted_flag = 0);
