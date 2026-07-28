UPDATE wf_template_node
SET approver_config = '{"type":"PROJECT_ROLE","roleCode":"PM"}',
    updated_at = CURRENT_TIMESTAMP
WHERE template_id = (
    SELECT id FROM wf_template WHERE template_code = 'TPL-TECHNICAL-SCHEME-001'
)
  AND node_code = 'N1';

UPDATE wf_template_node
SET approver_config = '{"type":"ROLE","roleCode":"CHIEF_ENGINEER"}',
    updated_at = CURRENT_TIMESTAMP
WHERE template_id = (
    SELECT id FROM wf_template WHERE template_code = 'TPL-TECHNICAL-SCHEME-001'
)
  AND node_code = 'N2';
