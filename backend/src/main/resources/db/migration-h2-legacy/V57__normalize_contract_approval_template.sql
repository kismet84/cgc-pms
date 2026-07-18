-- V57__normalize_contract_approval_template.sql
-- Keep H2 CONTRACT_APPROVAL aligned with MySQL.

UPDATE wf_template
SET template_code = 'TPL-CONTRACT-APPROVAL-001',
    template_name = '合同审批流程',
    enabled = 1,
    remark = '合同审批标准流程：项目经理 → 部门经理 → 总经理'
WHERE id = 50001
  AND tenant_id = 0
  AND business_type = 'CONTRACT_APPROVAL'
  AND deleted_flag = 0;

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
