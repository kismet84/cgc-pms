-- V118: add minimal alert category and business jump fields
ALTER TABLE alert_log ADD COLUMN alert_domain VARCHAR(50) NULL;
ALTER TABLE alert_log ADD COLUMN source_type VARCHAR(50) NULL;
ALTER TABLE alert_log ADD COLUMN source_id BIGINT NULL;

CREATE INDEX IF NOT EXISTS idx_alert_domain ON alert_log(alert_domain);
CREATE INDEX IF NOT EXISTS idx_alert_source ON alert_log(source_type, source_id);
CREATE INDEX IF NOT EXISTS idx_alert_list_filter ON alert_log(tenant_id, project_id, rule_type, alert_domain, is_read, triggered_at);
