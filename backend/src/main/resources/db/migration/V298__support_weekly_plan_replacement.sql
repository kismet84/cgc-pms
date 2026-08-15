ALTER TABLE project_period_plan
  ADD COLUMN replaces_period_plan_id BIGINT NULL AFTER parent_period_plan_id,
  ADD INDEX idx_project_period_replacement (tenant_id, replaces_period_plan_id),
  ADD CONSTRAINT fk_project_period_replacement
    FOREIGN KEY (replaces_period_plan_id) REFERENCES project_period_plan (id) ON DELETE RESTRICT;

ALTER TABLE project_period_plan DROP CHECK ck_project_period_status;

ALTER TABLE project_period_plan
  ADD CONSTRAINT ck_project_period_status
    CHECK (status IN ('DRAFT','PENDING','APPROVED','REJECTED','CANCELLED','SUPERSEDED'));
