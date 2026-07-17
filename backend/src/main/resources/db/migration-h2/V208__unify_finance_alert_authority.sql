ALTER TABLE finance_alert ADD COLUMN alert_log_id BIGINT;
ALTER TABLE finance_alert ADD CONSTRAINT uk_finance_alert_log UNIQUE(alert_log_id);
ALTER TABLE finance_alert ADD CONSTRAINT fk_finance_alert_log FOREIGN KEY(alert_log_id) REFERENCES alert_log(id);
