-- H2 counterpart of V296__validate_active_project_member_role_assignments.sql.

-- V85 removes the default admin, while V90 later inserted an integration-only
-- active member for that deleted user. Preserve the row as inactive history.
UPDATE pm_project_member m
SET status = 'INACTIVE',
    end_date = COALESCE(end_date, CURRENT_DATE),
    updated_at = CURRENT_TIMESTAMP
WHERE m.id = 40001
  AND m.tenant_id = 0
  AND m.project_id = 10001
  AND m.user_id = 1
  AND m.role_code = 'PROJECT_MANAGER'
  AND m.deleted_flag = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_user u
      WHERE u.tenant_id = m.tenant_id
        AND u.id = m.user_id
        AND u.deleted_flag = 0
  );

CREATE LOCAL TEMPORARY TABLE m296_guard (
    invalid_count INT NOT NULL CHECK (invalid_count = 0)
);

INSERT INTO m296_guard
SELECT COUNT(*)
FROM pm_project_member m
WHERE m.deleted_flag = 0
  AND m.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1
      FROM sys_user u
      JOIN sys_user_role ur
        ON ur.tenant_id = u.tenant_id
       AND ur.user_id = u.id
      JOIN sys_role r
        ON r.tenant_id = ur.tenant_id
       AND r.id = ur.role_id
      WHERE u.tenant_id = m.tenant_id
        AND u.id = m.user_id
        AND u.status = 'ENABLE'
        AND u.deleted_flag = 0
        AND r.role_code = m.role_code
        AND r.data_scope = 'PROJECT_MEMBER'
        AND r.status = 'ENABLE'
        AND r.deleted_flag = 0
  );

DROP TABLE m296_guard;
