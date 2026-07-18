-- V56__seed_contract_approval_template.sql
-- Seed CONTRACT_APPROVAL workflow template with 2-step approval chain

-- Template: 合同审批
INSERT INTO wf_template (id, tenant_id, template_code, template_name, business_type, enabled, created_by, updated_by)
SELECT 2000000000000000001, 0, 'CONTRACT_APPROVAL_DEFAULT', '合同审批（默认）', 'CONTRACT_APPROVAL', 1, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM wf_template WHERE template_code = 'CONTRACT_APPROVAL_DEFAULT' AND tenant_id = 0 AND deleted_flag = 0);

-- Node 1: 项目经理审批
INSERT INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode,
                              approver_config, created_by, updated_by)
SELECT 2000000000000000002, 0, 2000000000000000001, 'PM_APPROVE', '项目经理审批', 1, 'APPROVAL', 'SEQUENTIAL',
       '{}', 1, 1
WHERE EXISTS (SELECT 1 FROM wf_template WHERE id = 2000000000000000001 AND deleted_flag = 0);

-- Node 2: 总经理审批
INSERT INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode,
                              approver_config, created_by, updated_by)
SELECT 2000000000000000003, 0, 2000000000000000001, 'GM_APPROVE', '总经理审批', 2, 'APPROVAL', 'SEQUENTIAL',
       '{}', 1, 1
WHERE EXISTS (SELECT 1 FROM wf_template WHERE id = 2000000000000000001 AND deleted_flag = 0);