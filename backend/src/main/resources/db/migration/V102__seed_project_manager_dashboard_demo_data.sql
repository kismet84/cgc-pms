-- V102__seed_project_manager_dashboard_demo_data.sql
-- 为当前默认 ACTIVE 演示项目补齐项目经理驾驶舱最小非空数据。

SET @demo_project_id := (
    SELECT id
    FROM pm_project
    WHERE tenant_id = 0
      AND status = 'ACTIVE'
      AND deleted_flag = 0
    ORDER BY created_at DESC, id DESC
    LIMIT 1
);

SET @demo_any_partner_id := (
    SELECT id
    FROM md_partner
    WHERE tenant_id = 0
      AND status = 'ENABLE'
      AND deleted_flag = 0
    ORDER BY id ASC
    LIMIT 1
);

SET @demo_second_partner_id := (
    SELECT id
    FROM md_partner
    WHERE tenant_id = 0
      AND status = 'ENABLE'
      AND deleted_flag = 0
      AND id <> COALESCE(@demo_any_partner_id, -1)
    ORDER BY id ASC
    LIMIT 1
);

SET @demo_party_a_id := COALESCE(
    (
        SELECT party_a_id
        FROM ct_contract
        WHERE tenant_id = 0
          AND project_id = @demo_project_id
          AND deleted_flag = 0
          AND party_a_id IS NOT NULL
        ORDER BY created_at ASC, id ASC
        LIMIT 1
    ),
    @demo_any_partner_id
);

SET @demo_party_b_id := COALESCE(
    (
        SELECT party_b_id
        FROM ct_contract
        WHERE tenant_id = 0
          AND project_id = @demo_project_id
          AND deleted_flag = 0
          AND party_b_id IS NOT NULL
        ORDER BY created_at ASC, id ASC
        LIMIT 1
    ),
    @demo_second_partner_id,
    @demo_any_partner_id
);

UPDATE pm_project
SET planned_end_date = DATE_SUB(CURDATE(), INTERVAL 7 DAY),
    updated_by = 1,
    updated_at = NOW()
WHERE id = @demo_project_id
  AND deleted_flag = 0
  AND (planned_end_date IS NULL OR planned_end_date >= CURDATE());

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
    972000000000000101, 0, @demo_project_id, @demo_party_a_id, @demo_party_b_id,
    'CT-DEMO-PM-001', '项目经理驾驶舱演示履约合同', 'SUB',
    8800000.00, 8800000.00, 0.00,
    'PERFORMING', 'APPROVED',
    DATE_SUB(CURDATE(), INTERVAL 45 DAY), DATE_ADD(CURDATE(), INTERVAL 10 DAY),
    0,
    1, 1, NOW(), NOW(),
    0, '项目经理驾驶舱最小演示数据'
FROM dual
WHERE @demo_project_id IS NOT NULL
  AND @demo_party_a_id IS NOT NULL
  AND @demo_party_b_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM ct_contract
      WHERE tenant_id = 0
        AND contract_code = 'CT-DEMO-PM-001'
        AND deleted_flag = 0
  );

SET @pm_demo_contract_id := (
    SELECT id
    FROM ct_contract
    WHERE tenant_id = 0
      AND contract_code = 'CT-DEMO-PM-001'
      AND deleted_flag = 0
    LIMIT 1
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
SELECT
    972000000000000201, 0, 1,
    'CONTRACT', @pm_demo_contract_id, @demo_project_id, @pm_demo_contract_id,
    '项目经理待办审批演示', 8800000.00, 'RUNNING',
    1, 0, 1,
    1, '项目经理驾驶舱合同审批演示摘要', NULL,
    NOW(), 1, NOW(), 1, NOW(),
    0, '项目经理驾驶舱最小演示数据'
FROM dual
WHERE @demo_project_id IS NOT NULL
  AND @pm_demo_contract_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM wf_instance
      WHERE business_type = 'CONTRACT'
        AND business_id = @pm_demo_contract_id
        AND deleted_flag = 0
  );

SET @pm_demo_instance_id := (
    SELECT id
    FROM wf_instance
    WHERE business_type = 'CONTRACT'
      AND business_id = @pm_demo_contract_id
      AND deleted_flag = 0
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
SELECT
    972000000000000301, 0, @pm_demo_instance_id, 1,
    'CONTRACT', @pm_demo_contract_id,
    1, '项目经理', 'PENDING',
    1, 1,
    DATE_SUB(NOW(), INTERVAL 6 DAY),
    1, NOW(), 1, NOW(),
    0, '项目经理驾驶舱当前用户待办演示'
FROM dual
WHERE @pm_demo_instance_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM wf_task
      WHERE id = 972000000000000301
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
SELECT
    972000000000000302, 0, @pm_demo_instance_id, 2,
    'CONTRACT', @pm_demo_contract_id,
    2, '项目总监', 'PENDING',
    1, 1,
    DATE_SUB(NOW(), INTERVAL 3 DAY),
    1, NOW(), 1, NOW(),
    0, '项目经理驾驶舱项目待审批演示'
FROM dual
WHERE @pm_demo_instance_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM wf_task
      WHERE id = 972000000000000302
  );
