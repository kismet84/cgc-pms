-- V63__seed_purchase_order_template.sql
-- 建筑工程总包项目全过程管理系统 - 采购订单审批模板
-- 数据库：MySQL 8.0+
-- 说明：确保 PURCHASE_ORDER 审批模板存在、启用、节点正确
-- 幂等：INSERT IGNORE + UPDATE，允许多次执行不产生副作用

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

INSERT IGNORE INTO wf_template (
    id, tenant_id, template_code, template_name, business_type, enabled,
    amount_min, amount_max, condition_rule, form_schema, created_by, remark
) VALUES (
    50002, 0, 'TPL-PURCHASE-ORDER-001', '采购订单审批流程', 'PURCHASE_ORDER', 1,
    0.00, 999999999.99, NULL, NULL, 1, '采购订单审批标准流程：项目经理 → 部门经理 → 总经理'
);

UPDATE wf_template
SET template_code = 'TPL-PURCHASE-ORDER-001',
    template_name = '采购订单审批流程',
    enabled = 1,
    remark = '采购订单审批标准流程：项目经理 → 部门经理 → 总经理'
WHERE id = 50002 AND tenant_id = 0 AND business_type = 'PURCHASE_ORDER' AND deleted_flag = 0;

INSERT IGNORE INTO wf_template_node (
    id, tenant_id, template_id, node_code, node_name, node_order, node_type,
    approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours
) VALUES
    (50201, 0, 50002, 'N1', '项目经理审批', 1, 'APPROVAL', 'SEQUENTIAL',
     JSON_OBJECT('type', 'USER', 'userId', 1), NULL, 1, 1, 48),
    (50202, 0, 50002, 'N2', '部门经理审批', 2, 'APPROVAL', 'SEQUENTIAL',
     JSON_OBJECT('type', 'USER', 'userId', 1), NULL, 1, 1, 48),
    (50203, 0, 50002, 'N3', '总经理审批',   3, 'APPROVAL', 'SEQUENTIAL',
     JSON_OBJECT('type', 'USER', 'userId', 1), NULL, 1, 1, 72);

UPDATE wf_template_node SET template_id=50002, node_code='N1', node_name='项目经理审批', node_order=1, node_type='APPROVAL', approve_mode='SEQUENTIAL', approver_config=JSON_OBJECT('type','USER','userId',1), allow_transfer=1, allow_add_sign=1, timeout_hours=48 WHERE id=50201 AND deleted_flag=0;
UPDATE wf_template_node SET template_id=50002, node_code='N2', node_name='部门经理审批', node_order=2, node_type='APPROVAL', approve_mode='SEQUENTIAL', approver_config=JSON_OBJECT('type','USER','userId',1), allow_transfer=1, allow_add_sign=1, timeout_hours=48 WHERE id=50202 AND deleted_flag=0;
UPDATE wf_template_node SET template_id=50002, node_code='N3', node_name='总经理审批', node_order=3, node_type='APPROVAL', approve_mode='SEQUENTIAL', approver_config=JSON_OBJECT('type','USER','userId',1), allow_transfer=1, allow_add_sign=1, timeout_hours=72 WHERE id=50203 AND deleted_flag=0;

SET FOREIGN_KEY_CHECKS = 1;
