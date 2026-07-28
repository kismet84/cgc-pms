UPDATE wf_template_node
SET approver_config = '{"type":"ROLE","roleCode":"PROJECT_MANAGER"}'
WHERE id IN (53301, 53401);

UPDATE wf_template_node
SET approver_config = '{"type":"ROLE","roleCode":"PRODUCTION_MANAGER"}'
WHERE id IN (53302, 53402);

UPDATE wf_template_node
SET approver_config = '{"type":"ROLE","roleCode":"GENERAL_MANAGER"}'
WHERE id = 53303;

UPDATE wf_template_node
SET approver_config = '{"type":"ROLE","roleCode":"PURCHASE_MANAGER"}'
WHERE id = 50201;

UPDATE wf_template_node
SET approver_config = '{"type":"ROLE","roleCode":"FINANCE"}'
WHERE id = 50202;

UPDATE wf_template_node
SET approver_config = '{"type":"ROLE","roleCode":"GENERAL_MANAGER"}'
WHERE id = 50203;

UPDATE wf_template_node
SET approver_config = '{"type":"ROLE","roleCode":"PRODUCTION_MANAGER"}'
WHERE id = 50301;

UPDATE wf_template_node
SET approver_config = '{"type":"ROLE","roleCode":"COMMERCIAL_MANAGER"}'
WHERE id = 50302;

UPDATE wf_template_node
SET approver_config = '{"type":"ROLE","roleCode":"COST_MANAGER"}'
WHERE id = 50303;

UPDATE wf_template_node
SET deleted_flag = 1
WHERE id IN (51001, 51002, 51003)
  AND approver_config LIKE '%"type": "USER"%';
