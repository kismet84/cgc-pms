UPDATE wf_template_node
SET approver_config = '{"type":"ROLE","roleCode":"MANAGEMENT_EXECUTIVE"}'
WHERE id = 50102
  AND deleted_flag = 0;
