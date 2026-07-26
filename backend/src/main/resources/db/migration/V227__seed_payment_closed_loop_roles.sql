-- 第55条资金支出闭环：补齐真实审批角色、必要权限和角色路由。

INSERT INTO sys_role
    (id, tenant_id, role_code, role_name, role_type, status, data_scope,
     created_by, created_at, updated_by, updated_at, deleted_flag, remark, role_level)
SELECT 22700001, 0, 'COST_MANAGER', '成本经理', 'BUSINESS', 'ENABLE', 'ALL',
       1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 0, '项目资金支出闭环审批角色', 2
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role WHERE tenant_id = 0 AND role_code = 'COST_MANAGER' AND deleted_flag = 0
);

INSERT INTO sys_role
    (id, tenant_id, role_code, role_name, role_type, status, data_scope,
     created_by, created_at, updated_by, updated_at, deleted_flag, remark, role_level)
SELECT 22700002, 0, 'DEPARTMENT_MANAGER', '部门经理', 'BUSINESS', 'ENABLE', 'DEPT_AND_CHILD',
       1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 0, '项目资金支出闭环一级审批角色', 2
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role WHERE tenant_id = 0 AND role_code = 'DEPARTMENT_MANAGER' AND deleted_flag = 0
);

INSERT INTO sys_role
    (id, tenant_id, role_code, role_name, role_type, status, data_scope,
     created_by, created_at, updated_by, updated_at, deleted_flag, remark, role_level)
SELECT 22700003, 0, 'GENERAL_MANAGER', '总经理', 'BUSINESS', 'ENABLE', 'ALL',
       1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 0, '项目资金支出闭环二级审批角色', 1
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role WHERE tenant_id = 0 AND role_code = 'GENERAL_MANAGER' AND deleted_flag = 0
);

INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT 227000100000000 + ROW_NUMBER() OVER (ORDER BY r.id, m.id), r.tenant_id, r.id, m.id
FROM sys_role r
JOIN sys_menu m
  ON m.tenant_id = r.tenant_id
 AND m.id IN (613, 614, 908, 946, 947, 948)
 AND m.deleted_flag = 0
WHERE r.tenant_id = 0
  AND r.role_code IN ('PROJECT_MANAGER', 'COST_MANAGER', 'DEPARTMENT_MANAGER', 'GENERAL_MANAGER', 'FINANCE')
  AND r.deleted_flag = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm
      WHERE rm.tenant_id = r.tenant_id AND rm.role_id = r.id AND rm.menu_id = m.id
  );

INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT 227000200000000 + ROW_NUMBER() OVER (ORDER BY r.id, m.id), r.tenant_id, r.id, m.id
FROM sys_role r
JOIN sys_menu m
  ON m.tenant_id = r.tenant_id
 AND m.id IN (617, 618, 1020, 1021, 1022, 1023, 1024,
              1030, 1031, 1032, 1033, 1034, 1040, 809)
 AND m.deleted_flag = 0
WHERE r.tenant_id = 0
  AND r.role_code = 'COST_MANAGER'
  AND r.deleted_flag = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm
      WHERE rm.tenant_id = r.tenant_id AND rm.role_id = r.id AND rm.menu_id = m.id
  );

INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT 227000300000000 + ROW_NUMBER() OVER (ORDER BY r.id, m.id), r.tenant_id, r.id, m.id
FROM sys_role r
JOIN sys_menu m
  ON m.tenant_id = r.tenant_id
 AND m.id = 811
 AND m.deleted_flag = 0
WHERE r.tenant_id = 0
  AND r.role_code = 'GENERAL_MANAGER'
  AND r.deleted_flag = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm
      WHERE rm.tenant_id = r.tenant_id AND rm.role_id = r.id AND rm.menu_id = m.id
  );

UPDATE wf_template_node
SET approver_config = '{"type":"ROLE","roleCode":"PROJECT_MANAGER"}',
    updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = 0 AND id IN (50501, 52001) AND deleted_flag = 0;

UPDATE wf_template_node
SET approver_config = '{"type":"ROLE","roleCode":"DEPARTMENT_MANAGER"}',
    updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = 0 AND id = 50502 AND deleted_flag = 0;

UPDATE wf_template_node
SET approver_config = '{"type":"ROLE","roleCode":"GENERAL_MANAGER"}',
    updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = 0 AND id IN (50503, 52003) AND deleted_flag = 0;

UPDATE wf_template_node
SET approver_config = '{"type":"ROLE","roleCode":"COST_MANAGER"}',
    updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = 0 AND id IN (52002, 52102) AND deleted_flag = 0;

UPDATE wf_template_node
SET approver_config = '{"type":"ROLE","roleCode":"FINANCE"}',
    updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = 0 AND id = 52103 AND deleted_flag = 0;

UPDATE wf_template_node
SET approver_config = '{"type":"ROLE","roleCode":"PROJECT_MANAGER"}',
    updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = 0 AND id = 52101 AND deleted_flag = 0;
