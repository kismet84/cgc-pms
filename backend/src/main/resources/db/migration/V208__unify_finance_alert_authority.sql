SET NAMES utf8mb4;

ALTER TABLE finance_alert
    ADD COLUMN alert_log_id BIGINT NULL COMMENT '驾驶舱权威预警事实ID' AFTER alert_key,
    ADD UNIQUE KEY uk_finance_alert_log(alert_log_id),
    ADD CONSTRAINT fk_finance_alert_log FOREIGN KEY(alert_log_id) REFERENCES alert_log(id) ON DELETE RESTRICT;

-- finance_alert remains the operational handling queue; alert_log is the dashboard/reporting authority.
-- Existing rows are intentionally not guessed into projects. Deployment precheck must report unlinked history.
