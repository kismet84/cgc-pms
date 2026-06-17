-- V65__seed_sub_measure_template.sql
-- 建筑工程总包项目全过程管理系统 - 分包计量审批模板
-- 数据库：MySQL 8.0+
-- 幂等：INSERT IGNORE + UPDATE

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

INSERT IGNORE INTO wf_template (id, tenant_id, template_code, template_name, business_type, enabled, amount_min, amount_max, condition_rule, form_schema, created_by, remark) VALUES
  (50004, 0, 'TPL-SUB-MEASURE-001', '分包计量审批流程', 'SUB_MEASURE', 1, 0.00, 999999999.99, NULL, NULL, 1, '分包计量审批标准流程：项目经理 → 部门经理 → 总经理');

UPDATE wf_template SET template_code='TPL-SUB-MEASURE-001', template_name='分包计量审批流程', enabled=1 WHERE id=50004 AND business_type='SUB_MEASURE' AND deleted_flag=0;

INSERT IGNORE INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours) VALUES
  (50401, 0, 50004, 'N1', '项目经理审批', 1, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type','USER','userId',1), NULL, 1, 1, 48),
  (50402, 0, 50004, 'N2', '部门经理审批', 2, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type','USER','userId',1), NULL, 1, 1, 48),
  (50403, 0, 50004, 'N3', '总经理审批',   3, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type','USER','userId',1), NULL, 1, 1, 72);

UPDATE wf_template_node SET node_name='项目经理审批', node_order=1, timeout_hours=48 WHERE id=50401 AND deleted_flag=0;
UPDATE wf_template_node SET node_name='部门经理审批', node_order=2, timeout_hours=48 WHERE id=50402 AND deleted_flag=0;
UPDATE wf_template_node SET node_name='总经理审批',   node_order=3, timeout_hours=72 WHERE id=50403 AND deleted_flag=0;

SET FOREIGN_KEY_CHECKS = 1;
