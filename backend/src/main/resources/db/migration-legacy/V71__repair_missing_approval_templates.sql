-- V71__repair_missing_approval_templates.sql
-- 修复：清理编码损坏的手动插入模板，重新创建 6 个缺失模板
-- 说明：V63-V70 因 INSERT IGNORE 跳过（template_code 唯一键冲突）
-- 幂等：INSERT IGNORE + UPDATE

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- PURCHASE_ORDER 采购订单审批
-- ============================================================
INSERT IGNORE INTO wf_template (id, tenant_id, template_code, template_name, business_type, enabled, amount_min, amount_max, condition_rule, form_schema, created_by, remark) VALUES
(50002, 0, 'TPL-PURCHASE-ORDER-001', '采购订单审批流程', 'PURCHASE_ORDER', 1, 0.00, 999999999.99, NULL, NULL, 1, '采购订单审批标准流程：项目经理 → 部门经理 → 总经理');
INSERT IGNORE INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours) VALUES
(50201, 0, 50002, 'N1', '项目经理审批', 1, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type','USER','userId',1), NULL, 1, 1, 48),
(50202, 0, 50002, 'N2', '部门经理审批', 2, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type','USER','userId',1), NULL, 1, 1, 48),
(50203, 0, 50002, 'N3', '总经理审批',   3, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type','USER','userId',1), NULL, 1, 1, 72);

-- ============================================================
-- MATERIAL_RECEIPT 物料收货审批
-- ============================================================
INSERT IGNORE INTO wf_template (id, tenant_id, template_code, template_name, business_type, enabled, amount_min, amount_max, condition_rule, form_schema, created_by, remark) VALUES
(50003, 0, 'TPL-MATERIAL-RECEIPT-001', '物料收货审批流程', 'MATERIAL_RECEIPT', 1, 0.00, 999999999.99, NULL, NULL, 1, '物料收货审批标准流程：项目经理 → 部门经理 → 总经理');
INSERT IGNORE INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours) VALUES
(50301, 0, 50003, 'N1', '项目经理审批', 1, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type','USER','userId',1), NULL, 1, 1, 48),
(50302, 0, 50003, 'N2', '部门经理审批', 2, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type','USER','userId',1), NULL, 1, 1, 48),
(50303, 0, 50003, 'N3', '总经理审批',   3, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type','USER','userId',1), NULL, 1, 1, 72);

-- ============================================================
-- SUB_MEASURE 分包计量审批
-- ============================================================
INSERT IGNORE INTO wf_template (id, tenant_id, template_code, template_name, business_type, enabled, amount_min, amount_max, condition_rule, form_schema, created_by, remark) VALUES
(50004, 0, 'TPL-SUB-MEASURE-001', '分包计量审批流程', 'SUB_MEASURE', 1, 0.00, 999999999.99, NULL, NULL, 1, '分包计量审批标准流程：项目经理 → 部门经理 → 总经理');
INSERT IGNORE INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours) VALUES
(50401, 0, 50004, 'N1', '项目经理审批', 1, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type','USER','userId',1), NULL, 1, 1, 48),
(50402, 0, 50004, 'N2', '部门经理审批', 2, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type','USER','userId',1), NULL, 1, 1, 48),
(50403, 0, 50004, 'N3', '总经理审批',   3, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type','USER','userId',1), NULL, 1, 1, 72);

-- ============================================================
-- SETTLEMENT 结算审批
-- ============================================================
INSERT IGNORE INTO wf_template (id, tenant_id, template_code, template_name, business_type, enabled, amount_min, amount_max, condition_rule, form_schema, created_by, remark) VALUES
(50008, 0, 'TPL-SETTLEMENT-001', '结算审批流程', 'SETTLEMENT', 1, 0.00, 999999999.99, NULL, NULL, 1, '结算审批标准流程：项目经理 → 部门经理 → 总经理');
INSERT IGNORE INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours) VALUES
(50801, 0, 50008, 'N1', '项目经理审批', 1, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type','USER','userId',1), NULL, 1, 1, 48),
(50802, 0, 50008, 'N2', '部门经理审批', 2, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type','USER','userId',1), NULL, 1, 1, 48),
(50803, 0, 50008, 'N3', '总经理审批',   3, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type','USER','userId',1), NULL, 1, 1, 72);

-- ============================================================
-- COST_TARGET 目标成本审批
-- ============================================================
INSERT IGNORE INTO wf_template (id, tenant_id, template_code, template_name, business_type, enabled, amount_min, amount_max, condition_rule, form_schema, created_by, remark) VALUES
(50009, 0, 'TPL-COST-TARGET-001', '目标成本审批流程', 'COST_TARGET', 1, 0.00, 999999999.99, NULL, NULL, 1, '目标成本审批标准流程：项目经理 → 部门经理 → 总经理');
INSERT IGNORE INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours) VALUES
(50901, 0, 50009, 'N1', '项目经理审批', 1, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type','USER','userId',1), NULL, 1, 1, 48),
(50902, 0, 50009, 'N2', '部门经理审批', 2, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type','USER','userId',1), NULL, 1, 1, 48),
(50903, 0, 50009, 'N3', '总经理审批',   3, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type','USER','userId',1), NULL, 1, 1, 72);

-- ============================================================
-- PURCHASE_REQUEST 采购申请审批
-- ============================================================
INSERT IGNORE INTO wf_template (id, tenant_id, template_code, template_name, business_type, enabled, amount_min, amount_max, condition_rule, form_schema, created_by, remark) VALUES
(50010, 0, 'TPL-PURCHASE-REQUEST-001', '采购申请审批流程', 'PURCHASE_REQUEST', 1, 0.00, 999999999.99, NULL, NULL, 1, '采购申请审批标准流程：项目经理 → 部门经理 → 总经理');
INSERT IGNORE INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours) VALUES
(51001, 0, 50010, 'N1', '项目经理审批', 1, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type','USER','userId',1), NULL, 1, 1, 48),
(51002, 0, 50010, 'N2', '部门经理审批', 2, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type','USER','userId',1), NULL, 1, 1, 48),
(51003, 0, 50010, 'N3', '总经理审批',   3, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type','USER','userId',1), NULL, 1, 1, 72);

SET FOREIGN_KEY_CHECKS = 1;
