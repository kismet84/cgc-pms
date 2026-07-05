-- V126: repair SUB_MEASURE approval template approver resolution.
-- Avoid fixed USER userId=1 drift; resolve the current project's tenant-local PROJECT_MANAGER instead.

UPDATE wf_template_node
SET approver_config = JSON_OBJECT('type', 'PROJECT_ROLE', 'roleCode', 'PROJECT_MANAGER')
WHERE deleted_flag = 0
  AND template_id IN (
      SELECT id
      FROM wf_template
      WHERE business_type = 'SUB_MEASURE'
        AND deleted_flag = 0
  );
