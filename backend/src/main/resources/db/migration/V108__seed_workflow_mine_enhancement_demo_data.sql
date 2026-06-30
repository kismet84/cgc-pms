-- V108: 审批中心“我发起”状态筛选 / 撤回重提 / 翻页增强 demo 数据
-- 范围：只补 admin(userId=1) 的我发起实例样本，不改前端 pageSize，不回写 V106/V107。

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
SELECT s.contract_id, 0, @demo_project_id, @demo_party_a_id, @demo_party_b_id,
       s.contract_code, s.contract_name, 'SUB',
       s.contract_amount, s.contract_amount, 0.00,
       'DRAFT', s.approval_status,
       CURDATE(), DATE_ADD(CURDATE(), INTERVAL 90 DAY),
       0,
       1, 1, NOW(), NOW(),
       0, 'V108审批中心我发起增强合同业务样本'
FROM (
    SELECT 978000000000000101 AS contract_id, 'CT-DEMO-WF-MINE-RUN-001' AS contract_code, '审批中心我发起合同运行样本-1' AS contract_name, 410000.00 AS contract_amount, 'APPROVING' AS approval_status
    UNION ALL
    SELECT 978000000000000102, 'CT-DEMO-WF-MINE-APP-001', '审批中心我发起合同通过样本-1', 430000.00, 'APPROVED'
    UNION ALL
    SELECT 978000000000000103, 'CT-DEMO-WF-MINE-REJ-001', '审批中心我发起合同驳回样本-1', 450000.00, 'REJECTED'
    UNION ALL
    SELECT 978000000000000104, 'CT-DEMO-WF-MINE-WDR-001', '审批中心我发起合同撤回样本-1', 470000.00, 'DRAFT'
    UNION ALL
    SELECT 978000000000000105, 'CT-DEMO-WF-MINE-APP-002', '审批中心我发起合同通过样本-2', 490000.00, 'APPROVED'
) s
WHERE @demo_project_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM ct_contract c
      WHERE c.tenant_id = 0
        AND c.contract_code = s.contract_code
        AND c.deleted_flag = 0
  );

INSERT INTO mat_purchase_request (
    id, tenant_id, project_id, request_code, contract_id,
    approval_status, status, created_by, updated_by, created_at, updated_at, deleted_flag, remark
)
SELECT s.request_id, 0, @demo_project_id, s.request_code, @demo_contract_id,
       s.approval_status, s.request_status, 1, 1, NOW(), NOW(), 0, 'V108审批中心我发起增强采购申请样本'
FROM (
    SELECT 978000000000000201 AS request_id, 'PR-DEMO-WF-MINE-RUN-001' AS request_code, 'APPROVING' AS approval_status, 'DRAFT' AS request_status
    UNION ALL
    SELECT 978000000000000202, 'PR-DEMO-WF-MINE-APP-001', 'APPROVED', 'APPROVED'
    UNION ALL
    SELECT 978000000000000203, 'PR-DEMO-WF-MINE-REJ-001', 'REJECTED', 'REJECTED'
    UNION ALL
    SELECT 978000000000000204, 'PR-DEMO-WF-MINE-WDR-001', 'DRAFT', 'DRAFT'
    UNION ALL
    SELECT 978000000000000205, 'PR-DEMO-WF-MINE-RUN-002', 'APPROVING', 'DRAFT'
    UNION ALL
    SELECT 978000000000000206, 'PR-DEMO-WF-MINE-WDR-002', 'DRAFT', 'DRAFT'
) s
WHERE @demo_project_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM mat_purchase_request pr
      WHERE pr.tenant_id = 0
        AND pr.request_code = s.request_code
        AND pr.deleted_flag = 0
  );

INSERT INTO mat_purchase_request_item (
    id, tenant_id, request_id, material_id, quantity, unit, planned_date,
    created_by, updated_by, created_at, updated_at, deleted_flag, remark
)
SELECT s.item_id, 0, pr.id, @demo_material_id, s.quantity, '批', DATE_ADD(CURDATE(), INTERVAL s.plan_offset DAY),
       1, 1, NOW(), NOW(), 0, 'V108审批中心我发起增强采购申请明细'
FROM (
    SELECT 978000000000000211 AS item_id, 'PR-DEMO-WF-MINE-RUN-001' AS request_code, 5.0000 AS quantity, 3 AS plan_offset
    UNION ALL
    SELECT 978000000000000212, 'PR-DEMO-WF-MINE-APP-001', 6.0000, 5
    UNION ALL
    SELECT 978000000000000213, 'PR-DEMO-WF-MINE-REJ-001', 7.0000, 7
    UNION ALL
    SELECT 978000000000000214, 'PR-DEMO-WF-MINE-WDR-001', 8.0000, 9
    UNION ALL
    SELECT 978000000000000215, 'PR-DEMO-WF-MINE-RUN-002', 9.0000, 11
    UNION ALL
    SELECT 978000000000000216, 'PR-DEMO-WF-MINE-WDR-002', 10.0000, 13
) s
JOIN mat_purchase_request pr
  ON pr.tenant_id = 0
 AND pr.request_code = s.request_code
 AND pr.deleted_flag = 0
WHERE @demo_material_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM mat_purchase_request_item pri
      WHERE pri.id = s.item_id
  );

INSERT INTO sub_measure (
    id, tenant_id, project_id, contract_id, partner_id, measure_code, measure_period, measure_date,
    reported_amount, approved_amount, deduction_amount, net_amount, approval_status,
    cost_generated_flag, status, created_by, updated_by, created_at, updated_at, deleted_flag, remark
)
SELECT s.measure_id, 0, @demo_project_id, @demo_contract_id, @demo_partner_id,
       s.measure_code, DATE_FORMAT(CURDATE(), '%Y-%m'), CURDATE(),
       s.net_amount, s.approved_amount, s.deduction_amount, s.net_amount, s.approval_status,
       0, s.measure_status, 1, 1, NOW(), NOW(), 0, 'V108审批中心我发起增强分包计量样本'
FROM (
    SELECT 978000000000000301 AS measure_id, 'SM-DEMO-WF-MINE-RUN-001' AS measure_code, 128000.00 AS net_amount, 128000.00 AS approved_amount, 0.00 AS deduction_amount, 'APPROVING' AS approval_status, 'APPROVING' AS measure_status
    UNION ALL
    SELECT 978000000000000302, 'SM-DEMO-WF-MINE-APP-001', 138000.00, 138000.00, 0.00, 'APPROVED', 'CONFIRMED'
    UNION ALL
    SELECT 978000000000000303, 'SM-DEMO-WF-MINE-REJ-001', 148000.00, 140000.00, 8000.00, 'REJECTED', 'REJECTED'
    UNION ALL
    SELECT 978000000000000304, 'SM-DEMO-WF-MINE-WDR-001', 158000.00, 150000.00, 8000.00, 'DRAFT', 'DRAFT'
    UNION ALL
    SELECT 978000000000000305, 'SM-DEMO-WF-MINE-REJ-002', 168000.00, 160000.00, 8000.00, 'REJECTED', 'REJECTED'
) s
WHERE @demo_project_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM sub_measure sm
      WHERE sm.tenant_id = 0
        AND sm.measure_code = s.measure_code
        AND sm.deleted_flag = 0
  );

INSERT INTO sub_measure_item (
    id, tenant_id, measure_id, item_name, unit, contract_quantity, current_quantity, cumulative_quantity,
    unit_price, amount, created_by, updated_by, created_at, updated_at, deleted_flag, remark
)
SELECT s.item_id, 0, sm.id, s.item_name, '项',
       1.0000, 1.0000, 1.0000, s.amount, s.amount,
       1, 1, NOW(), NOW(), 0, 'V108审批中心我发起增强分包计量明细'
FROM (
    SELECT 978000000000000311 AS item_id, 'SM-DEMO-WF-MINE-RUN-001' AS measure_code, '审批中心我发起分包计量运行样本明细' AS item_name, 128000.0000 AS amount
    UNION ALL
    SELECT 978000000000000312, 'SM-DEMO-WF-MINE-APP-001', '审批中心我发起分包计量通过样本明细', 138000.0000
    UNION ALL
    SELECT 978000000000000313, 'SM-DEMO-WF-MINE-REJ-001', '审批中心我发起分包计量驳回样本明细', 148000.0000
    UNION ALL
    SELECT 978000000000000314, 'SM-DEMO-WF-MINE-WDR-001', '审批中心我发起分包计量撤回样本明细', 158000.0000
    UNION ALL
    SELECT 978000000000000315, 'SM-DEMO-WF-MINE-REJ-002', '审批中心我发起分包计量驳回样本明细-2', 168000.0000
) s
JOIN sub_measure sm
  ON sm.tenant_id = 0
 AND sm.measure_code = s.measure_code
 AND sm.deleted_flag = 0
WHERE NOT EXISTS (
    SELECT 1
    FROM sub_measure_item smi
    WHERE smi.id = s.item_id
);

INSERT INTO wf_instance (
    id, tenant_id, template_id,
    business_type, business_id, project_id, contract_id,
    title, amount, instance_status,
    current_round, resubmit_count, business_revision,
    initiator_id, business_summary, variables,
    started_at, ended_at, created_by, created_at, updated_by, updated_at,
    deleted_flag, remark
)
SELECT s.instance_id, 0, 50001,
       'CONTRACT_APPROVAL', s.contract_id, @demo_project_id, s.contract_id,
       s.title, s.amount, s.instance_status,
       1, 0, 1,
       1, NULL, NULL,
       DATE_SUB(NOW(), INTERVAL (s.offset_minute + 5) MINUTE),
       CASE WHEN s.instance_status = 'RUNNING' THEN NULL ELSE DATE_SUB(NOW(), INTERVAL s.offset_minute MINUTE) END,
       1, DATE_SUB(NOW(), INTERVAL (s.offset_minute + 5) MINUTE), 1, DATE_SUB(NOW(), INTERVAL s.offset_minute MINUTE),
       0, 'V108审批中心我发起增强实例样本'
FROM (
    SELECT 978000000000001001 AS instance_id, 978000000000000101 AS contract_id, '审批中心我发起合同运行演示-1' AS title, 410000.00 AS amount, 'RUNNING' AS instance_status, 4 AS offset_minute
    UNION ALL
    SELECT 978000000000001002, 978000000000000102, '审批中心我发起合同通过演示-1', 430000.00, 'APPROVED', 10
    UNION ALL
    SELECT 978000000000001003, 978000000000000103, '审批中心我发起合同驳回演示-1', 450000.00, 'REJECTED', 7
    UNION ALL
    SELECT 978000000000001004, 978000000000000104, '审批中心我发起合同撤回演示-1', 470000.00, 'WITHDRAWN', 11
    UNION ALL
    SELECT 978000000000001005, 978000000000000105, '审批中心我发起合同通过演示-2', 490000.00, 'APPROVED', 14
) s
WHERE EXISTS (
    SELECT 1
    FROM ct_contract c
    WHERE c.id = s.contract_id
      AND c.deleted_flag = 0
)
  AND NOT EXISTS (
      SELECT 1
      FROM wf_instance i
      WHERE i.business_type = 'CONTRACT_APPROVAL'
        AND i.business_id = s.contract_id
        AND i.deleted_flag = 0
  );

INSERT INTO wf_instance (
    id, tenant_id, template_id,
    business_type, business_id, project_id, contract_id,
    title, amount, instance_status,
    current_round, resubmit_count, business_revision,
    initiator_id, business_summary, variables,
    started_at, ended_at, created_by, created_at, updated_by, updated_at,
    deleted_flag, remark
)
SELECT s.instance_id, 0, 50010,
       'PURCHASE_REQUEST', s.request_id, @demo_project_id, @demo_contract_id,
       s.title, NULL, s.instance_status,
       1, 0, 1,
       1, NULL, NULL,
       DATE_SUB(NOW(), INTERVAL (s.offset_minute + 5) MINUTE),
       CASE WHEN s.instance_status = 'RUNNING' THEN NULL ELSE DATE_SUB(NOW(), INTERVAL s.offset_minute MINUTE) END,
       1, DATE_SUB(NOW(), INTERVAL (s.offset_minute + 5) MINUTE), 1, DATE_SUB(NOW(), INTERVAL s.offset_minute MINUTE),
       0, 'V108审批中心我发起增强实例样本'
FROM (
    SELECT 978000000000002001 AS instance_id, 978000000000000201 AS request_id, '审批中心我发起采购申请运行演示-1' AS title, 'RUNNING' AS instance_status, 3 AS offset_minute
    UNION ALL
    SELECT 978000000000002002, 978000000000000202, '审批中心我发起采购申请通过演示-1', 'APPROVED', 9
    UNION ALL
    SELECT 978000000000002003, 978000000000000203, '审批中心我发起采购申请驳回演示-1', 'REJECTED', 6
    UNION ALL
    SELECT 978000000000002004, 978000000000000204, '审批中心我发起采购申请撤回演示-1', 'WITHDRAWN', 12
    UNION ALL
    SELECT 978000000000002005, 978000000000000205, '审批中心我发起采购申请运行演示-2', 'RUNNING', 1
    UNION ALL
    SELECT 978000000000002006, 978000000000000206, '审批中心我发起采购申请撤回演示-2', 'WITHDRAWN', 15
) s
WHERE EXISTS (
    SELECT 1
    FROM mat_purchase_request pr
    WHERE pr.id = s.request_id
      AND pr.deleted_flag = 0
)
  AND NOT EXISTS (
      SELECT 1
      FROM wf_instance i
      WHERE i.business_type = 'PURCHASE_REQUEST'
        AND i.business_id = s.request_id
        AND i.deleted_flag = 0
  );

INSERT INTO wf_instance (
    id, tenant_id, template_id,
    business_type, business_id, project_id, contract_id,
    title, amount, instance_status,
    current_round, resubmit_count, business_revision,
    initiator_id, business_summary, variables,
    started_at, ended_at, created_by, created_at, updated_by, updated_at,
    deleted_flag, remark
)
SELECT s.instance_id, 0, 50004,
       'SUB_MEASURE', s.measure_id, @demo_project_id, @demo_contract_id,
       s.title, s.amount, s.instance_status,
       1, 0, 1,
       1, NULL, NULL,
       DATE_SUB(NOW(), INTERVAL (s.offset_minute + 5) MINUTE),
       CASE WHEN s.instance_status = 'RUNNING' THEN NULL ELSE DATE_SUB(NOW(), INTERVAL s.offset_minute MINUTE) END,
       1, DATE_SUB(NOW(), INTERVAL (s.offset_minute + 5) MINUTE), 1, DATE_SUB(NOW(), INTERVAL s.offset_minute MINUTE),
       0, 'V108审批中心我发起增强实例样本'
FROM (
    SELECT 978000000000003001 AS instance_id, 978000000000000301 AS measure_id, '审批中心我发起分包计量运行演示-1' AS title, 128000.00 AS amount, 'RUNNING' AS instance_status, 2 AS offset_minute
    UNION ALL
    SELECT 978000000000003002, 978000000000000302, '审批中心我发起分包计量通过演示-1', 138000.00, 'APPROVED', 8
    UNION ALL
    SELECT 978000000000003003, 978000000000000303, '审批中心我发起分包计量驳回演示-1', 148000.00, 'REJECTED', 5
    UNION ALL
    SELECT 978000000000003004, 978000000000000304, '审批中心我发起分包计量撤回演示-1', 158000.00, 'WITHDRAWN', 13
    UNION ALL
    SELECT 978000000000003005, 978000000000000305, '审批中心我发起分包计量驳回演示-2', 168000.00, 'REJECTED', 16
) s
WHERE EXISTS (
    SELECT 1
    FROM sub_measure sm
    WHERE sm.id = s.measure_id
      AND sm.deleted_flag = 0
)
  AND NOT EXISTS (
      SELECT 1
      FROM wf_instance i
      WHERE i.business_type = 'SUB_MEASURE'
        AND i.business_id = s.measure_id
        AND i.deleted_flag = 0
  );

INSERT INTO wf_node_instance (
    id, tenant_id, instance_id, template_node_id,
    node_code, node_name, node_order, approve_mode, node_status,
    round_no, pass_rule_json, reject_rule_json,
    started_at, created_by, created_at, updated_by, updated_at,
    deleted_flag, remark
)
SELECT 978000000000001101, 0, 978000000000001001, tn.id,
       tn.node_code, tn.node_name, tn.node_order, tn.approve_mode,
       'ACTIVE',
       1, tn.pass_rule_json, tn.reject_rule_json,
       DATE_SUB(NOW(), INTERVAL 4 MINUTE), 1, DATE_SUB(NOW(), INTERVAL 4 MINUTE), 1, DATE_SUB(NOW(), INTERVAL 4 MINUTE),
       0, 'V108审批中心我发起合同运行节点'
FROM (
    SELECT id, node_code, node_name, node_order, approve_mode, pass_rule_json, reject_rule_json
    FROM wf_template_node
    WHERE template_id = 50001
      AND deleted_flag = 0
    ORDER BY node_order ASC, id ASC
    LIMIT 1
) tn
WHERE EXISTS (SELECT 1 FROM wf_instance WHERE id = 978000000000001001 AND deleted_flag = 0)
  AND NOT EXISTS (SELECT 1 FROM wf_node_instance WHERE id = 978000000000001101);

INSERT INTO wf_node_instance (
    id, tenant_id, instance_id, template_node_id,
    node_code, node_name, node_order, approve_mode, node_status,
    round_no, pass_rule_json, reject_rule_json,
    started_at, created_by, created_at, updated_by, updated_at,
    deleted_flag, remark
)
SELECT 978000000000002101, 0, 978000000000002001, tn.id,
       tn.node_code, tn.node_name, tn.node_order, tn.approve_mode,
       'ACTIVE',
       1, tn.pass_rule_json, tn.reject_rule_json,
       DATE_SUB(NOW(), INTERVAL 3 MINUTE), 1, DATE_SUB(NOW(), INTERVAL 3 MINUTE), 1, DATE_SUB(NOW(), INTERVAL 3 MINUTE),
       0, 'V108审批中心我发起采购申请运行节点-1'
FROM (
    SELECT id, node_code, node_name, node_order, approve_mode, pass_rule_json, reject_rule_json
    FROM wf_template_node
    WHERE template_id = 50010
      AND deleted_flag = 0
    ORDER BY node_order ASC, id ASC
    LIMIT 1
) tn
WHERE EXISTS (SELECT 1 FROM wf_instance WHERE id = 978000000000002001 AND deleted_flag = 0)
  AND NOT EXISTS (SELECT 1 FROM wf_node_instance WHERE id = 978000000000002101);

INSERT INTO wf_node_instance (
    id, tenant_id, instance_id, template_node_id,
    node_code, node_name, node_order, approve_mode, node_status,
    round_no, pass_rule_json, reject_rule_json,
    started_at, created_by, created_at, updated_by, updated_at,
    deleted_flag, remark
)
SELECT 978000000000002105, 0, 978000000000002005, tn.id,
       tn.node_code, tn.node_name, tn.node_order, tn.approve_mode,
       'ACTIVE',
       1, tn.pass_rule_json, tn.reject_rule_json,
       DATE_SUB(NOW(), INTERVAL 1 MINUTE), 1, DATE_SUB(NOW(), INTERVAL 1 MINUTE), 1, DATE_SUB(NOW(), INTERVAL 1 MINUTE),
       0, 'V108审批中心我发起采购申请运行节点-2'
FROM (
    SELECT id, node_code, node_name, node_order, approve_mode, pass_rule_json, reject_rule_json
    FROM wf_template_node
    WHERE template_id = 50010
      AND deleted_flag = 0
    ORDER BY node_order ASC, id ASC
    LIMIT 1
) tn
WHERE EXISTS (SELECT 1 FROM wf_instance WHERE id = 978000000000002005 AND deleted_flag = 0)
  AND NOT EXISTS (SELECT 1 FROM wf_node_instance WHERE id = 978000000000002105);

INSERT INTO wf_node_instance (
    id, tenant_id, instance_id, template_node_id,
    node_code, node_name, node_order, approve_mode, node_status,
    round_no, pass_rule_json, reject_rule_json,
    started_at, created_by, created_at, updated_by, updated_at,
    deleted_flag, remark
)
SELECT 978000000000003101, 0, 978000000000003001, tn.id,
       tn.node_code, tn.node_name, tn.node_order, tn.approve_mode,
       'ACTIVE',
       1, tn.pass_rule_json, tn.reject_rule_json,
       DATE_SUB(NOW(), INTERVAL 2 MINUTE), 1, DATE_SUB(NOW(), INTERVAL 2 MINUTE), 1, DATE_SUB(NOW(), INTERVAL 2 MINUTE),
       0, 'V108审批中心我发起分包计量运行节点'
FROM (
    SELECT id, node_code, node_name, node_order, approve_mode, pass_rule_json, reject_rule_json
    FROM wf_template_node
    WHERE template_id = 50004
      AND deleted_flag = 0
    ORDER BY node_order ASC, id ASC
    LIMIT 1
) tn
WHERE EXISTS (SELECT 1 FROM wf_instance WHERE id = 978000000000003001 AND deleted_flag = 0)
  AND NOT EXISTS (SELECT 1 FROM wf_node_instance WHERE id = 978000000000003101);

INSERT INTO wf_task (
    id, tenant_id, instance_id, node_instance_id,
    business_type, business_id,
    approver_id, approver_name, task_status,
    round_no, task_version,
    received_at,
    created_by, created_at, updated_by, updated_at,
    deleted_flag, remark
)
SELECT 978000000000001201, 0, 978000000000001001,
       978000000000001101,
       'CONTRACT_APPROVAL', 978000000000000101,
       1, '系统管理员', 'PENDING',
       1, 1, DATE_SUB(NOW(), INTERVAL 4 MINUTE),
       1, DATE_SUB(NOW(), INTERVAL 4 MINUTE), 1, DATE_SUB(NOW(), INTERVAL 4 MINUTE),
       0, 'V108审批中心我发起合同运行待办'
FROM dual
WHERE EXISTS (SELECT 1 FROM wf_node_instance WHERE id = 978000000000001101)
  AND NOT EXISTS (SELECT 1 FROM wf_task WHERE id = 978000000000001201);

INSERT INTO wf_task (
    id, tenant_id, instance_id, node_instance_id,
    business_type, business_id,
    approver_id, approver_name, task_status,
    round_no, task_version,
    received_at,
    created_by, created_at, updated_by, updated_at,
    deleted_flag, remark
)
SELECT 978000000000002201, 0, 978000000000002001,
       978000000000002101,
       'PURCHASE_REQUEST', 978000000000000201,
       1, '系统管理员', 'PENDING',
       1, 1, DATE_SUB(NOW(), INTERVAL 3 MINUTE),
       1, DATE_SUB(NOW(), INTERVAL 3 MINUTE), 1, DATE_SUB(NOW(), INTERVAL 3 MINUTE),
       0, 'V108审批中心我发起采购申请运行待办-1'
FROM dual
WHERE EXISTS (SELECT 1 FROM wf_node_instance WHERE id = 978000000000002101)
  AND NOT EXISTS (SELECT 1 FROM wf_task WHERE id = 978000000000002201);

INSERT INTO wf_task (
    id, tenant_id, instance_id, node_instance_id,
    business_type, business_id,
    approver_id, approver_name, task_status,
    round_no, task_version,
    received_at,
    created_by, created_at, updated_by, updated_at,
    deleted_flag, remark
)
SELECT 978000000000002205, 0, 978000000000002005,
       978000000000002105,
       'PURCHASE_REQUEST', 978000000000000205,
       1, '系统管理员', 'PENDING',
       1, 1, DATE_SUB(NOW(), INTERVAL 1 MINUTE),
       1, DATE_SUB(NOW(), INTERVAL 1 MINUTE), 1, DATE_SUB(NOW(), INTERVAL 1 MINUTE),
       0, 'V108审批中心我发起采购申请运行待办-2'
FROM dual
WHERE EXISTS (SELECT 1 FROM wf_node_instance WHERE id = 978000000000002105)
  AND NOT EXISTS (SELECT 1 FROM wf_task WHERE id = 978000000000002205);

INSERT INTO wf_task (
    id, tenant_id, instance_id, node_instance_id,
    business_type, business_id,
    approver_id, approver_name, task_status,
    round_no, task_version,
    received_at,
    created_by, created_at, updated_by, updated_at,
    deleted_flag, remark
)
SELECT 978000000000003201, 0, 978000000000003001,
       978000000000003101,
       'SUB_MEASURE', 978000000000000301,
       1, '系统管理员', 'PENDING',
       1, 1, DATE_SUB(NOW(), INTERVAL 2 MINUTE),
       1, DATE_SUB(NOW(), INTERVAL 2 MINUTE), 1, DATE_SUB(NOW(), INTERVAL 2 MINUTE),
       0, 'V108审批中心我发起分包计量运行待办'
FROM dual
WHERE EXISTS (SELECT 1 FROM wf_node_instance WHERE id = 978000000000003101)
  AND NOT EXISTS (SELECT 1 FROM wf_task WHERE id = 978000000000003201);

INSERT INTO wf_record (
    id, tenant_id, instance_id, node_instance_id, task_id, round_no,
    business_type, business_id, node_code, node_name,
    action_type, action_name, operator_id, operator_name, comment, record_status,
    created_by, created_at, updated_by, updated_at, deleted_flag, remark
)
SELECT s.record_id, 0, s.instance_id, NULL, NULL, 1,
       s.business_type, s.business_id, NULL, NULL,
       s.action_type, s.action_name, 1, 'admin', s.comment_text, 'EFFECTIVE',
       1, DATE_SUB(NOW(), INTERVAL s.offset_minute MINUTE), 1, DATE_SUB(NOW(), INTERVAL s.offset_minute MINUTE), 0, 'V108审批中心我发起增强审批记录'
FROM (
    SELECT 978000000000001301 AS record_id, 978000000000001001 AS instance_id, 'CONTRACT_APPROVAL' AS business_type, 978000000000000101 AS business_id, 'SUBMIT' AS action_type, '提交审批' AS action_name, NULL AS comment_text, 4 AS offset_minute
    UNION ALL
    SELECT 978000000000001302, 978000000000001002, 'CONTRACT_APPROVAL', 978000000000000102, 'SUBMIT', '提交审批', NULL, 10
    UNION ALL
    SELECT 978000000000001303, 978000000000001002, 'CONTRACT_APPROVAL', 978000000000000102, 'APPROVE', '同意', 'V108通过样本', 9
    UNION ALL
    SELECT 978000000000001304, 978000000000001003, 'CONTRACT_APPROVAL', 978000000000000103, 'SUBMIT', '提交审批', NULL, 8
    UNION ALL
    SELECT 978000000000001305, 978000000000001003, 'CONTRACT_APPROVAL', 978000000000000103, 'REJECT', '驳回', 'V108驳回样本', 7
    UNION ALL
    SELECT 978000000000001306, 978000000000001004, 'CONTRACT_APPROVAL', 978000000000000104, 'SUBMIT', '提交审批', NULL, 12
    UNION ALL
    SELECT 978000000000001307, 978000000000001004, 'CONTRACT_APPROVAL', 978000000000000104, 'WITHDRAW', '撤回', 'V108撤回样本', 11
    UNION ALL
    SELECT 978000000000001308, 978000000000001005, 'CONTRACT_APPROVAL', 978000000000000105, 'SUBMIT', '提交审批', NULL, 15
    UNION ALL
    SELECT 978000000000001309, 978000000000001005, 'CONTRACT_APPROVAL', 978000000000000105, 'APPROVE', '同意', 'V108通过样本-2', 14
    UNION ALL
    SELECT 978000000000002301, 978000000000002001, 'PURCHASE_REQUEST', 978000000000000201, 'SUBMIT', '提交审批', NULL, 3
    UNION ALL
    SELECT 978000000000002302, 978000000000002002, 'PURCHASE_REQUEST', 978000000000000202, 'SUBMIT', '提交审批', NULL, 10
    UNION ALL
    SELECT 978000000000002303, 978000000000002002, 'PURCHASE_REQUEST', 978000000000000202, 'APPROVE', '同意', 'V108通过样本', 9
    UNION ALL
    SELECT 978000000000002304, 978000000000002003, 'PURCHASE_REQUEST', 978000000000000203, 'SUBMIT', '提交审批', NULL, 7
    UNION ALL
    SELECT 978000000000002305, 978000000000002003, 'PURCHASE_REQUEST', 978000000000000203, 'REJECT', '驳回', 'V108驳回样本', 6
    UNION ALL
    SELECT 978000000000002306, 978000000000002004, 'PURCHASE_REQUEST', 978000000000000204, 'SUBMIT', '提交审批', NULL, 13
    UNION ALL
    SELECT 978000000000002307, 978000000000002004, 'PURCHASE_REQUEST', 978000000000000204, 'WITHDRAW', '撤回', 'V108撤回样本', 12
    UNION ALL
    SELECT 978000000000002308, 978000000000002005, 'PURCHASE_REQUEST', 978000000000000205, 'SUBMIT', '提交审批', NULL, 1
    UNION ALL
    SELECT 978000000000002309, 978000000000002006, 'PURCHASE_REQUEST', 978000000000000206, 'SUBMIT', '提交审批', NULL, 16
    UNION ALL
    SELECT 978000000000002310, 978000000000002006, 'PURCHASE_REQUEST', 978000000000000206, 'WITHDRAW', '撤回', 'V108撤回样本-2', 15
    UNION ALL
    SELECT 978000000000003301, 978000000000003001, 'SUB_MEASURE', 978000000000000301, 'SUBMIT', '提交审批', NULL, 2
    UNION ALL
    SELECT 978000000000003302, 978000000000003002, 'SUB_MEASURE', 978000000000000302, 'SUBMIT', '提交审批', NULL, 9
    UNION ALL
    SELECT 978000000000003303, 978000000000003002, 'SUB_MEASURE', 978000000000000302, 'APPROVE', '同意', 'V108通过样本', 8
    UNION ALL
    SELECT 978000000000003304, 978000000000003003, 'SUB_MEASURE', 978000000000000303, 'SUBMIT', '提交审批', NULL, 6
    UNION ALL
    SELECT 978000000000003305, 978000000000003003, 'SUB_MEASURE', 978000000000000303, 'REJECT', '驳回', 'V108驳回样本', 5
    UNION ALL
    SELECT 978000000000003306, 978000000000003004, 'SUB_MEASURE', 978000000000000304, 'SUBMIT', '提交审批', NULL, 14
    UNION ALL
    SELECT 978000000000003307, 978000000000003004, 'SUB_MEASURE', 978000000000000304, 'WITHDRAW', '撤回', 'V108撤回样本', 13
    UNION ALL
    SELECT 978000000000003308, 978000000000003005, 'SUB_MEASURE', 978000000000000305, 'SUBMIT', '提交审批', NULL, 17
    UNION ALL
    SELECT 978000000000003309, 978000000000003005, 'SUB_MEASURE', 978000000000000305, 'REJECT', '驳回', 'V108驳回样本-2', 16
) s
WHERE EXISTS (
    SELECT 1
    FROM wf_instance i
    WHERE i.id = s.instance_id
      AND i.deleted_flag = 0
)
  AND NOT EXISTS (
      SELECT 1
      FROM wf_record r
      WHERE r.id = s.record_id
  );

SET FOREIGN_KEY_CHECKS = 1;
