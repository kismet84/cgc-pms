-- V110: 审批中心权限账号矩阵最小 demo-only 样本
-- H2 对等版本。只补 demo-only 用户/角色/绑定，以及 workflow-only / cc-readonly 最小样本。

INSERT INTO sys_user (
    id, tenant_id, username, password, real_name,
    status, is_admin, created_by, updated_by, created_at, updated_at,
    deleted_flag, remark
)
SELECT 980000000000000011, 0, 'demo_workflow_only',
       '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2',
       '流程只读演示账号',
       'ENABLE', 0, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
       0, 'V110审批中心权限矩阵demo账号'
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_user
    WHERE tenant_id = 0
      AND username = 'demo_workflow_only'
      AND deleted_flag = 0
);

INSERT INTO sys_user (
    id, tenant_id, username, password, real_name,
    status, is_admin, created_by, updated_by, created_at, updated_at,
    deleted_flag, remark
)
SELECT 980000000000000012, 0, 'demo_cc_readonly',
       '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2',
       '抄送只读演示账号',
       'ENABLE', 0, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
       0, 'V110审批中心权限矩阵demo账号'
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_user
    WHERE tenant_id = 0
      AND username = 'demo_cc_readonly'
      AND deleted_flag = 0
);

INSERT INTO sys_user (
    id, tenant_id, username, password, real_name,
    status, is_admin, created_by, updated_by, created_at, updated_at,
    deleted_flag, remark
)
SELECT 980000000000000013, 0, 'demo_non_participant',
       '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2',
       '非参与人演示账号',
       'ENABLE', 0, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
       0, 'V110审批中心权限矩阵demo账号'
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_user
    WHERE tenant_id = 0
      AND username = 'demo_non_participant'
      AND deleted_flag = 0
);

INSERT INTO sys_role (
    id, tenant_id, role_code, role_name, role_type,
    status, data_scope, created_by, updated_by, created_at, updated_at,
    deleted_flag, remark
)
SELECT 980000000000000111, 0, 'WORKFLOW_ONLY_DEMO', '审批矩阵-流程只读', 'CUSTOM',
       'ENABLE', 'ALL', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
       0, 'V110审批中心权限矩阵demo角色'
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_role
    WHERE tenant_id = 0
      AND role_code = 'WORKFLOW_ONLY_DEMO'
      AND deleted_flag = 0
);

INSERT INTO sys_role (
    id, tenant_id, role_code, role_name, role_type,
    status, data_scope, created_by, updated_by, created_at, updated_at,
    deleted_flag, remark
)
SELECT 980000000000000112, 0, 'CC_READONLY_DEMO', '审批矩阵-抄送只读', 'CUSTOM',
       'ENABLE', 'ALL', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
       0, 'V110审批中心权限矩阵demo角色'
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_role
    WHERE tenant_id = 0
      AND role_code = 'CC_READONLY_DEMO'
      AND deleted_flag = 0
);

INSERT INTO sys_role (
    id, tenant_id, role_code, role_name, role_type,
    status, data_scope, created_by, updated_by, created_at, updated_at,
    deleted_flag, remark
)
SELECT 980000000000000113, 0, 'NON_PARTICIPANT_DEMO', '审批矩阵-非参与人', 'CUSTOM',
       'ENABLE', 'ALL', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
       0, 'V110审批中心权限矩阵demo角色'
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_role
    WHERE tenant_id = 0
      AND role_code = 'NON_PARTICIPANT_DEMO'
      AND deleted_flag = 0
);

INSERT INTO sys_user_role (id, user_id, role_id)
SELECT 980000000000000211,
       (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0 LIMIT 1),
       (SELECT id FROM sys_role WHERE tenant_id = 0 AND role_code = 'WORKFLOW_ONLY_DEMO' AND deleted_flag = 0 LIMIT 1)
WHERE EXISTS (SELECT 1 FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0)
  AND EXISTS (SELECT 1 FROM sys_role WHERE tenant_id = 0 AND role_code = 'WORKFLOW_ONLY_DEMO' AND deleted_flag = 0)
  AND NOT EXISTS (
      SELECT 1
      FROM sys_user_role
      WHERE user_id = (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0 LIMIT 1)
        AND role_id = (SELECT id FROM sys_role WHERE tenant_id = 0 AND role_code = 'WORKFLOW_ONLY_DEMO' AND deleted_flag = 0 LIMIT 1)
  );

INSERT INTO sys_user_role (id, user_id, role_id)
SELECT 980000000000000212,
       (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_cc_readonly' AND deleted_flag = 0 LIMIT 1),
       (SELECT id FROM sys_role WHERE tenant_id = 0 AND role_code = 'CC_READONLY_DEMO' AND deleted_flag = 0 LIMIT 1)
WHERE EXISTS (SELECT 1 FROM sys_user WHERE tenant_id = 0 AND username = 'demo_cc_readonly' AND deleted_flag = 0)
  AND EXISTS (SELECT 1 FROM sys_role WHERE tenant_id = 0 AND role_code = 'CC_READONLY_DEMO' AND deleted_flag = 0)
  AND NOT EXISTS (
      SELECT 1
      FROM sys_user_role
      WHERE user_id = (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_cc_readonly' AND deleted_flag = 0 LIMIT 1)
        AND role_id = (SELECT id FROM sys_role WHERE tenant_id = 0 AND role_code = 'CC_READONLY_DEMO' AND deleted_flag = 0 LIMIT 1)
  );

INSERT INTO sys_user_role (id, user_id, role_id)
SELECT 980000000000000213,
       (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_non_participant' AND deleted_flag = 0 LIMIT 1),
       (SELECT id FROM sys_role WHERE tenant_id = 0 AND role_code = 'NON_PARTICIPANT_DEMO' AND deleted_flag = 0 LIMIT 1)
WHERE EXISTS (SELECT 1 FROM sys_user WHERE tenant_id = 0 AND username = 'demo_non_participant' AND deleted_flag = 0)
  AND EXISTS (SELECT 1 FROM sys_role WHERE tenant_id = 0 AND role_code = 'NON_PARTICIPANT_DEMO' AND deleted_flag = 0)
  AND NOT EXISTS (
      SELECT 1
      FROM sys_user_role
      WHERE user_id = (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_non_participant' AND deleted_flag = 0 LIMIT 1)
        AND role_id = (SELECT id FROM sys_role WHERE tenant_id = 0 AND role_code = 'NON_PARTICIPANT_DEMO' AND deleted_flag = 0 LIMIT 1)
  );

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 980000000000000311,
       (SELECT id FROM sys_role WHERE tenant_id = 0 AND role_code = 'WORKFLOW_ONLY_DEMO' AND deleted_flag = 0 LIMIT 1),
       908
WHERE EXISTS (SELECT 1 FROM sys_role WHERE tenant_id = 0 AND role_code = 'WORKFLOW_ONLY_DEMO' AND deleted_flag = 0)
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_menu
      WHERE role_id = (SELECT id FROM sys_role WHERE tenant_id = 0 AND role_code = 'WORKFLOW_ONLY_DEMO' AND deleted_flag = 0 LIMIT 1)
        AND menu_id = 908
  );

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 980000000000000312,
       (SELECT id FROM sys_role WHERE tenant_id = 0 AND role_code = 'WORKFLOW_ONLY_DEMO' AND deleted_flag = 0 LIMIT 1),
       946
WHERE EXISTS (SELECT 1 FROM sys_role WHERE tenant_id = 0 AND role_code = 'WORKFLOW_ONLY_DEMO' AND deleted_flag = 0)
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_menu
      WHERE role_id = (SELECT id FROM sys_role WHERE tenant_id = 0 AND role_code = 'WORKFLOW_ONLY_DEMO' AND deleted_flag = 0 LIMIT 1)
        AND menu_id = 946
  );

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 980000000000000313,
       (SELECT id FROM sys_role WHERE tenant_id = 0 AND role_code = 'WORKFLOW_ONLY_DEMO' AND deleted_flag = 0 LIMIT 1),
       947
WHERE EXISTS (SELECT 1 FROM sys_role WHERE tenant_id = 0 AND role_code = 'WORKFLOW_ONLY_DEMO' AND deleted_flag = 0)
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_menu
      WHERE role_id = (SELECT id FROM sys_role WHERE tenant_id = 0 AND role_code = 'WORKFLOW_ONLY_DEMO' AND deleted_flag = 0 LIMIT 1)
        AND menu_id = 947
  );

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 980000000000000314,
       (SELECT id FROM sys_role WHERE tenant_id = 0 AND role_code = 'WORKFLOW_ONLY_DEMO' AND deleted_flag = 0 LIMIT 1),
       949
WHERE EXISTS (SELECT 1 FROM sys_role WHERE tenant_id = 0 AND role_code = 'WORKFLOW_ONLY_DEMO' AND deleted_flag = 0)
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_menu
      WHERE role_id = (SELECT id FROM sys_role WHERE tenant_id = 0 AND role_code = 'WORKFLOW_ONLY_DEMO' AND deleted_flag = 0 LIMIT 1)
        AND menu_id = 949
  );

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 980000000000000321,
       (SELECT id FROM sys_role WHERE tenant_id = 0 AND role_code = 'CC_READONLY_DEMO' AND deleted_flag = 0 LIMIT 1),
       908
WHERE EXISTS (SELECT 1 FROM sys_role WHERE tenant_id = 0 AND role_code = 'CC_READONLY_DEMO' AND deleted_flag = 0)
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_menu
      WHERE role_id = (SELECT id FROM sys_role WHERE tenant_id = 0 AND role_code = 'CC_READONLY_DEMO' AND deleted_flag = 0 LIMIT 1)
        AND menu_id = 908
  );

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 980000000000000322,
       (SELECT id FROM sys_role WHERE tenant_id = 0 AND role_code = 'CC_READONLY_DEMO' AND deleted_flag = 0 LIMIT 1),
       948
WHERE EXISTS (SELECT 1 FROM sys_role WHERE tenant_id = 0 AND role_code = 'CC_READONLY_DEMO' AND deleted_flag = 0)
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_menu
      WHERE role_id = (SELECT id FROM sys_role WHERE tenant_id = 0 AND role_code = 'CC_READONLY_DEMO' AND deleted_flag = 0 LIMIT 1)
        AND menu_id = 948
  );

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 980000000000000331,
       (SELECT id FROM sys_role WHERE tenant_id = 0 AND role_code = 'NON_PARTICIPANT_DEMO' AND deleted_flag = 0 LIMIT 1),
       908
WHERE EXISTS (SELECT 1 FROM sys_role WHERE tenant_id = 0 AND role_code = 'NON_PARTICIPANT_DEMO' AND deleted_flag = 0)
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_menu
      WHERE role_id = (SELECT id FROM sys_role WHERE tenant_id = 0 AND role_code = 'NON_PARTICIPANT_DEMO' AND deleted_flag = 0 LIMIT 1)
        AND menu_id = 908
  );

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 980000000000000332,
       (SELECT id FROM sys_role WHERE tenant_id = 0 AND role_code = 'NON_PARTICIPANT_DEMO' AND deleted_flag = 0 LIMIT 1),
       946
WHERE EXISTS (SELECT 1 FROM sys_role WHERE tenant_id = 0 AND role_code = 'NON_PARTICIPANT_DEMO' AND deleted_flag = 0)
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_menu
      WHERE role_id = (SELECT id FROM sys_role WHERE tenant_id = 0 AND role_code = 'NON_PARTICIPANT_DEMO' AND deleted_flag = 0 LIMIT 1)
        AND menu_id = 946
  );

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 980000000000000333,
       (SELECT id FROM sys_role WHERE tenant_id = 0 AND role_code = 'NON_PARTICIPANT_DEMO' AND deleted_flag = 0 LIMIT 1),
       947
WHERE EXISTS (SELECT 1 FROM sys_role WHERE tenant_id = 0 AND role_code = 'NON_PARTICIPANT_DEMO' AND deleted_flag = 0)
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_menu
      WHERE role_id = (SELECT id FROM sys_role WHERE tenant_id = 0 AND role_code = 'NON_PARTICIPANT_DEMO' AND deleted_flag = 0 LIMIT 1)
        AND menu_id = 947
  );

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 980000000000000334,
       (SELECT id FROM sys_role WHERE tenant_id = 0 AND role_code = 'NON_PARTICIPANT_DEMO' AND deleted_flag = 0 LIMIT 1),
       949
WHERE EXISTS (SELECT 1 FROM sys_role WHERE tenant_id = 0 AND role_code = 'NON_PARTICIPANT_DEMO' AND deleted_flag = 0)
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_menu
      WHERE role_id = (SELECT id FROM sys_role WHERE tenant_id = 0 AND role_code = 'NON_PARTICIPANT_DEMO' AND deleted_flag = 0 LIMIT 1)
        AND menu_id = 949
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
SELECT 980000000000001001, 0, 10001, 20001, 20002,
       'CT-DEMO-WF-PERM-ONLY-001', '审批矩阵流程只读合同样本', 'SUB',
       520000.00, 520000.00, 0.00,
       'DRAFT', 'APPROVING',
       CURRENT_DATE, DATEADD('DAY', 60, CURRENT_DATE),
       0,
       (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0 LIMIT 1),
       (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0 LIMIT 1),
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
       0, 'V110审批中心权限矩阵workflow-only业务样本'
WHERE EXISTS (SELECT 1 FROM pm_project WHERE id = 10001 AND tenant_id = 0 AND deleted_flag = 0)
  AND EXISTS (SELECT 1 FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0)
  AND NOT EXISTS (
      SELECT 1
      FROM ct_contract
      WHERE tenant_id = 0
        AND contract_code = 'CT-DEMO-WF-PERM-ONLY-001'
        AND deleted_flag = 0
  );

INSERT INTO mat_purchase_request (
    id, tenant_id, project_id, request_code, contract_id,
    approval_status, status, created_by, updated_by, created_at, updated_at,
    deleted_flag, remark
)
SELECT 980000000000002001, 0, 10001, 'PR-DEMO-WF-PERM-ONLY-001', 30001,
       'APPROVING', 'DRAFT',
       (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0 LIMIT 1),
       (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0 LIMIT 1),
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
       0, 'V110审批中心权限矩阵workflow-only业务样本'
WHERE EXISTS (SELECT 1 FROM pm_project WHERE id = 10001 AND tenant_id = 0 AND deleted_flag = 0)
  AND EXISTS (SELECT 1 FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0)
  AND NOT EXISTS (
      SELECT 1
      FROM mat_purchase_request
      WHERE tenant_id = 0
        AND request_code = 'PR-DEMO-WF-PERM-ONLY-001'
        AND deleted_flag = 0
  );

INSERT INTO mat_purchase_request_item (
    id, tenant_id, request_id, material_id, quantity, unit, planned_date,
    created_by, updated_by, created_at, updated_at, deleted_flag, remark
)
SELECT 980000000000002002, 0,
       (SELECT id FROM mat_purchase_request WHERE tenant_id = 0 AND request_code = 'PR-DEMO-WF-PERM-ONLY-001' AND deleted_flag = 0 LIMIT 1),
       970000000000005001, 4.0000, '批', DATEADD('DAY', 7, CURRENT_DATE),
       (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0 LIMIT 1),
       (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0 LIMIT 1),
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 'V110审批中心权限矩阵workflow-only业务样本'
WHERE EXISTS (SELECT 1 FROM mat_purchase_request WHERE tenant_id = 0 AND request_code = 'PR-DEMO-WF-PERM-ONLY-001' AND deleted_flag = 0)
  AND EXISTS (SELECT 1 FROM md_material WHERE id = 970000000000005001 AND deleted_flag = 0)
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
SELECT 980000000000003001, 0, 10001, 30001, 20002,
       'SM-DEMO-WF-PERM-ONLY-001', FORMATDATETIME(CURRENT_DATE, 'yyyy-MM'), CURRENT_DATE,
       86000.00, 82000.00, 4000.00, 82000.00, 'APPROVING',
       0, 'APPROVING',
       (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0 LIMIT 1),
       (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0 LIMIT 1),
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
       0, 'V110审批中心权限矩阵workflow-only业务样本'
WHERE EXISTS (SELECT 1 FROM pm_project WHERE id = 10001 AND tenant_id = 0 AND deleted_flag = 0)
  AND EXISTS (SELECT 1 FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0)
  AND NOT EXISTS (
      SELECT 1
      FROM sub_measure
      WHERE tenant_id = 0
        AND measure_code = 'SM-DEMO-WF-PERM-ONLY-001'
        AND deleted_flag = 0
  );

INSERT INTO sub_measure_item (
    id, tenant_id, measure_id, item_name, unit, contract_quantity, current_quantity, cumulative_quantity,
    unit_price, amount, created_by, updated_by, created_at, updated_at, deleted_flag, remark
)
SELECT 980000000000003002, 0,
       (SELECT id FROM sub_measure WHERE tenant_id = 0 AND measure_code = 'SM-DEMO-WF-PERM-ONLY-001' AND deleted_flag = 0 LIMIT 1),
       '审批矩阵流程只读分包计量明细', '项',
       1.0000, 1.0000, 1.0000, 82000.0000, 82000.00,
       (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0 LIMIT 1),
       (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0 LIMIT 1),
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
       0, 'V110审批中心权限矩阵workflow-only业务样本'
WHERE EXISTS (SELECT 1 FROM sub_measure WHERE tenant_id = 0 AND measure_code = 'SM-DEMO-WF-PERM-ONLY-001' AND deleted_flag = 0)
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
       'CONTRACT_APPROVAL',
       (SELECT id FROM ct_contract WHERE tenant_id = 0 AND contract_code = 'CT-DEMO-WF-PERM-ONLY-001' AND deleted_flag = 0 LIMIT 1),
       10001,
       (SELECT id FROM ct_contract WHERE tenant_id = 0 AND contract_code = 'CT-DEMO-WF-PERM-ONLY-001' AND deleted_flag = 0 LIMIT 1),
       '审批矩阵流程只读合同审批样本', 520000.00, 'RUNNING',
       1, 0, 1,
       (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0 LIMIT 1),
       NULL, NULL,
       CURRENT_TIMESTAMP,
       (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0 LIMIT 1),
       CURRENT_TIMESTAMP,
       (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0 LIMIT 1),
       CURRENT_TIMESTAMP,
       0, 'V110审批中心权限矩阵workflow-only样本'
WHERE EXISTS (SELECT 1 FROM ct_contract WHERE tenant_id = 0 AND contract_code = 'CT-DEMO-WF-PERM-ONLY-001' AND deleted_flag = 0)
  AND EXISTS (SELECT 1 FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0)
  AND NOT EXISTS (
      SELECT 1
      FROM wf_instance
      WHERE business_type = 'CONTRACT_APPROVAL'
        AND business_id = (SELECT id FROM ct_contract WHERE tenant_id = 0 AND contract_code = 'CT-DEMO-WF-PERM-ONLY-001' AND deleted_flag = 0 LIMIT 1)
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
       'PURCHASE_REQUEST',
       (SELECT id FROM mat_purchase_request WHERE tenant_id = 0 AND request_code = 'PR-DEMO-WF-PERM-ONLY-001' AND deleted_flag = 0 LIMIT 1),
       10001,
       (SELECT contract_id FROM mat_purchase_request WHERE tenant_id = 0 AND request_code = 'PR-DEMO-WF-PERM-ONLY-001' AND deleted_flag = 0 LIMIT 1),
       '审批矩阵流程只读采购申请样本', CAST(NULL AS DECIMAL(18,2)), 'RUNNING',
       1, 0, 1,
       (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0 LIMIT 1),
       NULL, NULL,
       CURRENT_TIMESTAMP,
       (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0 LIMIT 1),
       CURRENT_TIMESTAMP,
       (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0 LIMIT 1),
       CURRENT_TIMESTAMP,
       0, 'V110审批中心权限矩阵workflow-only样本'
WHERE EXISTS (SELECT 1 FROM mat_purchase_request WHERE tenant_id = 0 AND request_code = 'PR-DEMO-WF-PERM-ONLY-001' AND deleted_flag = 0)
  AND EXISTS (SELECT 1 FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0)
  AND NOT EXISTS (
      SELECT 1
      FROM wf_instance
      WHERE business_type = 'PURCHASE_REQUEST'
        AND business_id = (SELECT id FROM mat_purchase_request WHERE tenant_id = 0 AND request_code = 'PR-DEMO-WF-PERM-ONLY-001' AND deleted_flag = 0 LIMIT 1)
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
       'SUB_MEASURE',
       (SELECT id FROM sub_measure WHERE tenant_id = 0 AND measure_code = 'SM-DEMO-WF-PERM-ONLY-001' AND deleted_flag = 0 LIMIT 1),
       10001,
       (SELECT contract_id FROM sub_measure WHERE tenant_id = 0 AND measure_code = 'SM-DEMO-WF-PERM-ONLY-001' AND deleted_flag = 0 LIMIT 1),
       '审批矩阵流程只读分包计量样本', 82000.00, 'RUNNING',
       1, 0, 1,
       (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0 LIMIT 1),
       NULL, NULL,
       CURRENT_TIMESTAMP,
       (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0 LIMIT 1),
       CURRENT_TIMESTAMP,
       (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0 LIMIT 1),
       CURRENT_TIMESTAMP,
       0, 'V110审批中心权限矩阵workflow-only样本'
WHERE EXISTS (SELECT 1 FROM sub_measure WHERE tenant_id = 0 AND measure_code = 'SM-DEMO-WF-PERM-ONLY-001' AND deleted_flag = 0)
  AND EXISTS (SELECT 1 FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0)
  AND NOT EXISTS (
      SELECT 1
      FROM wf_instance
      WHERE business_type = 'SUB_MEASURE'
        AND business_id = (SELECT id FROM sub_measure WHERE tenant_id = 0 AND measure_code = 'SM-DEMO-WF-PERM-ONLY-001' AND deleted_flag = 0 LIMIT 1)
        AND deleted_flag = 0
  );

INSERT INTO wf_node_instance (
    id, tenant_id, instance_id, template_node_id,
    node_code, node_name, node_order, approve_mode, node_status,
    round_no, pass_rule_json, reject_rule_json,
    started_at, created_by, created_at, updated_by, updated_at,
    deleted_flag, remark
)
SELECT 980000000000021001, 0, 980000000000011001, tn.id,
       tn.node_code, tn.node_name, tn.node_order, tn.approve_mode,
       'ACTIVE',
       1, tn.pass_rule_json, tn.reject_rule_json,
       CURRENT_TIMESTAMP,
       (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0 LIMIT 1),
       CURRENT_TIMESTAMP,
       (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0 LIMIT 1),
       CURRENT_TIMESTAMP,
       0, 'V110审批中心权限矩阵workflow-only节点'
FROM (
    SELECT id, node_code, node_name, node_order, approve_mode, pass_rule_json, reject_rule_json
    FROM wf_template_node
    WHERE template_id = 50001
      AND deleted_flag = 0
    ORDER BY node_order ASC, id ASC
    LIMIT 1
) tn
WHERE EXISTS (SELECT 1 FROM wf_instance WHERE id = 980000000000011001 AND deleted_flag = 0)
  AND EXISTS (SELECT 1 FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0)
  AND NOT EXISTS (SELECT 1 FROM wf_node_instance WHERE id = 980000000000021001);

INSERT INTO wf_node_instance (
    id, tenant_id, instance_id, template_node_id,
    node_code, node_name, node_order, approve_mode, node_status,
    round_no, pass_rule_json, reject_rule_json,
    started_at, created_by, created_at, updated_by, updated_at,
    deleted_flag, remark
)
SELECT 980000000000022001, 0, 980000000000012001, tn.id,
       tn.node_code, tn.node_name, tn.node_order, tn.approve_mode,
       'ACTIVE',
       1, tn.pass_rule_json, tn.reject_rule_json,
       CURRENT_TIMESTAMP,
       (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0 LIMIT 1),
       CURRENT_TIMESTAMP,
       (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0 LIMIT 1),
       CURRENT_TIMESTAMP,
       0, 'V110审批中心权限矩阵workflow-only节点'
FROM (
    SELECT id, node_code, node_name, node_order, approve_mode, pass_rule_json, reject_rule_json
    FROM wf_template_node
    WHERE template_id = 50010
      AND deleted_flag = 0
    ORDER BY node_order ASC, id ASC
    LIMIT 1
) tn
WHERE EXISTS (SELECT 1 FROM wf_instance WHERE id = 980000000000012001 AND deleted_flag = 0)
  AND EXISTS (SELECT 1 FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0)
  AND NOT EXISTS (SELECT 1 FROM wf_node_instance WHERE id = 980000000000022001);

INSERT INTO wf_node_instance (
    id, tenant_id, instance_id, template_node_id,
    node_code, node_name, node_order, approve_mode, node_status,
    round_no, pass_rule_json, reject_rule_json,
    started_at, created_by, created_at, updated_by, updated_at,
    deleted_flag, remark
)
SELECT 980000000000023001, 0, 980000000000013001, tn.id,
       tn.node_code, tn.node_name, tn.node_order, tn.approve_mode,
       'ACTIVE',
       1, tn.pass_rule_json, tn.reject_rule_json,
       CURRENT_TIMESTAMP,
       (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0 LIMIT 1),
       CURRENT_TIMESTAMP,
       (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0 LIMIT 1),
       CURRENT_TIMESTAMP,
       0, 'V110审批中心权限矩阵workflow-only节点'
FROM (
    SELECT id, node_code, node_name, node_order, approve_mode, pass_rule_json, reject_rule_json
    FROM wf_template_node
    WHERE template_id = 50004
      AND deleted_flag = 0
    ORDER BY node_order ASC, id ASC
    LIMIT 1
) tn
WHERE EXISTS (SELECT 1 FROM wf_instance WHERE id = 980000000000013001 AND deleted_flag = 0)
  AND EXISTS (SELECT 1 FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0)
  AND NOT EXISTS (SELECT 1 FROM wf_node_instance WHERE id = 980000000000023001);

INSERT INTO wf_task (
    id, tenant_id, instance_id, node_instance_id,
    business_type, business_id,
    approver_id, approver_name, task_status,
    round_no, task_version,
    received_at,
    created_by, created_at, updated_by, updated_at,
    deleted_flag, remark
)
SELECT 980000000000031001, 0, 980000000000011001, 980000000000021001,
       'CONTRACT_APPROVAL',
       (SELECT id FROM ct_contract WHERE tenant_id = 0 AND contract_code = 'CT-DEMO-WF-PERM-ONLY-001' AND deleted_flag = 0 LIMIT 1),
       1, '系统管理员', 'PENDING',
       1, 1, CURRENT_TIMESTAMP,
       (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0 LIMIT 1),
       CURRENT_TIMESTAMP,
       (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0 LIMIT 1),
       CURRENT_TIMESTAMP,
       0, 'V110审批中心权限矩阵workflow-only待办'
WHERE EXISTS (SELECT 1 FROM wf_node_instance WHERE id = 980000000000021001)
  AND NOT EXISTS (SELECT 1 FROM wf_task WHERE id = 980000000000031001);

INSERT INTO wf_task (
    id, tenant_id, instance_id, node_instance_id,
    business_type, business_id,
    approver_id, approver_name, task_status,
    round_no, task_version,
    received_at,
    created_by, created_at, updated_by, updated_at,
    deleted_flag, remark
)
SELECT 980000000000032001, 0, 980000000000012001, 980000000000022001,
       'PURCHASE_REQUEST',
       (SELECT id FROM mat_purchase_request WHERE tenant_id = 0 AND request_code = 'PR-DEMO-WF-PERM-ONLY-001' AND deleted_flag = 0 LIMIT 1),
       1, '系统管理员', 'PENDING',
       1, 1, CURRENT_TIMESTAMP,
       (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0 LIMIT 1),
       CURRENT_TIMESTAMP,
       (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0 LIMIT 1),
       CURRENT_TIMESTAMP,
       0, 'V110审批中心权限矩阵workflow-only待办'
WHERE EXISTS (SELECT 1 FROM wf_node_instance WHERE id = 980000000000022001)
  AND NOT EXISTS (SELECT 1 FROM wf_task WHERE id = 980000000000032001);

INSERT INTO wf_task (
    id, tenant_id, instance_id, node_instance_id,
    business_type, business_id,
    approver_id, approver_name, task_status,
    round_no, task_version,
    received_at,
    created_by, created_at, updated_by, updated_at,
    deleted_flag, remark
)
SELECT 980000000000033001, 0, 980000000000013001, 980000000000023001,
       'SUB_MEASURE',
       (SELECT id FROM sub_measure WHERE tenant_id = 0 AND measure_code = 'SM-DEMO-WF-PERM-ONLY-001' AND deleted_flag = 0 LIMIT 1),
       1, '系统管理员', 'PENDING',
       1, 1, CURRENT_TIMESTAMP,
       (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0 LIMIT 1),
       CURRENT_TIMESTAMP,
       (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0 LIMIT 1),
       CURRENT_TIMESTAMP,
       0, 'V110审批中心权限矩阵workflow-only待办'
WHERE EXISTS (SELECT 1 FROM wf_node_instance WHERE id = 980000000000023001)
  AND NOT EXISTS (SELECT 1 FROM wf_task WHERE id = 980000000000033001);

INSERT INTO wf_record (
    id, tenant_id, instance_id, node_instance_id, task_id, round_no,
    business_type, business_id, node_code, node_name,
    action_type, action_name, operator_id, operator_name, comment, record_status,
    created_by, created_at, updated_by, updated_at, deleted_flag, remark
)
SELECT 980000000000041001, 0, 980000000000011001, NULL, NULL, 1,
       'CONTRACT_APPROVAL',
       (SELECT id FROM ct_contract WHERE tenant_id = 0 AND contract_code = 'CT-DEMO-WF-PERM-ONLY-001' AND deleted_flag = 0 LIMIT 1),
       NULL, NULL,
       'SUBMIT', '提交审批',
       (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0 LIMIT 1),
       'demo_workflow_only', NULL, 'EFFECTIVE',
       (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0 LIMIT 1),
       CURRENT_TIMESTAMP,
       (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0 LIMIT 1),
       CURRENT_TIMESTAMP,
       0, 'V110审批中心权限矩阵workflow-only记录'
WHERE EXISTS (SELECT 1 FROM wf_instance WHERE id = 980000000000011001 AND deleted_flag = 0)
  AND NOT EXISTS (SELECT 1 FROM wf_record WHERE id = 980000000000041001);

INSERT INTO wf_record (
    id, tenant_id, instance_id, node_instance_id, task_id, round_no,
    business_type, business_id, node_code, node_name,
    action_type, action_name, operator_id, operator_name, comment, record_status,
    created_by, created_at, updated_by, updated_at, deleted_flag, remark
)
SELECT 980000000000042001, 0, 980000000000012001, NULL, NULL, 1,
       'PURCHASE_REQUEST',
       (SELECT id FROM mat_purchase_request WHERE tenant_id = 0 AND request_code = 'PR-DEMO-WF-PERM-ONLY-001' AND deleted_flag = 0 LIMIT 1),
       NULL, NULL,
       'SUBMIT', '提交审批',
       (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0 LIMIT 1),
       'demo_workflow_only', NULL, 'EFFECTIVE',
       (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0 LIMIT 1),
       CURRENT_TIMESTAMP,
       (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0 LIMIT 1),
       CURRENT_TIMESTAMP,
       0, 'V110审批中心权限矩阵workflow-only记录'
WHERE EXISTS (SELECT 1 FROM wf_instance WHERE id = 980000000000012001 AND deleted_flag = 0)
  AND NOT EXISTS (SELECT 1 FROM wf_record WHERE id = 980000000000042001);

INSERT INTO wf_record (
    id, tenant_id, instance_id, node_instance_id, task_id, round_no,
    business_type, business_id, node_code, node_name,
    action_type, action_name, operator_id, operator_name, comment, record_status,
    created_by, created_at, updated_by, updated_at, deleted_flag, remark
)
SELECT 980000000000043001, 0, 980000000000013001, NULL, NULL, 1,
       'SUB_MEASURE',
       (SELECT id FROM sub_measure WHERE tenant_id = 0 AND measure_code = 'SM-DEMO-WF-PERM-ONLY-001' AND deleted_flag = 0 LIMIT 1),
       NULL, NULL,
       'SUBMIT', '提交审批',
       (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0 LIMIT 1),
       'demo_workflow_only', NULL, 'EFFECTIVE',
       (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0 LIMIT 1),
       CURRENT_TIMESTAMP,
       (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_workflow_only' AND deleted_flag = 0 LIMIT 1),
       CURRENT_TIMESTAMP,
       0, 'V110审批中心权限矩阵workflow-only记录'
WHERE EXISTS (SELECT 1 FROM wf_instance WHERE id = 980000000000013001 AND deleted_flag = 0)
  AND NOT EXISTS (SELECT 1 FROM wf_record WHERE id = 980000000000043001);

INSERT INTO wf_cc (
    id, tenant_id, instance_id, cc_user_id, cc_user_name,
    business_type, business_id, title, is_read, created_at
)
SELECT s.cc_id, i.tenant_id, i.id,
       (SELECT id FROM sys_user WHERE tenant_id = 0 AND username = 'demo_cc_readonly' AND deleted_flag = 0 LIMIT 1),
       '抄送只读演示账号',
       i.business_type, i.business_id, i.title, 0, CURRENT_TIMESTAMP
FROM (
    SELECT 980000000000051001 AS cc_id, 978000000000001001 AS instance_id
    UNION ALL
    SELECT 980000000000051002, 978000000000001002
) s
JOIN wf_instance i
  ON i.tenant_id = 0
 AND i.id = s.instance_id
 AND i.deleted_flag = 0
WHERE EXISTS (SELECT 1 FROM sys_user WHERE tenant_id = 0 AND username = 'demo_cc_readonly' AND deleted_flag = 0)
  AND NOT EXISTS (
      SELECT 1
      FROM wf_cc c
      WHERE c.id = s.cc_id
  );
