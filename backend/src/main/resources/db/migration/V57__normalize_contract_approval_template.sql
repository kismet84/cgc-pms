-- V57__normalize_contract_approval_template.sql
-- Keep a single enabled CONTRACT_APPROVAL template and repair node names.

SET NAMES utf8mb4;

INSERT IGNORE INTO wf_template (
    id, tenant_id, template_code, template_name, business_type, enabled,
    amount_min, amount_max, condition_rule, form_schema, created_by, remark
)
VALUES (
    50001, 0, 'TPL-CONTRACT-APPROVAL-001', '合同审批流程', 'CONTRACT_APPROVAL', 1,
    0.00, 999999999.99, NULL, NULL, 1, '合同审批标准流程：项目经理 → 部门经理 → 总经理'
);

UPDATE wf_template
SET template_code = 'TPL-CONTRACT-APPROVAL-001',
    template_name = '合同审批流程',
    enabled = 1,
    remark = '合同审批标准流程：项目经理 → 部门经理 → 总经理'
WHERE id = 50001
  AND tenant_id = 0
  AND business_type = 'CONTRACT_APPROVAL'
  AND deleted_flag = 0;

INSERT IGNORE INTO wf_template_node (
    id, tenant_id, template_id, node_code, node_name, node_order, node_type,
    approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours
)
VALUES
    (50101, 0, 50001, 'N1', '项目经理审批', 1, 'APPROVAL', 'SEQUENTIAL',
     JSON_OBJECT('type', 'USER', 'userId', 1), NULL, 1, 1, 48),
    (50102, 0, 50001, 'N2', '部门经理审批', 2, 'APPROVAL', 'SEQUENTIAL',
     JSON_OBJECT('type', 'USER', 'userId', 1), NULL, 1, 1, 48),
    (50103, 0, 50001, 'N3', '总经理审批', 3, 'APPROVAL', 'SEQUENTIAL',
     JSON_OBJECT('type', 'USER', 'userId', 1), NULL, 1, 1, 72);

UPDATE wf_template_node
SET template_id = 50001,
    node_code = 'N1',
    node_name = '项目经理审批',
    node_order = 1,
    node_type = 'APPROVAL',
    approve_mode = 'SEQUENTIAL',
    approver_config = JSON_OBJECT('type', 'USER', 'userId', 1),
    allow_transfer = 1,
    allow_add_sign = 1,
    timeout_hours = 48
WHERE id = 50101 AND deleted_flag = 0;

UPDATE wf_template_node
SET template_id = 50001,
    node_code = 'N2',
    node_name = '部门经理审批',
    node_order = 2,
    node_type = 'APPROVAL',
    approve_mode = 'SEQUENTIAL',
    approver_config = JSON_OBJECT('type', 'USER', 'userId', 1),
    allow_transfer = 1,
    allow_add_sign = 1,
    timeout_hours = 48
WHERE id = 50102 AND deleted_flag = 0;

UPDATE wf_template_node
SET template_id = 50001,
    node_code = 'N3',
    node_name = '总经理审批',
    node_order = 3,
    node_type = 'APPROVAL',
    approve_mode = 'SEQUENTIAL',
    approver_config = JSON_OBJECT('type', 'USER', 'userId', 1),
    allow_transfer = 1,
    allow_add_sign = 1,
    timeout_hours = 72
WHERE id = 50103 AND deleted_flag = 0;

UPDATE wf_template
SET enabled = 0
WHERE tenant_id = 0
  AND business_type = 'CONTRACT_APPROVAL'
  AND deleted_flag = 0
  AND id <> 50001;
