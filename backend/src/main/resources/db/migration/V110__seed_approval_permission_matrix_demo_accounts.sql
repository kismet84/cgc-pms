-- V110: 审批中心权限账号矩阵最小 demo-only 样本
-- 范围：只补 demo-only 用户/角色/绑定，以及 workflow-only / cc-readonly 最小样本。

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

INSERT INTO sys_user (
    id, tenant_id, username, password, real_name,
    status, is_admin, created_by, updated_by, created_at, updated_at,
    deleted_flag, remark
)
SELECT 980000000000000011, 0, 'demo_workflow_only',
       '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2',
       '流程只读演示账号',
       'ENABLE', 0, 1, 1, NOW(), NOW(),
       0, 'V110审批中心权限矩阵demo账号'
FROM dual
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
       'ENABLE', 0, 1, 1, NOW(), NOW(),
       0, 'V110审批中心权限矩阵demo账号'
FROM dual
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
       'ENABLE', 0, 1, 1, NOW(), NOW(),
       0, 'V110审批中心权限矩阵demo账号'
FROM dual
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
       'ENABLE', 'ALL', 1, 1, NOW(), NOW(),
       0, 'V110审批中心权限矩阵demo角色'
FROM dual
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
       'ENABLE', 'ALL', 1, 1, NOW(), NOW(),
       0, 'V110审批中心权限矩阵demo角色'
FROM dual
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
       'ENABLE', 'ALL', 1, 1, NOW(), NOW(),
       0, 'V110审批中心权限矩阵demo角色'
FROM dual
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_role
    WHERE tenant_id = 0
      AND role_code = 'NON_PARTICIPANT_DEMO'
      AND deleted_flag = 0
);

SET @workflow_only_user_id := (
    SELECT id
    FROM sys_user
    WHERE tenant_id = 0
      AND username = 'demo_workflow_only'
      AND deleted_flag = 0
    ORDER BY id ASC
    LIMIT 1
);

SET @cc_readonly_user_id := (
    SELECT id
    FROM sys_user
    WHERE tenant_id = 0
      AND username = 'demo_cc_readonly'
      AND deleted_flag = 0
    ORDER BY id ASC
    LIMIT 1
);

SET @non_participant_user_id := (
    SELECT id
    FROM sys_user
    WHERE tenant_id = 0
      AND username = 'demo_non_participant'
      AND deleted_flag = 0
    ORDER BY id ASC
    LIMIT 1
);

SET @workflow_only_role_id := (
    SELECT id
    FROM sys_role
    WHERE tenant_id = 0
      AND role_code = 'WORKFLOW_ONLY_DEMO'
      AND deleted_flag = 0
    ORDER BY id ASC
    LIMIT 1
);

SET @cc_readonly_role_id := (
    SELECT id
    FROM sys_role
    WHERE tenant_id = 0
      AND role_code = 'CC_READONLY_DEMO'
      AND deleted_flag = 0
    ORDER BY id ASC
    LIMIT 1
);

SET @non_participant_role_id := (
    SELECT id
    FROM sys_role
    WHERE tenant_id = 0
      AND role_code = 'NON_PARTICIPANT_DEMO'
      AND deleted_flag = 0
    ORDER BY id ASC
    LIMIT 1
);

INSERT INTO sys_user_role (id, user_id, role_id)
SELECT 980000000000000211, @workflow_only_user_id, @workflow_only_role_id
FROM dual
WHERE @workflow_only_user_id IS NOT NULL
  AND @workflow_only_role_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM sys_user_role
      WHERE user_id = @workflow_only_user_id
        AND role_id = @workflow_only_role_id
  );

INSERT INTO sys_user_role (id, user_id, role_id)
SELECT 980000000000000212, @cc_readonly_user_id, @cc_readonly_role_id
FROM dual
WHERE @cc_readonly_user_id IS NOT NULL
  AND @cc_readonly_role_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM sys_user_role
      WHERE user_id = @cc_readonly_user_id
        AND role_id = @cc_readonly_role_id
  );

INSERT INTO sys_user_role (id, user_id, role_id)
SELECT 980000000000000213, @non_participant_user_id, @non_participant_role_id
FROM dual
WHERE @non_participant_user_id IS NOT NULL
  AND @non_participant_role_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM sys_user_role
      WHERE user_id = @non_participant_user_id
        AND role_id = @non_participant_role_id
  );

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 980000000000000311, @workflow_only_role_id, 908
FROM dual
WHERE @workflow_only_role_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_menu
      WHERE role_id = @workflow_only_role_id
        AND menu_id = 908
  );

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 980000000000000312, @workflow_only_role_id, 946
FROM dual
WHERE @workflow_only_role_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_menu
      WHERE role_id = @workflow_only_role_id
        AND menu_id = 946
  );

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 980000000000000313, @workflow_only_role_id, 947
FROM dual
WHERE @workflow_only_role_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_menu
      WHERE role_id = @workflow_only_role_id
        AND menu_id = 947
  );

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 980000000000000314, @workflow_only_role_id, 949
FROM dual
WHERE @workflow_only_role_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_menu
      WHERE role_id = @workflow_only_role_id
        AND menu_id = 949
  );

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 980000000000000321, @cc_readonly_role_id, 908
FROM dual
WHERE @cc_readonly_role_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_menu
      WHERE role_id = @cc_readonly_role_id
        AND menu_id = 908
  );

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 980000000000000322, @cc_readonly_role_id, 948
FROM dual
WHERE @cc_readonly_role_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_menu
      WHERE role_id = @cc_readonly_role_id
        AND menu_id = 948
  );

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 980000000000000331, @non_participant_role_id, 908
FROM dual
WHERE @non_participant_role_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_menu
      WHERE role_id = @non_participant_role_id
        AND menu_id = 908
  );

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 980000000000000332, @non_participant_role_id, 946
FROM dual
WHERE @non_participant_role_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_menu
      WHERE role_id = @non_participant_role_id
        AND menu_id = 946
  );

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 980000000000000333, @non_participant_role_id, 947
FROM dual
WHERE @non_participant_role_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_menu
      WHERE role_id = @non_participant_role_id
        AND menu_id = 947
  );

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 980000000000000334, @non_participant_role_id, 949
FROM dual
WHERE @non_participant_role_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_menu
      WHERE role_id = @non_participant_role_id
        AND menu_id = 949
  );

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
SELECT 980000000000001001, 0, @demo_project_id, @demo_party_a_id, @demo_party_b_id,
       'CT-DEMO-WF-PERM-ONLY-001', '审批矩阵流程只读合同样本', 'SUB',
       520000.00, 520000.00, 0.00,
       'DRAFT', 'APPROVING',
       CURDATE(), DATE_ADD(CURDATE(), INTERVAL 60 DAY),
       0,
       @workflow_only_user_id, @workflow_only_user_id, NOW(), NOW(),
       0, 'V110审批中心权限矩阵workflow-only业务样本'
FROM dual
WHERE @demo_project_id IS NOT NULL
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
SELECT 980000000000002001, 0, @demo_project_id, 'PR-DEMO-WF-PERM-ONLY-001', @demo_contract_id,
       'APPROVING', 'DRAFT', @workflow_only_user_id, @workflow_only_user_id, NOW(), NOW(),
       0, 'V110审批中心权限矩阵workflow-only业务样本'
FROM dual
WHERE @demo_project_id IS NOT NULL
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
SELECT 980000000000002002, 0, @workflow_only_request_id, @demo_material_id, 4.0000, '批',
       DATE_ADD(CURDATE(), INTERVAL 7 DAY),
       @workflow_only_user_id, @workflow_only_user_id, NOW(), NOW(), 0, 'V110审批中心权限矩阵workflow-only业务样本'
FROM dual
WHERE @workflow_only_request_id IS NOT NULL
  AND @demo_material_id IS NOT NULL
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
SELECT 980000000000003001, 0, @demo_project_id, @demo_contract_id, @demo_partner_id,
       'SM-DEMO-WF-PERM-ONLY-001', DATE_FORMAT(CURDATE(), '%Y-%m'), CURDATE(),
       86000.00, 82000.00, 4000.00, 82000.00, 'APPROVING',
       0, 'APPROVING', @workflow_only_user_id, @workflow_only_user_id, NOW(), NOW(),
       0, 'V110审批中心权限矩阵workflow-only业务样本'
FROM dual
WHERE @demo_project_id IS NOT NULL
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
       @workflow_only_user_id, @workflow_only_user_id, NOW(), NOW(), 0, 'V110审批中心权限矩阵workflow-only业务样本'
FROM dual
WHERE @workflow_only_measure_id IS NOT NULL
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
       'CONTRACT_APPROVAL', @workflow_only_contract_id, @demo_project_id, @workflow_only_contract_id,
       '审批矩阵流程只读合同审批样本', 520000.00, 'RUNNING',
       1, 0, 1,
       @workflow_only_user_id, NULL, NULL,
       NOW(), @workflow_only_user_id, NOW(), @workflow_only_user_id, NOW(),
       0, 'V110审批中心权限矩阵workflow-only样本'
FROM dual
WHERE @workflow_only_contract_id IS NOT NULL
  AND @demo_project_id IS NOT NULL
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
       'PURCHASE_REQUEST', @workflow_only_request_id, @demo_project_id,
       (SELECT contract_id FROM mat_purchase_request WHERE id = @workflow_only_request_id),
       '审批矩阵流程只读采购申请样本', NULL, 'RUNNING',
       1, 0, 1,
       @workflow_only_user_id, NULL, NULL,
       NOW(), @workflow_only_user_id, NOW(), @workflow_only_user_id, NOW(),
       0, 'V110审批中心权限矩阵workflow-only样本'
FROM dual
WHERE @workflow_only_request_id IS NOT NULL
  AND @demo_project_id IS NOT NULL
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
       'SUB_MEASURE', @workflow_only_measure_id, @demo_project_id,
       (SELECT contract_id FROM sub_measure WHERE id = @workflow_only_measure_id),
       '审批矩阵流程只读分包计量样本', 82000.00, 'RUNNING',
       1, 0, 1,
       @workflow_only_user_id, NULL, NULL,
       NOW(), @workflow_only_user_id, NOW(), @workflow_only_user_id, NOW(),
       0, 'V110审批中心权限矩阵workflow-only样本'
FROM dual
WHERE @workflow_only_measure_id IS NOT NULL
  AND @demo_project_id IS NOT NULL
  AND @workflow_only_user_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM wf_instance
      WHERE business_type = 'SUB_MEASURE'
        AND business_id = @workflow_only_measure_id
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
WHERE @workflow_only_user_id IS NOT NULL
  AND EXISTS (SELECT 1 FROM wf_instance WHERE id = 980000000000011001 AND deleted_flag = 0)
  AND NOT EXISTS (
      SELECT 1
      FROM wf_node_instance
      WHERE id = 980000000000021001
  );

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
WHERE @workflow_only_user_id IS NOT NULL
  AND EXISTS (SELECT 1 FROM wf_instance WHERE id = 980000000000012001 AND deleted_flag = 0)
  AND NOT EXISTS (
      SELECT 1
      FROM wf_node_instance
      WHERE id = 980000000000022001
  );

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
WHERE @workflow_only_user_id IS NOT NULL
  AND EXISTS (SELECT 1 FROM wf_instance WHERE id = 980000000000013001 AND deleted_flag = 0)
  AND NOT EXISTS (
      SELECT 1
      FROM wf_node_instance
      WHERE id = 980000000000023001
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
SELECT 980000000000031001, 0, 980000000000011001, 980000000000021001,
       'CONTRACT_APPROVAL', @workflow_only_contract_id,
       1, '系统管理员', 'PENDING',
       1, 1,
       NOW(),
       @workflow_only_user_id, NOW(), @workflow_only_user_id, NOW(),
       0, 'V110审批中心权限矩阵workflow-only待办'
FROM dual
WHERE @workflow_only_contract_id IS NOT NULL
  AND @workflow_only_user_id IS NOT NULL
  AND EXISTS (SELECT 1 FROM wf_node_instance WHERE id = 980000000000021001)
  AND NOT EXISTS (
      SELECT 1
      FROM wf_task
      WHERE id = 980000000000031001
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
SELECT 980000000000032001, 0, 980000000000012001, 980000000000022001,
       'PURCHASE_REQUEST', @workflow_only_request_id,
       1, '系统管理员', 'PENDING',
       1, 1,
       NOW(),
       @workflow_only_user_id, NOW(), @workflow_only_user_id, NOW(),
       0, 'V110审批中心权限矩阵workflow-only待办'
FROM dual
WHERE @workflow_only_request_id IS NOT NULL
  AND @workflow_only_user_id IS NOT NULL
  AND EXISTS (SELECT 1 FROM wf_node_instance WHERE id = 980000000000022001)
  AND NOT EXISTS (
      SELECT 1
      FROM wf_task
      WHERE id = 980000000000032001
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
SELECT 980000000000033001, 0, 980000000000013001, 980000000000023001,
       'SUB_MEASURE', @workflow_only_measure_id,
       1, '系统管理员', 'PENDING',
       1, 1,
       NOW(),
       @workflow_only_user_id, NOW(), @workflow_only_user_id, NOW(),
       0, 'V110审批中心权限矩阵workflow-only待办'
FROM dual
WHERE @workflow_only_measure_id IS NOT NULL
  AND @workflow_only_user_id IS NOT NULL
  AND EXISTS (SELECT 1 FROM wf_node_instance WHERE id = 980000000000023001)
  AND NOT EXISTS (
      SELECT 1
      FROM wf_task
      WHERE id = 980000000000033001
  );

INSERT INTO wf_record (
    id, tenant_id, instance_id, node_instance_id, task_id, round_no,
    business_type, business_id, node_code, node_name,
    action_type, action_name, operator_id, operator_name, comment, record_status,
    created_by, created_at, updated_by, updated_at, deleted_flag, remark
)
SELECT 980000000000041001, 0, 980000000000011001, NULL, NULL, 1,
       'CONTRACT_APPROVAL', @workflow_only_contract_id, NULL, NULL,
       'SUBMIT', '提交审批', @workflow_only_user_id, 'demo_workflow_only', NULL, 'EFFECTIVE',
       @workflow_only_user_id, NOW(), @workflow_only_user_id, NOW(), 0, 'V110审批中心权限矩阵workflow-only记录'
FROM dual
WHERE @workflow_only_contract_id IS NOT NULL
  AND @workflow_only_user_id IS NOT NULL
  AND EXISTS (SELECT 1 FROM wf_instance WHERE id = 980000000000011001 AND deleted_flag = 0)
  AND NOT EXISTS (
      SELECT 1
      FROM wf_record
      WHERE id = 980000000000041001
  );

INSERT INTO wf_record (
    id, tenant_id, instance_id, node_instance_id, task_id, round_no,
    business_type, business_id, node_code, node_name,
    action_type, action_name, operator_id, operator_name, comment, record_status,
    created_by, created_at, updated_by, updated_at, deleted_flag, remark
)
SELECT 980000000000042001, 0, 980000000000012001, NULL, NULL, 1,
       'PURCHASE_REQUEST', @workflow_only_request_id, NULL, NULL,
       'SUBMIT', '提交审批', @workflow_only_user_id, 'demo_workflow_only', NULL, 'EFFECTIVE',
       @workflow_only_user_id, NOW(), @workflow_only_user_id, NOW(), 0, 'V110审批中心权限矩阵workflow-only记录'
FROM dual
WHERE @workflow_only_request_id IS NOT NULL
  AND @workflow_only_user_id IS NOT NULL
  AND EXISTS (SELECT 1 FROM wf_instance WHERE id = 980000000000012001 AND deleted_flag = 0)
  AND NOT EXISTS (
      SELECT 1
      FROM wf_record
      WHERE id = 980000000000042001
  );

INSERT INTO wf_record (
    id, tenant_id, instance_id, node_instance_id, task_id, round_no,
    business_type, business_id, node_code, node_name,
    action_type, action_name, operator_id, operator_name, comment, record_status,
    created_by, created_at, updated_by, updated_at, deleted_flag, remark
)
SELECT 980000000000043001, 0, 980000000000013001, NULL, NULL, 1,
       'SUB_MEASURE', @workflow_only_measure_id, NULL, NULL,
       'SUBMIT', '提交审批', @workflow_only_user_id, 'demo_workflow_only', NULL, 'EFFECTIVE',
       @workflow_only_user_id, NOW(), @workflow_only_user_id, NOW(), 0, 'V110审批中心权限矩阵workflow-only记录'
FROM dual
WHERE @workflow_only_measure_id IS NOT NULL
  AND @workflow_only_user_id IS NOT NULL
  AND EXISTS (SELECT 1 FROM wf_instance WHERE id = 980000000000013001 AND deleted_flag = 0)
  AND NOT EXISTS (
      SELECT 1
      FROM wf_record
      WHERE id = 980000000000043001
  );

INSERT INTO wf_cc (
    id, tenant_id, instance_id, cc_user_id, cc_user_name,
    business_type, business_id, title, is_read, created_at
)
SELECT s.cc_id, i.tenant_id, i.id, @cc_readonly_user_id, '抄送只读演示账号',
       i.business_type, i.business_id, i.title, 0, NOW()
FROM (
    SELECT 980000000000051001 AS cc_id, 978000000000001001 AS instance_id
    UNION ALL
    SELECT 980000000000051002, 978000000000001002
) s
JOIN wf_instance i
  ON i.tenant_id = 0
 AND i.id = s.instance_id
 AND i.deleted_flag = 0
WHERE @cc_readonly_user_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM wf_cc c
      WHERE c.id = s.cc_id
  );

SET FOREIGN_KEY_CHECKS = 1;
