-- V26__add_cost_target_id_to_cost_summary.sql
-- H2-compatible version

ALTER TABLE cost_summary ADD COLUMN IF NOT EXISTS cost_target_id BIGINT NULL;
