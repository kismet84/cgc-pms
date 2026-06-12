-- V15__init_sub_measure_approval_template.sql
-- 建筑工程总包项目全过程管理系统 - 分包计量审批模板
-- 数据库：MySQL 8.0+
-- 说明：分包计量审批流程，3 个顺序审批节点

-- ============================================================
-- 分包计量审批模板（SUB_MEASURE）
-- ============================================================
INSERT INTO wf_template (id, tenant_id, template_code, template_name, business_type, enabled, amount_min, amount_max, condition_rule, form_schema, created_by, remark) VALUES
(50004, 0, 'TPL-SUB-MEASURE-001', '分包计量审批流程', 'SUB_MEASURE', 1, 0.00, 999999999.99, NULL, NULL, 1, '分包计量审批标准流程：项目经理 → 部门经理 → 总经理');

-- ============================================================
-- 分包计量审批模板节点（3 个顺序审批节点）
-- ============================================================
INSERT INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours) VALUES
(50401, 0, 50004, 'N1', '项目经理审批', 1, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type', 'USER', 'userId', 1), NULL, 1, 1, 48),
(50402, 0, 50004, 'N2', '部门经理审批', 2, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type', 'USER', 'userId', 1), NULL, 1, 1, 48),
(50403, 0, 50004, 'N3', '总经理审批',   3, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type', 'USER', 'userId', 1), NULL, 1, 1, 72);
