-- 第55条资金支出闭环：合同审批移除失效固定用户，改用真实角色路由。

UPDATE wf_template_node
SET approver_config = '{"type":"ROLE","roleCode":"PROJECT_MANAGER"}',
    approve_mode = 'OR_SIGN',
    updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = 0 AND id = 50101 AND deleted_flag = 0;

UPDATE wf_template_node
SET approver_config = '{"type":"ROLE","roleCode":"DEPARTMENT_MANAGER"}',
    approve_mode = 'OR_SIGN',
    updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = 0 AND id = 50102 AND deleted_flag = 0;

UPDATE wf_template_node
SET approver_config = '{"type":"ROLE","roleCode":"GENERAL_MANAGER"}',
    approve_mode = 'OR_SIGN',
    updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = 0 AND id = 50103 AND deleted_flag = 0;
