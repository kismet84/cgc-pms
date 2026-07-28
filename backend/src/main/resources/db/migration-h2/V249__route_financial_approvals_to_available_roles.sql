UPDATE wf_template_node
SET approver_config = '{"type":"PROJECT_ROLE","roleCode":"PM"}',
    approve_mode = 'OR_SIGN'
WHERE id IN (50401, 50501, 50801)
  AND deleted_flag = 0;

UPDATE wf_template_node
SET approver_config = '{"type":"ROLE","roleCode":"MANAGEMENT_EXECUTIVE"}',
    approve_mode = 'OR_SIGN'
WHERE id IN (50402, 50502, 50802)
  AND deleted_flag = 0;

UPDATE wf_template_node
SET approver_config = '{"type":"ROLE","roleCode":"GENERAL_MANAGER"}',
    approve_mode = 'OR_SIGN'
WHERE id IN (50403, 50503, 50803)
  AND deleted_flag = 0;
