UPDATE wf_template_node n
JOIN wf_template t ON t.id = n.template_id
SET n.approver_config = '{"type":"PROJECT_ROLE","roleCode":"PM"}',
    n.updated_at = CURRENT_TIMESTAMP
WHERE t.template_code = 'TPL-TECHNICAL-SCHEME-001'
  AND n.node_code = 'N1';

UPDATE wf_template_node n
JOIN wf_template t ON t.id = n.template_id
SET n.approver_config = '{"type":"ROLE","roleCode":"CHIEF_ENGINEER"}',
    n.updated_at = CURRENT_TIMESTAMP
WHERE t.template_code = 'TPL-TECHNICAL-SCHEME-001'
  AND n.node_code = 'N2';
