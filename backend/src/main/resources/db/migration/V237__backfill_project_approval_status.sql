UPDATE pm_project
SET approval_status = 'DRAFT'
WHERE approval_status IS NULL
  AND deleted_flag = 0;
