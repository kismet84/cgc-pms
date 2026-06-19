ALTER TABLE cost_subject ADD COLUMN IF NOT EXISTS account_category VARCHAR(20) DEFAULT 'COST' NOT NULL;
DROP INDEX IF EXISTS uk_cost_subject_code;
CREATE UNIQUE INDEX uk_cost_subject_code ON cost_subject (tenant_id, subject_code, account_category, deleted_flag);
