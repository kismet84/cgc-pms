-- V106: 审批中心三链路闭环 demo 数据
-- 范围：CONTRACT_APPROVAL / PURCHASE_REQUEST / SUB_MEASURE
-- 说明：只补演示环境审批闭环样本，不补付款、变更/签证、技术事项，不扩权限。

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

SET @demo_project_id := (
    SELECT id
    FROM pm_project
    WHERE tenant_id = 0
      AND status = 'ACTIVE'
      AND deleted_flag = 0
    ORDER BY created_at DESC, id DESC
    LIMIT 1
);

SET @demo_party_a_id := (
    SELECT party_a_id
    FROM ct_contract
    WHERE tenant_id = 0
      AND project_id = @demo_project_id
      AND party_a_id IS NOT NULL
      AND deleted_flag = 0
    ORDER BY created_at ASC, id ASC
    LIMIT 1
);

SET @demo_party_b_id := (
    SELECT party_b_id
    FROM ct_contract
    WHERE tenant_id = 0
      AND project_id = @demo_project_id
      AND party_b_id IS NOT NULL
      AND deleted_flag = 0
    ORDER BY created_at ASC, id ASC
    LIMIT 1
);

SET @demo_contract_id := (
    SELECT id
    FROM ct_contract
    WHERE tenant_id = 0
      AND project_id = @demo_project_id
      AND contract_status = 'PERFORMING'
      AND approval_status = 'APPROVED'
      AND deleted_flag = 0
    ORDER BY created_at ASC, id ASC
    LIMIT 1
);

SET @demo_partner_id := (
    SELECT id
    FROM md_partner
    WHERE tenant_id = 0
      AND status = 'ENABLE'
      AND deleted_flag = 0
    ORDER BY id ASC
    LIMIT 1
);

SET @demo_material_id := (
    SELECT id
    FROM md_material
    WHERE tenant_id = 0
      AND status = 'ENABLE'
      AND deleted_flag = 0
    ORDER BY id ASC
    LIMIT 1
);

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
    976000000000000101, 0, @demo_project_id, @demo_party_a_id, @demo_party_b_id,
    'CT-DEMO-WF-APPROVAL-001', '审批中心闭环演示合同审批样本', 'SUB',
    660000.00, 660000.00, 0.00,
    'DRAFT', 'APPROVING',
    CURDATE(), DATE_ADD(CURDATE(), INTERVAL 90 DAY),
    0,
    1, 1, NOW(), NOW(),
    0, 'V106审批中心合同审批闭环演示数据'
FROM dual
WHERE @demo_project_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM ct_contract
      WHERE tenant_id = 0
        AND contract_code = 'CT-DEMO-WF-APPROVAL-001'
        AND deleted_flag = 0
  );

INSERT INTO mat_purchase_request (
    id, tenant_id, project_id, request_code, contract_id,
    approval_status, status, created_by, updated_by, created_at, updated_at, deleted_flag, remark
)
SELECT 976000000000000201, 0, @demo_project_id, 'PR-DEMO-REAL-001', @demo_contract_id,
       'APPROVING', 'DRAFT', 1, 1, NOW(), NOW(), 0, 'V106审批中心采购申请兜底演示数据'
FROM dual
WHERE @demo_project_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM mat_purchase_request
      WHERE tenant_id = 0
        AND request_code = 'PR-DEMO-REAL-001'
        AND deleted_flag = 0
  );

SET @approval_pr_id := (
    SELECT id
    FROM mat_purchase_request
    WHERE tenant_id = 0
      AND request_code = 'PR-DEMO-REAL-001'
      AND deleted_flag = 0
    ORDER BY id ASC
    LIMIT 1
);

INSERT INTO mat_purchase_request_item (
    id, tenant_id, request_id, material_id, quantity, unit, planned_date,
    created_by, updated_by, created_at, updated_at, deleted_flag, remark
)
SELECT 976000000000000202, 0, @approval_pr_id, @demo_material_id, 6.0000, '批',
       DATE_ADD(CURDATE(), INTERVAL 5 DAY), 1, 1, NOW(), NOW(), 0, 'V106审批中心采购申请明细兜底'
FROM dual
WHERE @approval_pr_id IS NOT NULL
  AND @demo_material_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM mat_purchase_request_item
      WHERE id = 976000000000000202
  );

INSERT INTO sub_measure (
    id, tenant_id, project_id, contract_id, partner_id, measure_code, measure_period, measure_date,
    reported_amount, approved_amount, deduction_amount, net_amount, approval_status,
    cost_generated_flag, status, created_by, updated_by, created_at, updated_at, deleted_flag, remark
)
SELECT 976000000000000301, 0, @demo_project_id, @demo_contract_id, @demo_partner_id,
       'SM-DEMO-REAL-001', DATE_FORMAT(CURDATE(), '%Y-%m'), CURDATE(),
       90000.00, 80000.00, 10000.00, 80000.00, 'APPROVING',
       0, 'APPROVING', 1, 1, NOW(), NOW(), 0, 'V106审批中心分包计量兜底演示数据'
FROM dual
WHERE @demo_project_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM sub_measure
      WHERE tenant_id = 0
        AND measure_code = 'SM-DEMO-REAL-001'
        AND deleted_flag = 0
  );

SET @approval_sm_id := (
    SELECT id
    FROM sub_measure
    WHERE tenant_id = 0
      AND measure_code = 'SM-DEMO-REAL-001'
      AND deleted_flag = 0
    ORDER BY id ASC
    LIMIT 1
);

INSERT INTO sub_measure_item (
    id, tenant_id, measure_id, item_name, unit, contract_quantity, current_quantity, cumulative_quantity,
    unit_price, amount, created_by, updated_by, created_at, updated_at, deleted_flag, remark
)
SELECT 976000000000000302, 0, @approval_sm_id, '审批中心闭环演示分包计量明细', '项',
       1.0000, 1.0000, 1.0000, 80000.0000, 80000.00,
       1, 1, NOW(), NOW(), 0, 'V106审批中心分包计量明细兜底'
FROM dual
WHERE @approval_sm_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM sub_measure_item
      WHERE id = 976000000000000302
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
SELECT 976000000000001001, 0, 50001,
       'CONTRACT_APPROVAL', 976000000000000101, @demo_project_id, 976000000000000101,
       '审批中心合同审批闭环演示', 660000.00, 'RUNNING',
       1, 0, 1,
       1, NULL, NULL,
       NOW(), 1, NOW(), 1, NOW(),
       0, 'V106审批中心合同审批闭环演示数据'
FROM dual
WHERE EXISTS (SELECT 1 FROM ct_contract WHERE id = 976000000000000101 AND deleted_flag = 0)
  AND NOT EXISTS (
      SELECT 1 FROM wf_instance
      WHERE business_type = 'CONTRACT_APPROVAL'
        AND business_id = 976000000000000101
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
SELECT 976000000000002001, 0, 50010,
       'PURCHASE_REQUEST', @approval_pr_id, @demo_project_id,
       (SELECT contract_id FROM mat_purchase_request WHERE id = @approval_pr_id),
       '审批中心采购申请闭环演示', NULL, 'RUNNING',
       1, 0, 1,
       1, NULL, NULL,
       NOW(), 1, NOW(), 1, NOW(),
       0, 'V106审批中心采购申请闭环演示数据'
FROM dual
WHERE @approval_pr_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM wf_instance
      WHERE business_type = 'PURCHASE_REQUEST'
        AND business_id = @approval_pr_id
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
SELECT 976000000000003001, 0, 50004,
       'SUB_MEASURE', @approval_sm_id, @demo_project_id,
       (SELECT contract_id FROM sub_measure WHERE id = @approval_sm_id),
       '审批中心分包计量闭环演示',
       (SELECT net_amount FROM sub_measure WHERE id = @approval_sm_id),
       'RUNNING',
       1, 0, 1,
       1, NULL, NULL,
       NOW(), 1, NOW(), 1, NOW(),
       0, 'V106审批中心分包计量闭环演示数据'
FROM dual
WHERE @approval_sm_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM wf_instance
      WHERE business_type = 'SUB_MEASURE'
        AND business_id = @approval_sm_id
        AND deleted_flag = 0
  );

INSERT INTO wf_node_instance (
    id, tenant_id, instance_id, template_node_id,
    node_code, node_name, node_order, approve_mode, node_status,
    round_no, pass_rule_json, reject_rule_json,
    started_at, created_by, created_at, updated_by, updated_at,
    deleted_flag, remark
)
SELECT 976000000000001101, 0, 976000000000001001, tn.id,
       tn.node_code, tn.node_name, tn.node_order, tn.approve_mode,
       'ACTIVE',
       1, tn.pass_rule_json, tn.reject_rule_json,
       NOW(),
       1, NOW(), 1, NOW(),
       0, 'V106审批中心合同审批节点实例'
FROM (
    SELECT id, node_code, node_name, node_order, approve_mode, pass_rule_json, reject_rule_json
    FROM wf_template_node
    WHERE template_id = 50001
      AND deleted_flag = 0
    ORDER BY node_order ASC, id ASC
    LIMIT 1
) tn
WHERE 1 = 1
  AND EXISTS (SELECT 1 FROM wf_instance WHERE id = 976000000000001001 AND deleted_flag = 0)
  AND NOT EXISTS (
      SELECT 1 FROM wf_node_instance ni
      WHERE ni.id = 976000000000001101
  );

INSERT INTO wf_node_instance (
    id, tenant_id, instance_id, template_node_id,
    node_code, node_name, node_order, approve_mode, node_status,
    round_no, pass_rule_json, reject_rule_json,
    started_at, created_by, created_at, updated_by, updated_at,
    deleted_flag, remark
)
SELECT 976000000000002101, 0, 976000000000002001, tn.id,
       tn.node_code, tn.node_name, tn.node_order, tn.approve_mode,
       'ACTIVE',
       1, tn.pass_rule_json, tn.reject_rule_json,
       NOW(),
       1, NOW(), 1, NOW(),
       0, 'V106审批中心采购申请节点实例'
FROM (
    SELECT id, node_code, node_name, node_order, approve_mode, pass_rule_json, reject_rule_json
    FROM wf_template_node
    WHERE template_id = 50010
      AND deleted_flag = 0
    ORDER BY node_order ASC, id ASC
    LIMIT 1
) tn
WHERE 1 = 1
  AND EXISTS (SELECT 1 FROM wf_instance WHERE id = 976000000000002001 AND deleted_flag = 0)
  AND NOT EXISTS (
      SELECT 1 FROM wf_node_instance ni
      WHERE ni.id = 976000000000002101
  );

INSERT INTO wf_node_instance (
    id, tenant_id, instance_id, template_node_id,
    node_code, node_name, node_order, approve_mode, node_status,
    round_no, pass_rule_json, reject_rule_json,
    started_at, created_by, created_at, updated_by, updated_at,
    deleted_flag, remark
)
SELECT 976000000000003101, 0, 976000000000003001, tn.id,
       tn.node_code, tn.node_name, tn.node_order, tn.approve_mode,
       'ACTIVE',
       1, tn.pass_rule_json, tn.reject_rule_json,
       NOW(),
       1, NOW(), 1, NOW(),
       0, 'V106审批中心分包计量节点实例'
FROM (
    SELECT id, node_code, node_name, node_order, approve_mode, pass_rule_json, reject_rule_json
    FROM wf_template_node
    WHERE template_id = 50004
      AND deleted_flag = 0
    ORDER BY node_order ASC, id ASC
    LIMIT 1
) tn
WHERE 1 = 1
  AND EXISTS (SELECT 1 FROM wf_instance WHERE id = 976000000000003001 AND deleted_flag = 0)
  AND NOT EXISTS (
      SELECT 1 FROM wf_node_instance ni
      WHERE ni.id = 976000000000003101
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
SELECT 976000000000001201, 0, 976000000000001001,
       (SELECT id FROM wf_node_instance WHERE instance_id = 976000000000001001 AND node_order = 1 AND deleted_flag = 0 LIMIT 1),
       'CONTRACT_APPROVAL', 976000000000000101,
       1, '系统管理员', 'PENDING',
       1, 1, NOW(),
       1, NOW(), 1, NOW(),
       0, 'V106审批中心合同审批待办'
FROM dual
WHERE EXISTS (SELECT 1 FROM wf_instance WHERE id = 976000000000001001 AND deleted_flag = 0)
  AND NOT EXISTS (SELECT 1 FROM wf_task WHERE id = 976000000000001201);

INSERT INTO wf_task (
    id, tenant_id, instance_id, node_instance_id,
    business_type, business_id,
    approver_id, approver_name, task_status,
    round_no, task_version,
    received_at,
    created_by, created_at, updated_by, updated_at,
    deleted_flag, remark
)
SELECT 976000000000002201, 0, 976000000000002001,
       (SELECT id FROM wf_node_instance WHERE instance_id = 976000000000002001 AND node_order = 1 AND deleted_flag = 0 LIMIT 1),
       'PURCHASE_REQUEST', @approval_pr_id,
       1, '系统管理员', 'PENDING',
       1, 1, NOW(),
       1, NOW(), 1, NOW(),
       0, 'V106审批中心采购申请待办'
FROM dual
WHERE @approval_pr_id IS NOT NULL
  AND EXISTS (SELECT 1 FROM wf_instance WHERE id = 976000000000002001 AND deleted_flag = 0)
  AND NOT EXISTS (SELECT 1 FROM wf_task WHERE id = 976000000000002201);

INSERT INTO wf_task (
    id, tenant_id, instance_id, node_instance_id,
    business_type, business_id,
    approver_id, approver_name, task_status,
    round_no, task_version,
    received_at,
    created_by, created_at, updated_by, updated_at,
    deleted_flag, remark
)
SELECT 976000000000003201, 0, 976000000000003001,
       (SELECT id FROM wf_node_instance WHERE instance_id = 976000000000003001 AND node_order = 1 AND deleted_flag = 0 LIMIT 1),
       'SUB_MEASURE', @approval_sm_id,
       1, '系统管理员', 'PENDING',
       1, 1, NOW(),
       1, NOW(), 1, NOW(),
       0, 'V106审批中心分包计量待办'
FROM dual
WHERE @approval_sm_id IS NOT NULL
  AND EXISTS (SELECT 1 FROM wf_instance WHERE id = 976000000000003001 AND deleted_flag = 0)
  AND NOT EXISTS (SELECT 1 FROM wf_task WHERE id = 976000000000003201);

INSERT INTO wf_record (
    id, tenant_id, instance_id, node_instance_id, task_id, round_no,
    business_type, business_id, node_code, node_name,
    action_type, action_name, operator_id, operator_name, comment, record_status,
    created_by, created_at, updated_by, updated_at, deleted_flag, remark
)
SELECT 976000000000001301, 0, 976000000000001001, NULL, NULL, 1,
       'CONTRACT_APPROVAL', 976000000000000101, NULL, NULL,
       'SUBMIT', '提交审批', 1, 'admin', NULL, 'EFFECTIVE',
       1, NOW(), 1, NOW(), 0, 'V106审批中心合同审批提交记录'
FROM dual
WHERE EXISTS (SELECT 1 FROM wf_instance WHERE id = 976000000000001001 AND deleted_flag = 0)
  AND NOT EXISTS (SELECT 1 FROM wf_record WHERE id = 976000000000001301);

INSERT INTO wf_record (
    id, tenant_id, instance_id, node_instance_id, task_id, round_no,
    business_type, business_id, node_code, node_name,
    action_type, action_name, operator_id, operator_name, comment, record_status,
    created_by, created_at, updated_by, updated_at, deleted_flag, remark
)
SELECT 976000000000002301, 0, 976000000000002001, NULL, NULL, 1,
       'PURCHASE_REQUEST', @approval_pr_id, NULL, NULL,
       'SUBMIT', '提交审批', 1, 'admin', NULL, 'EFFECTIVE',
       1, NOW(), 1, NOW(), 0, 'V106审批中心采购申请提交记录'
FROM dual
WHERE @approval_pr_id IS NOT NULL
  AND EXISTS (SELECT 1 FROM wf_instance WHERE id = 976000000000002001 AND deleted_flag = 0)
  AND NOT EXISTS (SELECT 1 FROM wf_record WHERE id = 976000000000002301);

INSERT INTO wf_record (
    id, tenant_id, instance_id, node_instance_id, task_id, round_no,
    business_type, business_id, node_code, node_name,
    action_type, action_name, operator_id, operator_name, comment, record_status,
    created_by, created_at, updated_by, updated_at, deleted_flag, remark
)
SELECT 976000000000003301, 0, 976000000000003001, NULL, NULL, 1,
       'SUB_MEASURE', @approval_sm_id, NULL, NULL,
       'SUBMIT', '提交审批', 1, 'admin', NULL, 'EFFECTIVE',
       1, NOW(), 1, NOW(), 0, 'V106审批中心分包计量提交记录'
FROM dual
WHERE @approval_sm_id IS NOT NULL
  AND EXISTS (SELECT 1 FROM wf_instance WHERE id = 976000000000003001 AND deleted_flag = 0)
  AND NOT EXISTS (SELECT 1 FROM wf_record WHERE id = 976000000000003301);

SET FOREIGN_KEY_CHECKS = 1;
