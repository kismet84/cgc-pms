-- V111: 修复审批中心权限矩阵 workflow-only 样本
-- 范围：只补 V110 workflow-only 缺失的业务单据 / wf_instance / 节点 / 任务 / 提交记录。
-- 说明：优先复用默认演示项目、既有审批闭环样本链路和固定演示物料，不再依赖“最新 ACTIVE 项目”。

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

SET @workflow_only_user_id := (
    SELECT id
    FROM sys_user
    WHERE tenant_id = 0
      AND username = 'demo_workflow_only'
      AND deleted_flag = 0
    ORDER BY id ASC
    LIMIT 1
);

SET @stable_project_id := COALESCE(
    (
        SELECT id
        FROM pm_project
        WHERE tenant_id = 0
          AND id = 2071032241708793858
          AND deleted_flag = 0
        LIMIT 1
    ),
    (
        SELECT project_id
        FROM mat_purchase_request
        WHERE tenant_id = 0
          AND request_code = 'PR-DEMO-REAL-001'
          AND deleted_flag = 0
        ORDER BY id ASC
        LIMIT 1
    ),
    (
        SELECT project_id
        FROM sub_measure
        WHERE tenant_id = 0
          AND measure_code = 'SM-DEMO-REAL-001'
          AND deleted_flag = 0
        ORDER BY id ASC
        LIMIT 1
    ),
    (
        SELECT project_id
        FROM ct_contract
        WHERE tenant_id = 0
          AND id = 2071033100000000001
          AND deleted_flag = 0
        LIMIT 1
    ),
    (
        SELECT id
        FROM pm_project
        WHERE tenant_id = 0
          AND status = 'ACTIVE'
          AND deleted_flag = 0
        ORDER BY created_at DESC, id DESC
        LIMIT 1
    )
);

SET @anchor_contract_id := COALESCE(
    (
        SELECT id
        FROM ct_contract
        WHERE tenant_id = 0
          AND id = 2071033100000000001
          AND deleted_flag = 0
        LIMIT 1
    ),
    (
        SELECT contract_id
        FROM mat_purchase_request
        WHERE tenant_id = 0
          AND request_code = 'PR-DEMO-REAL-001'
          AND deleted_flag = 0
        ORDER BY id ASC
        LIMIT 1
    ),
    (
        SELECT contract_id
        FROM sub_measure
        WHERE tenant_id = 0
          AND measure_code = 'SM-DEMO-REAL-001'
          AND deleted_flag = 0
        ORDER BY id ASC
        LIMIT 1
    ),
    (
        SELECT id
        FROM ct_contract
        WHERE tenant_id = 0
          AND project_id = @stable_project_id
          AND contract_status = 'PERFORMING'
          AND approval_status = 'APPROVED'
          AND deleted_flag = 0
        ORDER BY created_at ASC, id ASC
        LIMIT 1
    ),
    (
        SELECT id
        FROM ct_contract
        WHERE tenant_id = 0
          AND project_id = @stable_project_id
          AND deleted_flag = 0
        ORDER BY created_at ASC, id ASC
        LIMIT 1
    )
);

SET @anchor_party_a_id := (
    SELECT party_a_id
    FROM ct_contract
    WHERE id = @anchor_contract_id
    LIMIT 1
);

SET @anchor_party_b_id := (
    SELECT party_b_id
    FROM ct_contract
    WHERE id = @anchor_contract_id
    LIMIT 1
);

SET @stable_material_id := COALESCE(
    (
        SELECT id
        FROM md_material
        WHERE tenant_id = 0
          AND id = 970000000000005001
          AND deleted_flag = 0
        LIMIT 1
    ),
    (
        SELECT material_id
        FROM mat_purchase_request_item
        WHERE tenant_id = 0
          AND request_id = (
              SELECT id
              FROM mat_purchase_request
              WHERE tenant_id = 0
                AND request_code = 'PR-DEMO-REAL-001'
                AND deleted_flag = 0
              ORDER BY id ASC
              LIMIT 1
          )
          AND deleted_flag = 0
        ORDER BY id ASC
        LIMIT 1
    ),
    (
        SELECT id
        FROM md_material
        WHERE tenant_id = 0
          AND status = 'ENABLE'
          AND deleted_flag = 0
        ORDER BY id ASC
        LIMIT 1
    )
);

SET @stable_partner_id := COALESCE(
    (
        SELECT partner_id
        FROM sub_measure
        WHERE tenant_id = 0
          AND measure_code = 'SM-DEMO-REAL-001'
          AND deleted_flag = 0
        ORDER BY id ASC
        LIMIT 1
    ),
    @anchor_party_b_id,
    (
        SELECT id
        FROM md_partner
        WHERE tenant_id = 0
          AND status = 'ENABLE'
          AND deleted_flag = 0
        ORDER BY id ASC
        LIMIT 1
    )
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
SELECT 980000000000001001, 0, @stable_project_id, @anchor_party_a_id, @anchor_party_b_id,
       'CT-DEMO-WF-PERM-ONLY-001', '审批矩阵流程只读合同样本', 'SUB',
       520000.00, 520000.00, 0.00,
       'DRAFT', 'APPROVING',
       CURDATE(), DATE_ADD(CURDATE(), INTERVAL 60 DAY),
       0,
       @workflow_only_user_id, @workflow_only_user_id, NOW(), NOW(),
       0, 'V110审批中心权限矩阵workflow-only业务样本'
FROM dual
WHERE @stable_project_id IS NOT NULL
  AND @workflow_only_user_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM ct_contract
      WHERE tenant_id = 0
        AND contract_code = 'CT-DEMO-WF-PERM-ONLY-001'
        AND deleted_flag = 0
  );

SET @workflow_only_contract_id := (
    SELECT id
    FROM ct_contract
    WHERE tenant_id = 0
      AND contract_code = 'CT-DEMO-WF-PERM-ONLY-001'
      AND deleted_flag = 0
    ORDER BY id ASC
    LIMIT 1
);

INSERT INTO mat_purchase_request (
    id, tenant_id, project_id, request_code, contract_id,
    approval_status, status, created_by, updated_by, created_at, updated_at,
    deleted_flag, remark
)
SELECT 980000000000002001, 0, @stable_project_id, 'PR-DEMO-WF-PERM-ONLY-001', @anchor_contract_id,
       'APPROVING', 'DRAFT',
       @workflow_only_user_id, @workflow_only_user_id, NOW(), NOW(),
       0, 'V110审批中心权限矩阵workflow-only业务样本'
FROM dual
WHERE @stable_project_id IS NOT NULL
  AND @workflow_only_user_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM mat_purchase_request
      WHERE tenant_id = 0
        AND request_code = 'PR-DEMO-WF-PERM-ONLY-001'
        AND deleted_flag = 0
  );

SET @workflow_only_request_id := (
    SELECT id
    FROM mat_purchase_request
    WHERE tenant_id = 0
      AND request_code = 'PR-DEMO-WF-PERM-ONLY-001'
      AND deleted_flag = 0
    ORDER BY id ASC
    LIMIT 1
);

INSERT INTO mat_purchase_request_item (
    id, tenant_id, request_id, material_id, quantity, unit, planned_date,
    created_by, updated_by, created_at, updated_at, deleted_flag, remark
)
SELECT 980000000000002002, 0, @workflow_only_request_id, @stable_material_id, 4.0000, '批',
       DATE_ADD(CURDATE(), INTERVAL 7 DAY),
       @workflow_only_user_id, @workflow_only_user_id, NOW(), NOW(),
       0, 'V110审批中心权限矩阵workflow-only业务样本'
FROM dual
WHERE @workflow_only_request_id IS NOT NULL
  AND @stable_material_id IS NOT NULL
  AND @workflow_only_user_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM mat_purchase_request_item
      WHERE id = 980000000000002002
  );

INSERT INTO sub_measure (
    id, tenant_id, project_id, contract_id, partner_id, measure_code, measure_period, measure_date,
    reported_amount, approved_amount, deduction_amount, net_amount, approval_status,
    cost_generated_flag, status, created_by, updated_by, created_at, updated_at,
    deleted_flag, remark
)
SELECT 980000000000003001, 0, @stable_project_id, @anchor_contract_id, @stable_partner_id,
       'SM-DEMO-WF-PERM-ONLY-001', DATE_FORMAT(CURDATE(), '%Y-%m'), CURDATE(),
       86000.00, 82000.00, 4000.00, 82000.00, 'APPROVING',
       0, 'APPROVING', @workflow_only_user_id, @workflow_only_user_id, NOW(), NOW(),
       0, 'V110审批中心权限矩阵workflow-only业务样本'
FROM dual
WHERE @stable_project_id IS NOT NULL
  AND @workflow_only_user_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM sub_measure
      WHERE tenant_id = 0
        AND measure_code = 'SM-DEMO-WF-PERM-ONLY-001'
        AND deleted_flag = 0
  );

SET @workflow_only_measure_id := (
    SELECT id
    FROM sub_measure
    WHERE tenant_id = 0
      AND measure_code = 'SM-DEMO-WF-PERM-ONLY-001'
      AND deleted_flag = 0
    ORDER BY id ASC
    LIMIT 1
);

INSERT INTO sub_measure_item (
    id, tenant_id, measure_id, item_name, unit, contract_quantity, current_quantity, cumulative_quantity,
    unit_price, amount, created_by, updated_by, created_at, updated_at, deleted_flag, remark
)
SELECT 980000000000003002, 0, @workflow_only_measure_id, '审批矩阵流程只读分包计量明细', '项',
       1.0000, 1.0000, 1.0000, 82000.0000, 82000.00,
       @workflow_only_user_id, @workflow_only_user_id, NOW(), NOW(),
       0, 'V110审批中心权限矩阵workflow-only业务样本'
FROM dual
WHERE @workflow_only_measure_id IS NOT NULL
  AND @workflow_only_user_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM sub_measure_item
      WHERE id = 980000000000003002
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
SELECT 980000000000011001, 0, 50001,
       'CONTRACT_APPROVAL', @workflow_only_contract_id,
       COALESCE((SELECT project_id FROM ct_contract WHERE id = @workflow_only_contract_id LIMIT 1), @stable_project_id),
       @workflow_only_contract_id,
       '审批矩阵流程只读合同审批样本', 520000.00, 'RUNNING',
       1, 0, 1,
       @workflow_only_user_id, NULL, NULL,
       NOW(), @workflow_only_user_id, NOW(), @workflow_only_user_id, NOW(),
       0, 'V110审批中心权限矩阵workflow-only样本'
FROM dual
WHERE @workflow_only_contract_id IS NOT NULL
  AND @workflow_only_user_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM wf_instance
      WHERE business_type = 'CONTRACT_APPROVAL'
        AND business_id = @workflow_only_contract_id
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
SELECT 980000000000012001, 0, 50010,
       'PURCHASE_REQUEST', @workflow_only_request_id,
       COALESCE((SELECT project_id FROM mat_purchase_request WHERE id = @workflow_only_request_id LIMIT 1), @stable_project_id),
       (SELECT contract_id FROM mat_purchase_request WHERE id = @workflow_only_request_id LIMIT 1),
       '审批矩阵流程只读采购申请样本', NULL, 'RUNNING',
       1, 0, 1,
       @workflow_only_user_id, NULL, NULL,
       NOW(), @workflow_only_user_id, NOW(), @workflow_only_user_id, NOW(),
       0, 'V110审批中心权限矩阵workflow-only样本'
FROM dual
WHERE @workflow_only_request_id IS NOT NULL
  AND @workflow_only_user_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM wf_instance
      WHERE business_type = 'PURCHASE_REQUEST'
        AND business_id = @workflow_only_request_id
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
SELECT 980000000000013001, 0, 50004,
       'SUB_MEASURE', @workflow_only_measure_id,
       COALESCE((SELECT project_id FROM sub_measure WHERE id = @workflow_only_measure_id LIMIT 1), @stable_project_id),
       (SELECT contract_id FROM sub_measure WHERE id = @workflow_only_measure_id LIMIT 1),
       '审批矩阵流程只读分包计量样本', 82000.00, 'RUNNING',
       1, 0, 1,
       @workflow_only_user_id, NULL, NULL,
       NOW(), @workflow_only_user_id, NOW(), @workflow_only_user_id, NOW(),
       0, 'V110审批中心权限矩阵workflow-only样本'
FROM dual
WHERE @workflow_only_measure_id IS NOT NULL
  AND @workflow_only_user_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM wf_instance
      WHERE business_type = 'SUB_MEASURE'
        AND business_id = @workflow_only_measure_id
        AND deleted_flag = 0
  );

SET @workflow_only_contract_instance_id := (
    SELECT id
    FROM wf_instance
    WHERE business_type = 'CONTRACT_APPROVAL'
      AND business_id = @workflow_only_contract_id
      AND deleted_flag = 0
    ORDER BY id ASC
    LIMIT 1
);

SET @workflow_only_request_instance_id := (
    SELECT id
    FROM wf_instance
    WHERE business_type = 'PURCHASE_REQUEST'
      AND business_id = @workflow_only_request_id
      AND deleted_flag = 0
    ORDER BY id ASC
    LIMIT 1
);

SET @workflow_only_measure_instance_id := (
    SELECT id
    FROM wf_instance
    WHERE business_type = 'SUB_MEASURE'
      AND business_id = @workflow_only_measure_id
      AND deleted_flag = 0
    ORDER BY id ASC
    LIMIT 1
);

INSERT INTO wf_node_instance (
    id, tenant_id, instance_id, template_node_id,
    node_code, node_name, node_order, approve_mode, node_status,
    round_no, pass_rule_json, reject_rule_json,
    started_at, created_by, created_at, updated_by, updated_at,
    deleted_flag, remark
)
SELECT 980000000000021001, 0, @workflow_only_contract_instance_id, tn.id,
       tn.node_code, tn.node_name, tn.node_order, tn.approve_mode,
       'ACTIVE',
       1, tn.pass_rule_json, tn.reject_rule_json,
       NOW(), @workflow_only_user_id, NOW(), @workflow_only_user_id, NOW(),
       0, 'V110审批中心权限矩阵workflow-only节点'
FROM (
    SELECT id, node_code, node_name, node_order, approve_mode, pass_rule_json, reject_rule_json
    FROM wf_template_node
    WHERE template_id = 50001
      AND deleted_flag = 0
    ORDER BY node_order ASC, id ASC
    LIMIT 1
) tn
WHERE @workflow_only_contract_instance_id IS NOT NULL
  AND @workflow_only_user_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM wf_node_instance
      WHERE instance_id = @workflow_only_contract_instance_id
        AND node_order = 1
        AND round_no = 1
        AND deleted_flag = 0
  );

INSERT INTO wf_node_instance (
    id, tenant_id, instance_id, template_node_id,
    node_code, node_name, node_order, approve_mode, node_status,
    round_no, pass_rule_json, reject_rule_json,
    started_at, created_by, created_at, updated_by, updated_at,
    deleted_flag, remark
)
SELECT 980000000000022001, 0, @workflow_only_request_instance_id, tn.id,
       tn.node_code, tn.node_name, tn.node_order, tn.approve_mode,
       'ACTIVE',
       1, tn.pass_rule_json, tn.reject_rule_json,
       NOW(), @workflow_only_user_id, NOW(), @workflow_only_user_id, NOW(),
       0, 'V110审批中心权限矩阵workflow-only节点'
FROM (
    SELECT id, node_code, node_name, node_order, approve_mode, pass_rule_json, reject_rule_json
    FROM wf_template_node
    WHERE template_id = 50010
      AND deleted_flag = 0
    ORDER BY node_order ASC, id ASC
    LIMIT 1
) tn
WHERE @workflow_only_request_instance_id IS NOT NULL
  AND @workflow_only_user_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM wf_node_instance
      WHERE instance_id = @workflow_only_request_instance_id
        AND node_order = 1
        AND round_no = 1
        AND deleted_flag = 0
  );

INSERT INTO wf_node_instance (
    id, tenant_id, instance_id, template_node_id,
    node_code, node_name, node_order, approve_mode, node_status,
    round_no, pass_rule_json, reject_rule_json,
    started_at, created_by, created_at, updated_by, updated_at,
    deleted_flag, remark
)
SELECT 980000000000023001, 0, @workflow_only_measure_instance_id, tn.id,
       tn.node_code, tn.node_name, tn.node_order, tn.approve_mode,
       'ACTIVE',
       1, tn.pass_rule_json, tn.reject_rule_json,
       NOW(), @workflow_only_user_id, NOW(), @workflow_only_user_id, NOW(),
       0, 'V110审批中心权限矩阵workflow-only节点'
FROM (
    SELECT id, node_code, node_name, node_order, approve_mode, pass_rule_json, reject_rule_json
    FROM wf_template_node
    WHERE template_id = 50004
      AND deleted_flag = 0
    ORDER BY node_order ASC, id ASC
    LIMIT 1
) tn
WHERE @workflow_only_measure_instance_id IS NOT NULL
  AND @workflow_only_user_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM wf_node_instance
      WHERE instance_id = @workflow_only_measure_instance_id
        AND node_order = 1
        AND round_no = 1
        AND deleted_flag = 0
  );

SET @workflow_only_contract_node_id := (
    SELECT id
    FROM wf_node_instance
    WHERE instance_id = @workflow_only_contract_instance_id
      AND node_order = 1
      AND round_no = 1
      AND deleted_flag = 0
    ORDER BY id ASC
    LIMIT 1
);

SET @workflow_only_request_node_id := (
    SELECT id
    FROM wf_node_instance
    WHERE instance_id = @workflow_only_request_instance_id
      AND node_order = 1
      AND round_no = 1
      AND deleted_flag = 0
    ORDER BY id ASC
    LIMIT 1
);

SET @workflow_only_measure_node_id := (
    SELECT id
    FROM wf_node_instance
    WHERE instance_id = @workflow_only_measure_instance_id
      AND node_order = 1
      AND round_no = 1
      AND deleted_flag = 0
    ORDER BY id ASC
    LIMIT 1
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
SELECT 980000000000031001, 0, @workflow_only_contract_instance_id, @workflow_only_contract_node_id,
       'CONTRACT_APPROVAL', @workflow_only_contract_id,
       1, '系统管理员', 'PENDING',
       1, 1,
       NOW(),
       @workflow_only_user_id, NOW(), @workflow_only_user_id, NOW(),
       0, 'V110审批中心权限矩阵workflow-only待办'
FROM dual
WHERE @workflow_only_contract_instance_id IS NOT NULL
  AND @workflow_only_contract_node_id IS NOT NULL
  AND @workflow_only_contract_id IS NOT NULL
  AND @workflow_only_user_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM wf_task
      WHERE instance_id = @workflow_only_contract_instance_id
        AND approver_id = 1
        AND task_status = 'PENDING'
        AND deleted_flag = 0
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
SELECT 980000000000032001, 0, @workflow_only_request_instance_id, @workflow_only_request_node_id,
       'PURCHASE_REQUEST', @workflow_only_request_id,
       1, '系统管理员', 'PENDING',
       1, 1,
       NOW(),
       @workflow_only_user_id, NOW(), @workflow_only_user_id, NOW(),
       0, 'V110审批中心权限矩阵workflow-only待办'
FROM dual
WHERE @workflow_only_request_instance_id IS NOT NULL
  AND @workflow_only_request_node_id IS NOT NULL
  AND @workflow_only_request_id IS NOT NULL
  AND @workflow_only_user_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM wf_task
      WHERE instance_id = @workflow_only_request_instance_id
        AND approver_id = 1
        AND task_status = 'PENDING'
        AND deleted_flag = 0
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
SELECT 980000000000033001, 0, @workflow_only_measure_instance_id, @workflow_only_measure_node_id,
       'SUB_MEASURE', @workflow_only_measure_id,
       1, '系统管理员', 'PENDING',
       1, 1,
       NOW(),
       @workflow_only_user_id, NOW(), @workflow_only_user_id, NOW(),
       0, 'V110审批中心权限矩阵workflow-only待办'
FROM dual
WHERE @workflow_only_measure_instance_id IS NOT NULL
  AND @workflow_only_measure_node_id IS NOT NULL
  AND @workflow_only_measure_id IS NOT NULL
  AND @workflow_only_user_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM wf_task
      WHERE instance_id = @workflow_only_measure_instance_id
        AND approver_id = 1
        AND task_status = 'PENDING'
        AND deleted_flag = 0
  );

INSERT INTO wf_record (
    id, tenant_id, instance_id, node_instance_id, task_id, round_no,
    business_type, business_id, node_code, node_name,
    action_type, action_name, operator_id, operator_name, comment, record_status,
    created_by, created_at, updated_by, updated_at, deleted_flag, remark
)
SELECT 980000000000041001, 0, @workflow_only_contract_instance_id, NULL, NULL, 1,
       'CONTRACT_APPROVAL', @workflow_only_contract_id, NULL, NULL,
       'SUBMIT', '提交审批', @workflow_only_user_id, 'demo_workflow_only', NULL, 'EFFECTIVE',
       @workflow_only_user_id, NOW(), @workflow_only_user_id, NOW(),
       0, 'V110审批中心权限矩阵workflow-only记录'
FROM dual
WHERE @workflow_only_contract_instance_id IS NOT NULL
  AND @workflow_only_contract_id IS NOT NULL
  AND @workflow_only_user_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM wf_record
      WHERE instance_id = @workflow_only_contract_instance_id
        AND action_type = 'SUBMIT'
        AND deleted_flag = 0
  );

INSERT INTO wf_record (
    id, tenant_id, instance_id, node_instance_id, task_id, round_no,
    business_type, business_id, node_code, node_name,
    action_type, action_name, operator_id, operator_name, comment, record_status,
    created_by, created_at, updated_by, updated_at, deleted_flag, remark
)
SELECT 980000000000042001, 0, @workflow_only_request_instance_id, NULL, NULL, 1,
       'PURCHASE_REQUEST', @workflow_only_request_id, NULL, NULL,
       'SUBMIT', '提交审批', @workflow_only_user_id, 'demo_workflow_only', NULL, 'EFFECTIVE',
       @workflow_only_user_id, NOW(), @workflow_only_user_id, NOW(),
       0, 'V110审批中心权限矩阵workflow-only记录'
FROM dual
WHERE @workflow_only_request_instance_id IS NOT NULL
  AND @workflow_only_request_id IS NOT NULL
  AND @workflow_only_user_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM wf_record
      WHERE instance_id = @workflow_only_request_instance_id
        AND action_type = 'SUBMIT'
        AND deleted_flag = 0
  );

INSERT INTO wf_record (
    id, tenant_id, instance_id, node_instance_id, task_id, round_no,
    business_type, business_id, node_code, node_name,
    action_type, action_name, operator_id, operator_name, comment, record_status,
    created_by, created_at, updated_by, updated_at, deleted_flag, remark
)
SELECT 980000000000043001, 0, @workflow_only_measure_instance_id, NULL, NULL, 1,
       'SUB_MEASURE', @workflow_only_measure_id, NULL, NULL,
       'SUBMIT', '提交审批', @workflow_only_user_id, 'demo_workflow_only', NULL, 'EFFECTIVE',
       @workflow_only_user_id, NOW(), @workflow_only_user_id, NOW(),
       0, 'V110审批中心权限矩阵workflow-only记录'
FROM dual
WHERE @workflow_only_measure_instance_id IS NOT NULL
  AND @workflow_only_measure_id IS NOT NULL
  AND @workflow_only_user_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM wf_record
      WHERE instance_id = @workflow_only_measure_instance_id
        AND action_type = 'SUBMIT'
        AND deleted_flag = 0
  );

SET FOREIGN_KEY_CHECKS = 1;
