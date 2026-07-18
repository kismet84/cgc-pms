-- V102__seed_project_manager_dashboard_demo_data.sql (H2 version)
-- 为默认演示项目补齐项目经理驾驶舱最小非空数据。

UPDATE pm_project
SET planned_end_date = DATEADD('DAY', -7, CURRENT_DATE),
    updated_by = 1,
    updated_at = NOW()
WHERE id = 10001
  AND deleted_flag = 0
  AND (planned_end_date IS NULL OR planned_end_date >= CURRENT_DATE);

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
    972000000000000101, 0, 10001, 20001, 20002,
    'CT-DEMO-PM-001', '项目经理驾驶舱演示履约合同', 'SUB',
    8800000.00, 8800000.00, 0.00,
    'PERFORMING', 'APPROVED',
    DATEADD('DAY', -45, CURRENT_DATE), DATEADD('DAY', 10, CURRENT_DATE),
    0,
    1, 1, NOW(), NOW(),
    0, '项目经理驾驶舱最小演示数据'
WHERE NOT EXISTS (
    SELECT 1
    FROM ct_contract
    WHERE tenant_id = 0
      AND contract_code = 'CT-DEMO-PM-001'
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
SELECT
    972000000000000201, 0, 1,
    'CONTRACT', 972000000000000101, 10001, 972000000000000101,
    '项目经理待办审批演示', 8800000.00, 'RUNNING',
    1, 0, 1,
    1, '项目经理驾驶舱合同审批演示摘要', NULL,
    NOW(), 1, NOW(), 1, NOW(),
    0, '项目经理驾驶舱最小演示数据'
WHERE EXISTS (
    SELECT 1
    FROM ct_contract
    WHERE id = 972000000000000101
      AND deleted_flag = 0
)
  AND NOT EXISTS (
      SELECT 1
      FROM wf_instance
      WHERE business_type = 'CONTRACT'
        AND business_id = 972000000000000101
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
SELECT
    972000000000000301, 0, 972000000000000201, 1,
    'CONTRACT', 972000000000000101,
    1, '项目经理', 'PENDING',
    1, 1,
    DATEADD('DAY', -6, CURRENT_TIMESTAMP),
    1, NOW(), 1, NOW(),
    0, '项目经理驾驶舱当前用户待办演示'
WHERE EXISTS (
    SELECT 1
    FROM wf_instance
    WHERE id = 972000000000000201
      AND deleted_flag = 0
)
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
    972000000000000302, 0, 972000000000000201, 2,
    'CONTRACT', 972000000000000101,
    2, '项目总监', 'PENDING',
    1, 1,
    DATEADD('DAY', -3, CURRENT_TIMESTAMP),
    1, NOW(), 1, NOW(),
    0, '项目经理驾驶舱项目待审批演示'
WHERE EXISTS (
    SELECT 1
    FROM wf_instance
    WHERE id = 972000000000000201
      AND deleted_flag = 0
)
  AND NOT EXISTS (
      SELECT 1
      FROM wf_task
      WHERE id = 972000000000000302
  );
