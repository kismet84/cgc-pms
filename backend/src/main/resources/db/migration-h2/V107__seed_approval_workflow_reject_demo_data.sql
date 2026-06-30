-- V107: 审批中心驳回闭环 demo 数据
-- H2 对等版本。只补 PURCHASE_REQUEST / CONTRACT_APPROVAL。

INSERT INTO ct_contract (
    id, tenant_id, project_id, party_a_id, party_b_id,
    contract_code, contract_name, contract_type,
    contract_amount, current_amount, paid_amount,
    contract_status, approval_status,
    start_date, end_date,
    cost_generated_flag,
    created_by, updated_by, created_at, updated_at,
    deleted_flag, remark
)
SELECT
    977000000000000101, 0, 10001, 20001, 20002,
    'CT-DEMO-WF-REJECT-001', '审批中心驳回演示合同样本', 'SUB',
    480000.00, 480000.00, 0.00,
    'DRAFT', 'APPROVING',
    CURRENT_DATE, DATEADD('DAY', 60, CURRENT_DATE),
    0,
    1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
    0, 'V107审批中心合同驳回闭环演示数据'
WHERE EXISTS (SELECT 1 FROM pm_project WHERE id = 10001 AND tenant_id = 0 AND deleted_flag = 0)
  AND NOT EXISTS (
      SELECT 1
      FROM ct_contract
      WHERE tenant_id = 0
        AND contract_code = 'CT-DEMO-WF-REJECT-001'
        AND deleted_flag = 0
  );

INSERT INTO mat_purchase_request (
    id, tenant_id, project_id, request_code, contract_id,
    approval_status, status, created_by, updated_by, created_at, updated_at, deleted_flag, remark
)
SELECT 977000000000000201, 0, 10001, 'PR-DEMO-WF-REJECT-001', 30001,
       'APPROVING', 'DRAFT', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 'V107审批中心采购申请驳回演示数据'
WHERE EXISTS (SELECT 1 FROM pm_project WHERE id = 10001 AND tenant_id = 0 AND deleted_flag = 0)
  AND NOT EXISTS (
      SELECT 1
      FROM mat_purchase_request
      WHERE tenant_id = 0
        AND request_code = 'PR-DEMO-WF-REJECT-001'
        AND deleted_flag = 0
  );

INSERT INTO mat_purchase_request_item (
    id, tenant_id, request_id, material_id, quantity, unit, planned_date,
    created_by, updated_by, created_at, updated_at, deleted_flag, remark
)
SELECT 977000000000000202, 0,
       (SELECT id FROM mat_purchase_request WHERE tenant_id = 0 AND request_code = 'PR-DEMO-WF-REJECT-001' AND deleted_flag = 0 LIMIT 1),
       970000000000005001, 9.0000, '批',
       DATEADD('DAY', 7, CURRENT_DATE), 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 'V107审批中心采购申请驳回演示明细'
WHERE EXISTS (SELECT 1 FROM mat_purchase_request WHERE tenant_id = 0 AND request_code = 'PR-DEMO-WF-REJECT-001' AND deleted_flag = 0)
  AND EXISTS (SELECT 1 FROM md_material WHERE id = 970000000000005001 AND deleted_flag = 0)
  AND NOT EXISTS (
      SELECT 1
      FROM mat_purchase_request_item
      WHERE id = 977000000000000202
  );

INSERT INTO wf_instance (
    id, tenant_id, template_id,
    business_type, business_id, project_id, contract_id,
    title, amount, instance_status,
    current_round, resubmit_count, business_revision,
    initiator_id, business_summary, variables,
    started_at, created_by, created_at, updated_by, updated_at,
    deleted_flag, remark
)
SELECT 977000000000001001, 0, 50001,
       'CONTRACT_APPROVAL', 977000000000000101, 10001, 977000000000000101,
       '审批中心合同驳回演示', 480000.00, 'RUNNING',
       1, 0, 1,
       1, NULL, NULL,
       CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP,
       0, 'V107审批中心合同驳回闭环演示数据'
WHERE EXISTS (SELECT 1 FROM ct_contract WHERE id = 977000000000000101 AND deleted_flag = 0)
  AND NOT EXISTS (
      SELECT 1 FROM wf_instance
      WHERE business_type = 'CONTRACT_APPROVAL'
        AND business_id = 977000000000000101
        AND deleted_flag = 0
  );

INSERT INTO wf_instance (
    id, tenant_id, template_id,
    business_type, business_id, project_id, contract_id,
    title, amount, instance_status,
    current_round, resubmit_count, business_revision,
    initiator_id, business_summary, variables,
    started_at, created_by, created_at, updated_by, updated_at,
    deleted_flag, remark
)
SELECT 977000000000002001, 0, 50010,
       'PURCHASE_REQUEST',
       (SELECT id FROM mat_purchase_request WHERE tenant_id = 0 AND request_code = 'PR-DEMO-WF-REJECT-001' AND deleted_flag = 0 LIMIT 1),
       10001,
       (SELECT contract_id FROM mat_purchase_request WHERE tenant_id = 0 AND request_code = 'PR-DEMO-WF-REJECT-001' AND deleted_flag = 0 LIMIT 1),
       '审批中心采购申请驳回演示', NULL, 'RUNNING',
       1, 0, 1,
       1, NULL, NULL,
       CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP,
       0, 'V107审批中心采购申请驳回闭环演示数据'
WHERE EXISTS (SELECT 1 FROM mat_purchase_request WHERE tenant_id = 0 AND request_code = 'PR-DEMO-WF-REJECT-001' AND deleted_flag = 0)
  AND NOT EXISTS (
      SELECT 1 FROM wf_instance
      WHERE business_type = 'PURCHASE_REQUEST'
        AND business_id = (SELECT id FROM mat_purchase_request WHERE tenant_id = 0 AND request_code = 'PR-DEMO-WF-REJECT-001' AND deleted_flag = 0 LIMIT 1)
        AND deleted_flag = 0
  );

INSERT INTO wf_node_instance (
    id, tenant_id, instance_id, template_node_id,
    node_code, node_name, node_order, approve_mode, node_status,
    round_no, pass_rule_json, reject_rule_json,
    started_at, created_by, created_at, updated_by, updated_at,
    deleted_flag, remark
)
SELECT 977000000000001101, 0, 977000000000001001, tn.id,
       tn.node_code, tn.node_name, tn.node_order, tn.approve_mode,
       'ACTIVE',
       1, tn.pass_rule_json, tn.reject_rule_json,
       CURRENT_TIMESTAMP,
       1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP,
       0, 'V107审批中心合同驳回节点实例'
FROM (
    SELECT id, node_code, node_name, node_order, approve_mode, pass_rule_json, reject_rule_json
    FROM wf_template_node
    WHERE template_id = 50001
      AND deleted_flag = 0
    ORDER BY node_order ASC, id ASC
    LIMIT 1
) tn
WHERE EXISTS (SELECT 1 FROM wf_instance WHERE id = 977000000000001001 AND deleted_flag = 0)
  AND NOT EXISTS (
      SELECT 1 FROM wf_node_instance ni
      WHERE ni.id = 977000000000001101
  );

INSERT INTO wf_node_instance (
    id, tenant_id, instance_id, template_node_id,
    node_code, node_name, node_order, approve_mode, node_status,
    round_no, pass_rule_json, reject_rule_json,
    started_at, created_by, created_at, updated_by, updated_at,
    deleted_flag, remark
)
SELECT 977000000000002101, 0, 977000000000002001, tn.id,
       tn.node_code, tn.node_name, tn.node_order, tn.approve_mode,
       'ACTIVE',
       1, tn.pass_rule_json, tn.reject_rule_json,
       CURRENT_TIMESTAMP,
       1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP,
       0, 'V107审批中心采购申请驳回节点实例'
FROM (
    SELECT id, node_code, node_name, node_order, approve_mode, pass_rule_json, reject_rule_json
    FROM wf_template_node
    WHERE template_id = 50010
      AND deleted_flag = 0
    ORDER BY node_order ASC, id ASC
    LIMIT 1
) tn
WHERE EXISTS (SELECT 1 FROM wf_instance WHERE id = 977000000000002001 AND deleted_flag = 0)
  AND NOT EXISTS (
      SELECT 1 FROM wf_node_instance ni
      WHERE ni.id = 977000000000002101
  );

INSERT INTO wf_task (
    id, tenant_id, instance_id, node_instance_id,
    business_type, business_id,
    approver_id, approver_name, task_status,
    round_no, task_version,
    received_at,
    created_by, created_at, updated_by, updated_at,
    deleted_flag, remark
)
SELECT 977000000000001201, 0, 977000000000001001,
       (SELECT id FROM wf_node_instance WHERE instance_id = 977000000000001001 AND node_order = 1 AND deleted_flag = 0 LIMIT 1),
       'CONTRACT_APPROVAL', 977000000000000101,
       1, '系统管理员', 'PENDING',
       1, 1, CURRENT_TIMESTAMP,
       1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP,
       0, 'V107审批中心合同驳回待办'
WHERE EXISTS (SELECT 1 FROM wf_instance WHERE id = 977000000000001001 AND deleted_flag = 0)
  AND NOT EXISTS (SELECT 1 FROM wf_task WHERE id = 977000000000001201);

INSERT INTO wf_task (
    id, tenant_id, instance_id, node_instance_id,
    business_type, business_id,
    approver_id, approver_name, task_status,
    round_no, task_version,
    received_at,
    created_by, created_at, updated_by, updated_at,
    deleted_flag, remark
)
SELECT 977000000000002201, 0, 977000000000002001,
       (SELECT id FROM wf_node_instance WHERE instance_id = 977000000000002001 AND node_order = 1 AND deleted_flag = 0 LIMIT 1),
       'PURCHASE_REQUEST',
       (SELECT id FROM mat_purchase_request WHERE tenant_id = 0 AND request_code = 'PR-DEMO-WF-REJECT-001' AND deleted_flag = 0 LIMIT 1),
       1, '系统管理员', 'PENDING',
       1, 1, CURRENT_TIMESTAMP,
       1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP,
       0, 'V107审批中心采购申请驳回待办'
WHERE EXISTS (SELECT 1 FROM wf_instance WHERE id = 977000000000002001 AND deleted_flag = 0)
  AND NOT EXISTS (SELECT 1 FROM wf_task WHERE id = 977000000000002201);

INSERT INTO wf_record (
    id, tenant_id, instance_id, node_instance_id, task_id, round_no,
    business_type, business_id, node_code, node_name,
    action_type, action_name, operator_id, operator_name, comment, record_status,
    created_by, created_at, updated_by, updated_at, deleted_flag, remark
)
SELECT 977000000000001301, 0, 977000000000001001, NULL, NULL, 1,
       'CONTRACT_APPROVAL', 977000000000000101, NULL, NULL,
       'SUBMIT', '提交审批', 1, 'admin', NULL, 'EFFECTIVE',
       1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 0, 'V107审批中心合同驳回提交记录'
WHERE EXISTS (SELECT 1 FROM wf_instance WHERE id = 977000000000001001 AND deleted_flag = 0)
  AND NOT EXISTS (SELECT 1 FROM wf_record WHERE id = 977000000000001301);

INSERT INTO wf_record (
    id, tenant_id, instance_id, node_instance_id, task_id, round_no,
    business_type, business_id, node_code, node_name,
    action_type, action_name, operator_id, operator_name, comment, record_status,
    created_by, created_at, updated_by, updated_at, deleted_flag, remark
)
SELECT 977000000000002301, 0, 977000000000002001, NULL, NULL, 1,
       'PURCHASE_REQUEST',
       (SELECT id FROM mat_purchase_request WHERE tenant_id = 0 AND request_code = 'PR-DEMO-WF-REJECT-001' AND deleted_flag = 0 LIMIT 1),
       NULL, NULL,
       'SUBMIT', '提交审批', 1, 'admin', NULL, 'EFFECTIVE',
       1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 0, 'V107审批中心采购申请驳回提交记录'
WHERE EXISTS (SELECT 1 FROM wf_instance WHERE id = 977000000000002001 AND deleted_flag = 0)
  AND NOT EXISTS (SELECT 1 FROM wf_record WHERE id = 977000000000002301);
