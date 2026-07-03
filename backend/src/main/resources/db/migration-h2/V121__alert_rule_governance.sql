-- V121: alert-rule governance baseline for M2 (H2)

ALTER TABLE alert_log ADD COLUMN alert_category VARCHAR(50) NULL;
ALTER TABLE alert_log ADD COLUMN dedup_key VARCHAR(200) NULL;
ALTER TABLE alert_log ADD COLUMN process_status VARCHAR(20) DEFAULT 'OPEN' NOT NULL;
ALTER TABLE alert_log ADD COLUMN processed_at TIMESTAMP NULL;
ALTER TABLE alert_log ADD COLUMN archived_at TIMESTAMP NULL;
ALTER TABLE alert_log ADD COLUMN status_remark VARCHAR(500) NULL;

CREATE INDEX IF NOT EXISTS idx_alert_process_status ON alert_log(process_status);
CREATE INDEX IF NOT EXISTS idx_alert_dedup_window ON alert_log(tenant_id, dedup_key, process_status, triggered_at);

UPDATE alert_log
SET alert_category = CASE rule_type
        WHEN 'DYNAMIC_COST_EXCEEDS_TARGET' THEN 'COST_DYNAMIC'
        WHEN 'MATERIAL_EXCEEDS_BUDGET' THEN 'COST_MATERIAL'
        WHEN 'SUBCONTRACT_EXCEEDS_CONTRACT' THEN 'COST_SUBCONTRACT'
        WHEN 'CONTRACT_OVERDUE' THEN 'CONTRACT_TERM'
        WHEN 'CONTRACT_EXPIRING' THEN 'CONTRACT_TERM'
        WHEN 'WARRANTY_EARLY_RELEASE' THEN 'CONTRACT_WARRANTY'
        WHEN 'PAYMENT_EXCEEDS_RATIO' THEN 'PAYMENT_RATIO'
        WHEN 'VARIATION_UNCONFIRMED' THEN 'VARIATION_CONFIRM'
        WHEN 'PURCHASE_DELIVERY_OVERDUE' THEN 'PURCHASE_DELIVERY'
        ELSE 'OTHER'
    END,
    process_status = COALESCE(NULLIF(process_status, ''), 'OPEN'),
    dedup_key = CASE
        WHEN source_type IS NOT NULL AND source_type <> '' AND source_id IS NOT NULL
            THEN 'S:' || source_type || ':' || source_id || ':R:' || rule_type
        WHEN contract_id IS NOT NULL
            THEN 'C:' || contract_id || ':R:' || rule_type
        ELSE 'P:' || project_id || ':R:' || rule_type
    END
WHERE alert_category IS NULL
   OR alert_category = ''
   OR process_status IS NULL
   OR process_status = ''
   OR dedup_key IS NULL
   OR dedup_key = '';

CREATE TABLE IF NOT EXISTS alert_rule_config (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    rule_type VARCHAR(100) NOT NULL,
    alert_domain VARCHAR(50) NOT NULL,
    alert_category VARCHAR(50) NOT NULL,
    enabled SMALLINT NOT NULL DEFAULT 1,
    dedup_hours INT NOT NULL DEFAULT 24,
    window_days INT NULL,
    threshold_ratio DECIMAL(10,4) NULL,
    severity_override VARCHAR(20) NULL,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_alert_rule_config ON alert_rule_config(tenant_id, rule_type);
CREATE INDEX IF NOT EXISTS idx_alert_rule_config_enabled ON alert_rule_config(tenant_id, enabled, deleted_flag);

INSERT INTO alert_rule_config
    (id, tenant_id, rule_type, alert_domain, alert_category, enabled, dedup_hours, window_days, threshold_ratio, severity_override, created_by, remark)
SELECT 121001, 0, 'DYNAMIC_COST_EXCEEDS_TARGET', 'COST', 'COST_DYNAMIC', 1, 24, NULL, 1.0000, NULL, 1, 'M2规则治理默认配置'
WHERE NOT EXISTS (SELECT 1 FROM alert_rule_config WHERE tenant_id = 0 AND rule_type = 'DYNAMIC_COST_EXCEEDS_TARGET');
INSERT INTO alert_rule_config
    (id, tenant_id, rule_type, alert_domain, alert_category, enabled, dedup_hours, window_days, threshold_ratio, severity_override, created_by, remark)
SELECT 121002, 0, 'MATERIAL_EXCEEDS_BUDGET', 'COST', 'COST_MATERIAL', 1, 24, NULL, 1.0000, NULL, 1, 'M2规则治理默认配置'
WHERE NOT EXISTS (SELECT 1 FROM alert_rule_config WHERE tenant_id = 0 AND rule_type = 'MATERIAL_EXCEEDS_BUDGET');
INSERT INTO alert_rule_config
    (id, tenant_id, rule_type, alert_domain, alert_category, enabled, dedup_hours, window_days, threshold_ratio, severity_override, created_by, remark)
SELECT 121003, 0, 'SUBCONTRACT_EXCEEDS_CONTRACT', 'COST', 'COST_SUBCONTRACT', 1, 24, NULL, 1.0000, NULL, 1, 'M2规则治理默认配置'
WHERE NOT EXISTS (SELECT 1 FROM alert_rule_config WHERE tenant_id = 0 AND rule_type = 'SUBCONTRACT_EXCEEDS_CONTRACT');
INSERT INTO alert_rule_config
    (id, tenant_id, rule_type, alert_domain, alert_category, enabled, dedup_hours, window_days, threshold_ratio, severity_override, created_by, remark)
SELECT 121004, 0, 'CONTRACT_OVERDUE', 'CONTRACT', 'CONTRACT_TERM', 1, 24, NULL, NULL, NULL, 1, 'M2规则治理默认配置'
WHERE NOT EXISTS (SELECT 1 FROM alert_rule_config WHERE tenant_id = 0 AND rule_type = 'CONTRACT_OVERDUE');
INSERT INTO alert_rule_config
    (id, tenant_id, rule_type, alert_domain, alert_category, enabled, dedup_hours, window_days, threshold_ratio, severity_override, created_by, remark)
SELECT 121005, 0, 'PAYMENT_EXCEEDS_RATIO', 'PAYMENT', 'PAYMENT_RATIO', 1, 24, NULL, 1.0000, NULL, 1, 'M2规则治理默认配置'
WHERE NOT EXISTS (SELECT 1 FROM alert_rule_config WHERE tenant_id = 0 AND rule_type = 'PAYMENT_EXCEEDS_RATIO');
INSERT INTO alert_rule_config
    (id, tenant_id, rule_type, alert_domain, alert_category, enabled, dedup_hours, window_days, threshold_ratio, severity_override, created_by, remark)
SELECT 121006, 0, 'WARRANTY_EARLY_RELEASE', 'CONTRACT', 'CONTRACT_WARRANTY', 1, 24, NULL, NULL, NULL, 1, 'M2规则治理默认配置'
WHERE NOT EXISTS (SELECT 1 FROM alert_rule_config WHERE tenant_id = 0 AND rule_type = 'WARRANTY_EARLY_RELEASE');
INSERT INTO alert_rule_config
    (id, tenant_id, rule_type, alert_domain, alert_category, enabled, dedup_hours, window_days, threshold_ratio, severity_override, created_by, remark)
SELECT 121007, 0, 'CONTRACT_EXPIRING', 'CONTRACT', 'CONTRACT_TERM', 1, 24, 30, NULL, NULL, 1, 'M2规则治理默认配置'
WHERE NOT EXISTS (SELECT 1 FROM alert_rule_config WHERE tenant_id = 0 AND rule_type = 'CONTRACT_EXPIRING');
INSERT INTO alert_rule_config
    (id, tenant_id, rule_type, alert_domain, alert_category, enabled, dedup_hours, window_days, threshold_ratio, severity_override, created_by, remark)
SELECT 121008, 0, 'VARIATION_UNCONFIRMED', 'VARIATION', 'VARIATION_CONFIRM', 1, 24, 30, NULL, NULL, 1, 'M2规则治理默认配置'
WHERE NOT EXISTS (SELECT 1 FROM alert_rule_config WHERE tenant_id = 0 AND rule_type = 'VARIATION_UNCONFIRMED');
INSERT INTO alert_rule_config
    (id, tenant_id, rule_type, alert_domain, alert_category, enabled, dedup_hours, window_days, threshold_ratio, severity_override, created_by, remark)
SELECT 121009, 0, 'PURCHASE_DELIVERY_OVERDUE', 'PURCHASE', 'PURCHASE_DELIVERY', 1, 24, NULL, NULL, NULL, 1, 'M2规则治理默认配置'
WHERE NOT EXISTS (SELECT 1 FROM alert_rule_config WHERE tenant_id = 0 AND rule_type = 'PURCHASE_DELIVERY_OVERDUE');
