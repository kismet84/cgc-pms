-- V59__repair_variation_approval_template.sql
-- H2 对等迁移：规范化 VAR_ORDER 审批模板数据
-- V17 已在 H2 中播种正确数据，此迁移仅做修复性更新确保一致性

-- ============================================================
-- 1. 确保模板启用并规范化名称
-- ============================================================
UPDATE wf_template
SET template_name = '签证变更审批流程',
    enabled = 1,
    remark = '签证变更审批标准流程：项目经理 → 部门经理 → 总经理'
WHERE id = 50006
  AND tenant_id = 0
  AND business_type = 'VAR_ORDER'
  AND deleted_flag = 0;

-- ============================================================
-- 2. 规范化节点数据（确保数据一致性，即使 V17 已正确播种）
-- ============================================================
UPDATE wf_template_node
SET node_code = 'N1',
    node_name = '项目经理审批',
    node_order = 1,
    node_type = 'APPROVAL',
    approve_mode = 'SEQUENTIAL',
    approver_config = JSON_OBJECT('type', 'USER', 'userId', 1),
    allow_transfer = 1,
    allow_add_sign = 1,
    timeout_hours = 48
WHERE id = 50601 AND deleted_flag = 0;

UPDATE wf_template_node
SET node_code = 'N2',
    node_name = '部门经理审批',
    node_order = 2,
    node_type = 'APPROVAL',
    approve_mode = 'SEQUENTIAL',
    approver_config = JSON_OBJECT('type', 'USER', 'userId', 1),
    allow_transfer = 1,
    allow_add_sign = 1,
    timeout_hours = 48
WHERE id = 50602 AND deleted_flag = 0;

UPDATE wf_template_node
SET node_code = 'N3',
    node_name = '总经理审批',
    node_order = 3,
    node_type = 'APPROVAL',
    approve_mode = 'SEQUENTIAL',
    approver_config = JSON_OBJECT('type', 'USER', 'userId', 1),
    allow_transfer = 1,
    allow_add_sign = 1,
    timeout_hours = 72
WHERE id = 50603 AND deleted_flag = 0;

-- ============================================================
-- 3. 禁用其他 VAR_ORDER 模板（如果有多个则只保留 50006）
-- ============================================================
UPDATE wf_template
SET enabled = 0
WHERE tenant_id = 0
  AND business_type = 'VAR_ORDER'
  AND deleted_flag = 0
  AND id <> 50006;
