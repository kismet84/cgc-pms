UPDATE wf_template_node
SET approve_mode = 'OR_SIGN',
    approver_config = '{"type":"ROLE","roleCode":"GENERAL_MANAGER"}'
WHERE id = 53001
  AND deleted_flag = 0;
