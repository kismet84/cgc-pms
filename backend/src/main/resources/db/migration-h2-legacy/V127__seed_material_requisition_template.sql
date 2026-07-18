-- V127 H2: add missing MATERIAL_REQUISITION approval template for requisition submit flow.
-- Use tenant-local PROJECT_ROLE resolution to avoid fixed user drift in non-tenant-0 environments.

INSERT INTO wf_template (
    id, tenant_id, template_code, template_name, business_type, enabled,
    amount_min, amount_max, condition_rule, form_schema, created_by, remark
)
SELECT 50014, 0, 'TPL-MATERIAL-REQUISITION-001', '领料申请审批流程', 'MATERIAL_REQUISITION', 1,
       0.00, 999999999.99, NULL, NULL, 1, '领料申请审批标准流程：项目经理审批。'
WHERE NOT EXISTS (
    SELECT 1 FROM wf_template WHERE id = 50014 AND deleted_flag = 0
);

UPDATE wf_template
SET template_code = 'TPL-MATERIAL-REQUISITION-001',
    template_name = '领料申请审批流程',
    enabled = 1,
    amount_min = 0.00,
    amount_max = 999999999.99,
    remark = '领料申请审批标准流程：项目经理审批。'
WHERE id = 50014
  AND business_type = 'MATERIAL_REQUISITION'
  AND deleted_flag = 0;

INSERT INTO wf_template_node (
    id, tenant_id, template_id, node_code, node_name, node_order, node_type,
    approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours
)
SELECT 51401, 0, 50014, 'N1', '项目经理审批', 1, 'APPROVAL',
       'SEQUENTIAL', JSON_OBJECT('type', 'PROJECT_ROLE', 'roleCode', 'PROJECT_MANAGER'),
       NULL, 1, 1, 48
WHERE NOT EXISTS (
    SELECT 1 FROM wf_template_node WHERE id = 51401 AND deleted_flag = 0
);

UPDATE wf_template_node
SET node_code = 'N1',
    node_name = '项目经理审批',
    node_order = 1,
    node_type = 'APPROVAL',
    approve_mode = 'SEQUENTIAL',
    approver_config = JSON_OBJECT('type', 'PROJECT_ROLE', 'roleCode', 'PROJECT_MANAGER'),
    allow_transfer = 1,
    allow_add_sign = 1,
    timeout_hours = 48
WHERE id = 51401
  AND deleted_flag = 0;

UPDATE wf_template
SET enabled = 0
WHERE tenant_id = 0
  AND business_type = 'MATERIAL_REQUISITION'
  AND deleted_flag = 0
  AND id <> 50014;
