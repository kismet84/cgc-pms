-- Fail closed unless every active project member has the same enabled,
-- project-scoped system role in the same tenant. V295 is immutable because it
-- has already been applied; this forward guard closes its legacy-alias gap.

CREATE TEMPORARY TABLE m296_guard (
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

DROP TEMPORARY TABLE m296_guard;
