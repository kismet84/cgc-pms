-- Unified project cost budget: cost target is the approved planning fact,
-- project budget remains the stable execution/occupancy ledger.
ALTER TABLE project_budget
    ADD COLUMN source_cost_target_id BIGINT NULL COMMENT '最新审批生效的目标成本版本ID' AFTER project_id;

CREATE UNIQUE INDEX uk_project_budget_source_target
    ON project_budget (tenant_id, source_cost_target_id);

ALTER TABLE project_budget
    ADD CONSTRAINT fk_project_budget_source_target
        FOREIGN KEY (source_cost_target_id) REFERENCES cost_target (id) ON DELETE RESTRICT;
