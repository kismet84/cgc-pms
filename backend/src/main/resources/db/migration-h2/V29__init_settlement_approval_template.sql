-- V29__init_settlement_approval_template.sql
-- 建筑工程总包项目全过程管理系统 - 结算审批模板 + 合同结算金额字段
-- 数据库：MySQL 8.0+
-- 说明：结算审批流程，3 个顺序审批节点；ct_contract 新增 settlement_amount 用于审批通过回写

-- ============================================================
-- A. ct_contract 新增结算金额字段（审批通过后回写）
-- ============================================================
ALTER TABLE ct_contract
    ADD COLUMN settlement_amount DECIMAL(18,2) NULL;

-- ============================================================
-- B. 结算审批模板（SETTLEMENT）
-- ============================================================
INSERT INTO wf_template (id, tenant_id, template_code, template_name, business_type, enabled, amount_min, amount_max, condition_rule, form_schema, created_by, remark) VALUES
(50008, 0, 'TPL-SETTLEMENT-001', '结算审批流程', 'SETTLEMENT', 1, 0.00, 999999999.99, NULL, NULL, 1, '结算审批标准流程：项目经理 → 部门经理 → 总经理。审批通过后锁定结算单并回写合同结算金额。');

-- ============================================================
-- C. 结算审批模板节点（3 个顺序审批节点）
-- ============================================================
INSERT INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours) VALUES
(50801, 0, 50008, 'N1', '项目经理审批', 1, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type', 'USER', 'userId', 1), NULL, 1, 1, 48),
(50802, 0, 50008, 'N2', '部门经理审批', 2, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type', 'USER', 'userId', 1), NULL, 1, 1, 48),
(50803, 0, 50008, 'N3', '总经理审批',   3, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type', 'USER', 'userId', 1), NULL, 1, 1, 72);
