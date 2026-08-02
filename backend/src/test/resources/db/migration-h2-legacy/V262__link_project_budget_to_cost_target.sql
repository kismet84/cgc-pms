-- Legacy H2 upgrade counterpart of V262__link_project_budget_to_cost_target.sql.
ALTER TABLE project_budget ADD COLUMN IF NOT EXISTS source_cost_target_id BIGINT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_project_budget_source_target
    ON project_budget (tenant_id, source_cost_target_id);

ALTER TABLE project_budget ADD CONSTRAINT IF NOT EXISTS fk_project_budget_source_target
    FOREIGN KEY (source_cost_target_id) REFERENCES cost_target (id);
