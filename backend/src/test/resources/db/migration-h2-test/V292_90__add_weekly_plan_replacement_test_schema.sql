-- Frozen legacy Spring-test fixture shim for production V298.
ALTER TABLE project_period_plan ADD COLUMN replaces_period_plan_id BIGINT NULL;
ALTER TABLE project_period_plan ADD CONSTRAINT fk_project_period_replacement
  FOREIGN KEY (replaces_period_plan_id) REFERENCES project_period_plan (id) ON DELETE RESTRICT;
ALTER TABLE project_period_plan DROP CONSTRAINT IF EXISTS ck_project_period_status;
ALTER TABLE project_period_plan ADD CONSTRAINT ck_project_period_status
  CHECK (status IN ('DRAFT','PENDING','APPROVED','REJECTED','CANCELLED','SUPERSEDED'));
