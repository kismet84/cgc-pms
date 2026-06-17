-- V68__seed_settlement_template.sql
-- 建筑工程总包项目全过程管理系统 - 结算审批模板
-- 数据库：MySQL 8.0+
-- 幂等：INSERT IGNORE + UPDATE

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

INSERT IGNORE INTO wf_template (id, tenant_id, template_code, template_name, business_type, enabled, amount_min, amount_max, condition_rule, form_schema, created_by, remark) VALUES
  (50008, 0, 'TPL-SETTLEMENT-001', '结算审批流程', 'SETTLEMENT', 1, 0.00, 999999999.99, NULL, NULL, 1, '结算审批标准流程：项目经理 → 部门经理 → 总经理。审批通过后锁定结算单并回写合同结算金额。');

UPDATE wf_template SET template_code='TPL-SETTLEMENT-001', template_name='结算审批流程', enabled=1 WHERE id=50008 AND business_type='SETTLEMENT' AND deleted_flag=0;

INSERT IGNORE INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours) VALUES
  (50801, 0, 50008, 'N1', '项目经理审批', 1, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type','USER','userId',1), NULL, 1, 1, 48),
  (50802, 0, 50008, 'N2', '部门经理审批', 2, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type','USER','userId',1), NULL, 1, 1, 48),
  (50803, 0, 50008, 'N3', '总经理审批',   3, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type','USER','userId',1), NULL, 1, 1, 72);

UPDATE wf_template_node SET node_name='项目经理审批', node_order=1, timeout_hours=48 WHERE id=50801 AND deleted_flag=0;
UPDATE wf_template_node SET node_name='部门经理审批', node_order=2, timeout_hours=48 WHERE id=50802 AND deleted_flag=0;
UPDATE wf_template_node SET node_name='总经理审批',   node_order=3, timeout_hours=72 WHERE id=50803 AND deleted_flag=0;

SET FOREIGN_KEY_CHECKS = 1;
