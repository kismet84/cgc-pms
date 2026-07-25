ALTER TABLE project_budget
    ADD COLUMN budget_code varchar(50) NULL COMMENT '项目预算业务编号' AFTER project_id;

UPDATE project_budget b
JOIN (
    SELECT id,
           CONCAT(
               'BUD-',
               DATE_FORMAT(created_at, '%Y%m%d'),
               '-',
               LPAD(ROW_NUMBER() OVER (
                   PARTITION BY tenant_id, DATE(created_at)
                   ORDER BY created_at, id
               ), 3, '0')
           ) AS generated_code
    FROM project_budget
) numbered ON numbered.id = b.id
SET b.budget_code = numbered.generated_code;

ALTER TABLE project_budget
    MODIFY COLUMN budget_code varchar(50) NOT NULL COMMENT '项目预算业务编号',
    ADD UNIQUE KEY uk_project_budget_code (tenant_id, budget_code);
