-- V43: Add contract_id to alert_log for structured contract-level alert tracking
ALTER TABLE alert_log ADD COLUMN contract_id BIGINT NULL COMMENT '合同ID' AFTER project_id;
ALTER TABLE alert_log ADD INDEX idx_alert_contract (contract_id);
