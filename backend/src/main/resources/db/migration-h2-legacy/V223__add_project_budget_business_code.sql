ALTER TABLE project_budget ADD COLUMN budget_code VARCHAR(50) NULL;

UPDATE project_budget t
SET budget_code = CONCAT(
    'BUD-',
    FORMATDATETIME(created_at, 'yyyyMMdd'),
    '-',
    LPAD(CAST((
        SELECT COUNT(*) + 1
        FROM project_budget s
        WHERE s.tenant_id = t.tenant_id
          AND CAST(s.created_at AS DATE) = CAST(t.created_at AS DATE)
          AND s.id < t.id
    ) AS VARCHAR), 3, '0')
);

ALTER TABLE project_budget ALTER COLUMN budget_code SET NOT NULL;
CREATE UNIQUE INDEX uk_project_budget_code ON project_budget (tenant_id, budget_code);
