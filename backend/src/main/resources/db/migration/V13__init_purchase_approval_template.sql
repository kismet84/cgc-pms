-- V13__init_purchase_approval_template.sql
-- 建筑工程总包项目全过程管理系统 - 采购订单审批模板
-- 数据库：MySQL 8.0+
-- 说明：采购订单审批流程，3 个顺序审批节点

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 采购订单审批模板（PURCHASE_ORDER）
-- ============================================================
INSERT IGNORE INTO wf_template (id, tenant_id, template_code, template_name, business_type, enabled, amount_min, amount_max, condition_rule, form_schema, created_by, remark) VALUES
(50002, 0, 'TPL-PURCHASE-ORDER-001', '采购订单审批流程', 'PURCHASE_ORDER', 1, 0.00, 999999999.99, NULL, NULL, 1, '采购订单审批标准流程：项目经理 → 部门经理 → 总经理');

-- ============================================================
-- 采购订单审批模板节点（3 个顺序审批节点）
-- ============================================================
INSERT IGNORE INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours) VALUES
(50201, 0, 50002, 'N1', '项目经理审批', 1, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type', 'USER', 'userId', 1), NULL, 1, 1, 48),
(50202, 0, 50002, 'N2', '部门经理审批', 2, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type', 'USER', 'userId', 1), NULL, 1, 1, 48),
(50203, 0, 50002, 'N3', '总经理审批',   3, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type', 'USER', 'userId', 1), NULL, 1, 1, 72);

SET FOREIGN_KEY_CHECKS = 1;
