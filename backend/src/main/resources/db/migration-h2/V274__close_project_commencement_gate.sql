-- H2 counterpart of V274__close_project_commencement_gate.sql.

ALTER TABLE pm_project ADD COLUMN IF NOT EXISTS owner_contract_id BIGINT NULL;
CREATE INDEX IF NOT EXISTS idx_pm_project_owner_contract ON pm_project (tenant_id, owner_contract_id);
ALTER TABLE pm_project ADD CONSTRAINT IF NOT EXISTS fk_pm_project_owner_contract
    FOREIGN KEY (tenant_id, owner_contract_id) REFERENCES ct_contract (tenant_id, id);

ALTER TABLE pm_project DROP CONSTRAINT IF EXISTS ck_pm_project_initiation_basis;
UPDATE pm_project SET initiation_basis='LEGACY_UNCLASSIFIED' WHERE initiation_basis IS NULL;
ALTER TABLE pm_project ALTER COLUMN initiation_basis DROP DEFAULT;
ALTER TABLE pm_project ALTER COLUMN initiation_basis DROP NOT NULL;
ALTER TABLE pm_project ADD CONSTRAINT IF NOT EXISTS ck_pm_project_initiation_basis
    CHECK (initiation_basis IN ('BID_AWARD', 'DIRECT_APPROVAL', 'LEGACY_UNCLASSIFIED'));

ALTER TABLE cost_target ADD COLUMN IF NOT EXISTS source_contract_id BIGINT NULL;
CREATE INDEX IF NOT EXISTS idx_cost_target_source_contract ON cost_target (tenant_id, source_contract_id);
ALTER TABLE cost_target ADD CONSTRAINT IF NOT EXISTS fk_cost_target_source_contract
    FOREIGN KEY (tenant_id, source_contract_id) REFERENCES ct_contract (tenant_id, id);

CREATE TABLE IF NOT EXISTS project_commencement (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL,
    planned_start_date DATE NOT NULL,
    actual_start_date DATE NULL,
    basis_type VARCHAR(32) NOT NULL,
    approval_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    approval_instance_id BIGINT NULL,
    version INT NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    CONSTRAINT uk_project_commencement_project UNIQUE (tenant_id, project_id),
    CONSTRAINT uk_project_commencement_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_project_commencement_project FOREIGN KEY (tenant_id, project_id)
        REFERENCES pm_project (tenant_id, id),
    CONSTRAINT fk_project_commencement_instance FOREIGN KEY (approval_instance_id)
        REFERENCES wf_instance (id),
    CONSTRAINT ck_project_commencement_status CHECK (
        approval_status IN ('DRAFT', 'APPROVING', 'APPROVED', 'REJECTED')
    )
);
CREATE INDEX IF NOT EXISTS idx_project_commencement_status
    ON project_commencement (tenant_id, approval_status, deleted_flag);
CREATE INDEX IF NOT EXISTS idx_project_commencement_instance
    ON project_commencement (approval_instance_id);

INSERT INTO sys_type_registry
    (id, type_domain, type_code, owner_module, contract_version, status, description, created_at, updated_at)
SELECT 2740001, 'WORKFLOW_BUSINESS_TYPE', 'PROJECT_COMMENCEMENT', 'project', '1.0',
       'ACTIVE', '项目开工准入审批', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM sys_type_registry
    WHERE type_domain = 'WORKFLOW_BUSINESS_TYPE' AND type_code = 'PROJECT_COMMENCEMENT'
);

INSERT INTO wf_template
    (id, tenant_id, template_code, template_name, business_type, enabled,
     amount_min, amount_max, condition_rule, form_schema, created_by, created_at,
     updated_by, updated_at, deleted_flag, remark)
SELECT 50069, 0, 'TPL-PROJECT-COMMENCEMENT-001', '项目开工准入审批',
       'PROJECT_COMMENCEMENT', 1, NULL, NULL, NULL, NULL, 1, CURRENT_TIMESTAMP,
       1, CURRENT_TIMESTAMP, 0, '开工准入由部门经理与总经理复核，批准后集中启用项目'
WHERE NOT EXISTS (
    SELECT 1 FROM wf_template
    WHERE tenant_id = 0 AND template_code = 'TPL-PROJECT-COMMENCEMENT-001' AND deleted_flag = 0
);

INSERT INTO wf_template_node
    (id, tenant_id, template_id, node_code, node_name, node_order, node_type,
     approve_mode, approver_config, pass_rule_json, reject_rule_json, condition_rule,
     node_config, allow_transfer, allow_add_sign, timeout_hours, created_by, created_at,
     updated_by, updated_at, deleted_flag, remark)
SELECT 56901, 0, 50069, 'DEPARTMENT_MANAGER', '部门经理复核', 1, 'APPROVAL',
       'SEQUENTIAL', '{"type":"ROLE","roleCode":"DEPARTMENT_MANAGER"}', NULL, NULL, NULL,
       NULL, 1, 1, 48, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 0, NULL
WHERE NOT EXISTS (SELECT 1 FROM wf_template_node WHERE template_id = 50069 AND node_code = 'DEPARTMENT_MANAGER');

INSERT INTO wf_template_node
    (id, tenant_id, template_id, node_code, node_name, node_order, node_type,
     approve_mode, approver_config, pass_rule_json, reject_rule_json, condition_rule,
     node_config, allow_transfer, allow_add_sign, timeout_hours, created_by, created_at,
     updated_by, updated_at, deleted_flag, remark)
SELECT 56902, 0, 50069, 'GENERAL_MANAGER', '总经理确认', 2, 'APPROVAL',
       'SEQUENTIAL', '{"type":"ROLE","roleCode":"GENERAL_MANAGER"}', NULL, NULL, NULL,
       NULL, 1, 1, 72, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 0, NULL
WHERE NOT EXISTS (SELECT 1 FROM wf_template_node WHERE template_id = 50069 AND node_code = 'GENERAL_MANAGER');

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon,
     order_num, status, visible, created_by, updated_by, remark, created_at, updated_at, deleted_flag)
SELECT seed.id, 0, 2, seed.menu_name, 'BUTTON', NULL, NULL, seed.perms, NULL,
       seed.order_num, 'ENABLE', 0, 1, 1, 'MAINLINE-69', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM (
    SELECT 27401 AS id, '开工准入查询' AS menu_name, 'project:commencement:query' AS perms, 69 AS order_num
    UNION ALL SELECT 27402, '开工准入新建', 'project:commencement:add', 70
    UNION ALL SELECT 27403, '开工准入编辑', 'project:commencement:edit', 71
    UNION ALL SELECT 27404, '开工准入提交', 'project:commencement:submit', 72
) seed
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu m WHERE m.tenant_id = 0 AND m.perms = seed.perms AND m.deleted_flag = 0
);

INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT 274000100000000 + ROW_NUMBER() OVER (ORDER BY r.id, m.id), r.tenant_id, r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.tenant_id = r.tenant_id
    AND m.perms IN ('project:commencement:query', 'project:commencement:add',
                    'project:commencement:edit', 'project:commencement:submit')
    AND m.deleted_flag = 0
WHERE r.tenant_id = 0 AND r.role_code = 'PROJECT_MANAGER' AND r.deleted_flag = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm
      WHERE rm.tenant_id = r.tenant_id AND rm.role_id = r.id AND rm.menu_id = m.id
  );
