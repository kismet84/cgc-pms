ALTER TABLE cost_reversal_request
    DROP INDEX uk_cost_reversal_request_target,
    DROP CHECK ck_cost_reversal_status,
    ADD COLUMN active_target_guard TINYINT GENERATED ALWAYS AS
        (CASE WHEN status<>'CANCELLED' THEN 1 ELSE NULL END) STORED AFTER status,
    ADD UNIQUE KEY uk_cost_reversal_request_target
        (tenant_id,target_type,target_id,active_target_guard),
    ADD CONSTRAINT ck_cost_reversal_status
        CHECK (status IN ('DRAFT','SUBMITTED','REJECTED','WITHDRAWN','CANCELLED','POSTED'));
