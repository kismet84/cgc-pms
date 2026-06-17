-- V70__seed_purchase_request_template.sql
-- 建筑工程总包项目全过程管理系统 - 采购申请审批模板
-- 数据库：MySQL 8.0+
-- 说明：V39 中 INSERT IGNORE 未生效（可能被手动删除），此处重新确保存在
-- 幂等：INSERT IGNORE + UPDATE

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

INSERT IGNORE INTO wf_template (id, tenant_id, template_code, template_name, business_type, enabled, amount_min, amount_max, condition_rule, form_schema, created_by, remark) VALUES
  (50010, 0, 'TPL-PURCHASE-REQUEST-001', '采购申请审批流程', 'PURCHASE_REQUEST', 1, 0.00, 999999999.99, NULL, NULL, 1, '采购申请审批标准流程：项目经理 → 部门经理 → 总经理');

UPDATE wf_template SET template_code='TPL-PURCHASE-REQUEST-001', template_name='采购申请审批流程', enabled=1 WHERE id=50010 AND business_type='PURCHASE_REQUEST' AND deleted_flag=0;

INSERT IGNORE INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours) VALUES
  (51001, 0, 50010, 'N1', '项目经理审批', 1, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type','USER','userId',1), NULL, 1, 1, 48),
  (51002, 0, 50010, 'N2', '部门经理审批', 2, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type','USER','userId',1), NULL, 1, 1, 48),
  (51003, 0, 50010, 'N3', '总经理审批',   3, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type','USER','userId',1), NULL, 1, 1, 72);

UPDATE wf_template_node SET node_name='项目经理审批', node_order=1, timeout_hours=48 WHERE id=51001 AND deleted_flag=0;
UPDATE wf_template_node SET node_name='部门经理审批', node_order=2, timeout_hours=48 WHERE id=51002 AND deleted_flag=0;
UPDATE wf_template_node SET node_name='总经理审批',   node_order=3, timeout_hours=72 WHERE id=51003 AND deleted_flag=0;

-- 禁用 V39 遗留的其他 PURCHASE_REQUEST 模板（仅保留 50010）
UPDATE wf_template SET enabled = 0 WHERE tenant_id = 0 AND business_type = 'PURCHASE_REQUEST' AND deleted_flag = 0 AND id <> 50010;

SET FOREIGN_KEY_CHECKS = 1;
