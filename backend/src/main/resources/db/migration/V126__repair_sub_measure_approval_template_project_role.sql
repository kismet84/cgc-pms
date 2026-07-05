-- V126: repair SUB_MEASURE approval template approver resolution.
-- Avoid fixed USER userId=1 drift; resolve the current project's tenant-local PROJECT_MANAGER instead.

SET NAMES utf8mb4;

UPDATE wf_template_node n
JOIN wf_template t ON t.id = n.template_id
SET n.approver_config = JSON_OBJECT('type', 'PROJECT_ROLE', 'roleCode', 'PROJECT_MANAGER')
WHERE t.business_type = 'SUB_MEASURE'
  AND t.deleted_flag = 0
  AND n.deleted_flag = 0;
