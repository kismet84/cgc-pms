-- V59__repair_variation_approval_template.sql
-- 建筑工程总包项目全过程管理系统 - 修复签证变更审批模板
-- 数据库：MySQL 8.0+
-- 说明：确保 VAR_ORDER 审批模板存在、启用、节点正确，修复 V17 数据漂移
-- 幂等：INSERT IGNORE + UPDATE，允许多次执行不产生副作用

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 1. 确保模板存在（INSERT IGNORE 幂等）
-- ============================================================
INSERT IGNORE INTO wf_template (
    id, tenant_id, template_code, template_name, business_type, enabled,
    amount_min, amount_max, condition_rule, form_schema, created_by, remark
) VALUES (
    50006, 0, 'TPL-VARIATION-ORDER-001', '签证变更审批流程', 'VAR_ORDER', 1,
    0.00, 999999999.99, NULL, NULL, 1, '签证变更审批标准流程：项目经理 → 部门经理 → 总经理'
);

-- ============================================================
-- 2. 确保模板启用并规范化名称（如果已存在但被禁用或名称漂移）
-- ============================================================
UPDATE wf_template
SET template_code = 'TPL-VARIATION-ORDER-001',
    template_name = '签证变更审批流程',
    enabled = 1,
    remark = '签证变更审批标准流程：项目经理 → 部门经理 → 总经理'
WHERE id = 50006
  AND tenant_id = 0
  AND business_type = 'VAR_ORDER'
  AND deleted_flag = 0;

-- ============================================================
-- 3. 确保三个节点存在（INSERT IGNORE 幂等）
-- ============================================================
INSERT IGNORE INTO wf_template_node (
    id, tenant_id, template_id, node_code, node_name, node_order, node_type,
    approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours
) VALUES
    (50601, 0, 50006, 'N1', '项目经理审批', 1, 'APPROVAL', 'SEQUENTIAL',
     JSON_OBJECT('type', 'USER', 'userId', 1), NULL, 1, 1, 48),
    (50602, 0, 50006, 'N2', '部门经理审批', 2, 'APPROVAL', 'SEQUENTIAL',
     JSON_OBJECT('type', 'USER', 'userId', 1), NULL, 1, 1, 48),
    (50603, 0, 50006, 'N3', '总经理审批',   3, 'APPROVAL', 'SEQUENTIAL',
     JSON_OBJECT('type', 'USER', 'userId', 1), NULL, 1, 1, 72);

-- ============================================================
-- 4. 修复节点数据（如果节点存在但数据漂移）
-- ============================================================
UPDATE wf_template_node
SET template_id = 50006,
    node_code = 'N1',
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
SET template_id = 50006,
    node_code = 'N2',
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
SET template_id = 50006,
    node_code = 'N3',
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
-- 5. 禁用其他 VAR_ORDER 模板（如果有多个则只保留 50006）
-- ============================================================
UPDATE wf_template
SET enabled = 0
WHERE tenant_id = 0
  AND business_type = 'VAR_ORDER'
  AND deleted_flag = 0
  AND id <> 50006;

SET FOREIGN_KEY_CHECKS = 1;
