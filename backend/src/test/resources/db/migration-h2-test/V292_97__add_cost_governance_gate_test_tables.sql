CREATE TABLE IF NOT EXISTS cost_project_config_request (
  id BIGINT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  status VARCHAR(24) NOT NULL
);

CREATE TABLE IF NOT EXISTS bid_cost_target_transfer_request (
  id BIGINT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  status VARCHAR(24) NOT NULL,
  deleted_flag TINYINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS finance_cost_allocation_request (
  id BIGINT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  status VARCHAR(24) NOT NULL,
  deleted_flag TINYINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS finance_cost_allocation_request_line (
  id BIGINT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  request_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS cost_reversal_request (
  id BIGINT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  request_code VARCHAR(64) NOT NULL,
  target_type VARCHAR(32) NOT NULL,
  target_id BIGINT NOT NULL,
  project_id BIGINT,
  status VARCHAR(24) NOT NULL,
  version INT NOT NULL DEFAULT 0,
  created_by BIGINT NOT NULL,
  reason VARCHAR(500) NOT NULL
);

ALTER TABLE bid_cost_target_transfer_request ADD COLUMN IF NOT EXISTS request_code VARCHAR(64);
ALTER TABLE finance_cost_allocation_request ADD COLUMN IF NOT EXISTS request_code VARCHAR(64);
ALTER TABLE cost_project_config_request ADD COLUMN IF NOT EXISTS request_code VARCHAR(64);
ALTER TABLE cost_recalculation_batch ADD COLUMN IF NOT EXISTS batch_code VARCHAR(64);
