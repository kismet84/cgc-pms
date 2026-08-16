ALTER TABLE cost_reversal_request DROP CONSTRAINT IF EXISTS uk_cost_reversal_request_target;
ALTER TABLE cost_reversal_request DROP CONSTRAINT IF EXISTS ck_cost_reversal_status;
ALTER TABLE cost_reversal_request ADD COLUMN IF NOT EXISTS active_target_guard TINYINT
    GENERATED ALWAYS AS (CASE WHEN status<>'CANCELLED' THEN 1 ELSE NULL END);
ALTER TABLE cost_reversal_request ADD CONSTRAINT uk_cost_reversal_request_target
    UNIQUE(tenant_id,target_type,target_id,active_target_guard);
ALTER TABLE cost_reversal_request ADD CONSTRAINT ck_cost_reversal_status
    CHECK(status IN('DRAFT','SUBMITTED','REJECTED','WITHDRAWN','CANCELLED','POSTED'));
