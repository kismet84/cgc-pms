-- H2 counterpart of V295__align_project_member_roles.sql.

UPDATE pm_project_member m
SET role_code = (
    SELECT MAX(r.role_code)
    FROM sys_user_role ur
    JOIN sys_user u
      ON u.tenant_id = ur.tenant_id
     AND u.id = ur.user_id
     AND u.deleted_flag = 0
     AND u.status = 'ENABLE'
    JOIN sys_role r
      ON r.tenant_id = ur.tenant_id
     AND r.id = ur.role_id
     AND r.deleted_flag = 0
     AND r.status = 'ENABLE'
     AND r.data_scope = 'PROJECT_MEMBER'
     AND r.role_code IN ('PROJECT_MANAGER','PROJECT_ACCOUNTANT','TECHNICAL_LEAD','SAFETY_LEAD',
                         'CONSTRUCTION_LEAD','PROCUREMENT_LEAD','EMPLOYEE')
    WHERE ur.tenant_id = m.tenant_id
      AND ur.user_id = m.user_id
)
WHERE UPPER(TRIM(m.role_code)) NOT IN (
    'PROJECT_MANAGER','PROJECT_ACCOUNTANT','TECHNICAL_LEAD','SAFETY_LEAD',
    'CONSTRUCTION_LEAD','PROCUREMENT_LEAD','EMPLOYEE'
)
AND 1 = (
    SELECT COUNT(DISTINCT r.role_code)
    FROM sys_user_role ur
    JOIN sys_user u
      ON u.tenant_id = ur.tenant_id
     AND u.id = ur.user_id
     AND u.deleted_flag = 0
     AND u.status = 'ENABLE'
    JOIN sys_role r
      ON r.tenant_id = ur.tenant_id
     AND r.id = ur.role_id
     AND r.deleted_flag = 0
     AND r.status = 'ENABLE'
     AND r.data_scope = 'PROJECT_MEMBER'
     AND r.role_code IN ('PROJECT_MANAGER','PROJECT_ACCOUNTANT','TECHNICAL_LEAD','SAFETY_LEAD',
                         'CONSTRUCTION_LEAD','PROCUREMENT_LEAD','EMPLOYEE')
    WHERE ur.tenant_id = m.tenant_id
      AND ur.user_id = m.user_id
);

UPDATE pm_project_member
SET role_code = CASE UPPER(TRIM(role_code))
    WHEN 'PM' THEN 'PROJECT_MANAGER'
    WHEN 'CM' THEN 'PROJECT_ACCOUNTANT'
    WHEN 'CSTM' THEN 'PROJECT_ACCOUNTANT'
    WHEN 'COST_MANAGER' THEN 'PROJECT_ACCOUNTANT'
    WHEN 'COMMERCIAL_MANAGER' THEN 'PROJECT_ACCOUNTANT'
    WHEN 'DEPARTMENT_MANAGER' THEN 'PROJECT_ACCOUNTANT'
    WHEN 'FIN' THEN 'PROJECT_ACCOUNTANT'
    WHEN 'FINANCE' THEN 'PROJECT_ACCOUNTANT'
    WHEN 'CHIEF_ENGINEER' THEN 'TECHNICAL_LEAD'
    WHEN 'PRODUCTION_MANAGER' THEN 'CONSTRUCTION_LEAD'
    WHEN 'PURCHASE_MANAGER' THEN 'PROCUREMENT_LEAD'
    WHEN 'MATERIAL_CLERK' THEN 'PROCUREMENT_LEAD'
    WHEN 'MAT' THEN 'PROCUREMENT_LEAD'
    WHEN 'COMMON_USER' THEN 'EMPLOYEE'
    WHEN 'PROJECT_MANAGER' THEN 'PROJECT_MANAGER'
    WHEN 'PROJECT_ACCOUNTANT' THEN 'PROJECT_ACCOUNTANT'
    WHEN 'TECHNICAL_LEAD' THEN 'TECHNICAL_LEAD'
    WHEN 'SAFETY_LEAD' THEN 'SAFETY_LEAD'
    WHEN 'CONSTRUCTION_LEAD' THEN 'CONSTRUCTION_LEAD'
    WHEN 'PROCUREMENT_LEAD' THEN 'PROCUREMENT_LEAD'
    WHEN 'EMPLOYEE' THEN 'EMPLOYEE'
    ELSE role_code
END;

CREATE LOCAL TEMPORARY TABLE m295_guard (
    invalid_count INT NOT NULL CHECK (invalid_count = 0)
);

INSERT INTO m295_guard
SELECT COUNT(*)
FROM pm_project_member
WHERE UPPER(TRIM(role_code)) NOT IN (
    'PROJECT_MANAGER','PROJECT_ACCOUNTANT','TECHNICAL_LEAD','SAFETY_LEAD',
    'CONSTRUCTION_LEAD','PROCUREMENT_LEAD','EMPLOYEE'
);

DROP TABLE m295_guard;

ALTER TABLE pm_project_member DROP CONSTRAINT IF EXISTS ck_pm_project_member_role_code;
ALTER TABLE pm_project_member ADD CONSTRAINT ck_pm_project_member_role_code CHECK (
    role_code IN ('PROJECT_MANAGER','PROJECT_ACCOUNTANT','TECHNICAL_LEAD','SAFETY_LEAD',
                  'CONSTRUCTION_LEAD','PROCUREMENT_LEAD','EMPLOYEE')
);
