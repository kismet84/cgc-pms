CREATE TABLE IF NOT EXISTS alert_notification_send_record (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    alert_id BIGINT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    channel VARCHAR(50) NOT NULL,
    target_user_id BIGINT NULL,
    biz_notification_id BIGINT NULL,
    send_status VARCHAR(50) NOT NULL,
    fail_reason VARCHAR(500) NULL,
    requested_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP NULL,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_ansr_alert_event
    ON alert_notification_send_record (tenant_id, alert_id, event_type);

CREATE INDEX IF NOT EXISTS idx_ansr_channel_status
    ON alert_notification_send_record (tenant_id, channel, send_status);
