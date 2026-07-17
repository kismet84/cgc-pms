ALTER TABLE alert_log ADD COLUMN read_by BIGINT NULL;
ALTER TABLE alert_log ADD COLUMN read_at TIMESTAMP NULL;
ALTER TABLE alert_log ADD COLUMN acknowledged_by BIGINT NULL;
ALTER TABLE alert_log ADD COLUMN acknowledged_at TIMESTAMP NULL;
ALTER TABLE alert_log ADD COLUMN response_due_at TIMESTAMP NULL;
ALTER TABLE alert_log ADD COLUMN resolution_due_at TIMESTAMP NULL;
ALTER TABLE alert_log ADD COLUMN escalation_level INT DEFAULT 0 NOT NULL;
ALTER TABLE alert_log ADD COLUMN last_escalated_at TIMESTAMP NULL;
ALTER TABLE alert_log ADD COLUMN processed_by BIGINT NULL;
ALTER TABLE alert_log ADD COLUMN archived_by BIGINT NULL;
ALTER TABLE alert_log ADD COLUMN version INT DEFAULT 0 NOT NULL;

UPDATE alert_log
SET read_by = CASE WHEN is_read = 1 THEN COALESCE(updated_by, created_by) ELSE NULL END,
    read_at = CASE WHEN is_read = 1 THEN COALESCE(updated_at, created_at, triggered_at) ELSE NULL END,
    acknowledged_by = CASE WHEN process_status IN ('PROCESSED','ARCHIVED') THEN COALESCE(updated_by, created_by) ELSE NULL END,
    acknowledged_at = CASE WHEN process_status IN ('PROCESSED','ARCHIVED') THEN COALESCE(processed_at, updated_at, triggered_at) ELSE NULL END,
    processed_by = CASE WHEN process_status IN ('PROCESSED','ARCHIVED') THEN COALESCE(updated_by, created_by) ELSE NULL END,
    archived_by = CASE WHEN process_status IN ('ARCHIVED','INVALID') THEN COALESCE(updated_by, created_by) ELSE NULL END,
    response_due_at = DATEADD('HOUR', CASE severity WHEN 'HIGH' THEN 4 WHEN 'LOW' THEN 72 ELSE 24 END,
        COALESCE(triggered_at, created_at)),
    resolution_due_at = DATEADD('HOUR', CASE severity WHEN 'HIGH' THEN 24 WHEN 'LOW' THEN 168 ELSE 72 END,
        COALESCE(triggered_at, created_at)),
    escalation_level = 0
WHERE deleted_flag = 0;

CREATE INDEX IF NOT EXISTS idx_alert_ack_due ON alert_log(tenant_id, process_status, acknowledged_at, response_due_at);
CREATE INDEX IF NOT EXISTS idx_alert_escalation_due ON alert_log(tenant_id, process_status, escalation_level, resolution_due_at);

CREATE TABLE alert_lifecycle_event (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    alert_id BIGINT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    from_status VARCHAR(20) NULL,
    to_status VARCHAR(20) NULL,
    operator_id BIGINT NULL,
    remark VARCHAR(500) NULL,
    occurred_at TIMESTAMP NOT NULL,
    payload_json CLOB NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_alert_lifecycle_alert FOREIGN KEY (alert_id) REFERENCES alert_log(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_alert_lifecycle_trace ON alert_lifecycle_event(tenant_id, alert_id, occurred_at, id);
