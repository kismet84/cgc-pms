-- V121: alert-rule governance baseline for M2

ALTER TABLE alert_log
    ADD COLUMN alert_category VARCHAR(50) NULL COMMENT '细分类标签' AFTER alert_domain,
    ADD COLUMN dedup_key VARCHAR(200) NULL COMMENT '去重键' AFTER source_id,
    ADD COLUMN process_status VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT '处理状态：OPEN/PROCESSED/ARCHIVED/INVALID' AFTER is_read,
    ADD COLUMN processed_at DATETIME NULL COMMENT '处理时间' AFTER process_status,
    ADD COLUMN archived_at DATETIME NULL COMMENT '归档时间' AFTER processed_at,
    ADD COLUMN status_remark VARCHAR(500) NULL COMMENT '状态备注' AFTER archived_at;

CREATE INDEX idx_alert_process_status ON alert_log(process_status);
CREATE INDEX idx_alert_dedup_window ON alert_log(tenant_id, dedup_key, process_status, triggered_at);

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
            THEN CONCAT('S:', source_type, ':', source_id, ':R:', rule_type)
        WHEN contract_id IS NOT NULL
            THEN CONCAT('C:', contract_id, ':R:', rule_type)
        ELSE CONCAT('P:', project_id, ':R:', rule_type)
    END
WHERE alert_category IS NULL
   OR alert_category = ''
   OR process_status IS NULL
   OR process_status = ''
   OR dedup_key IS NULL
   OR dedup_key = '';

CREATE TABLE IF NOT EXISTS alert_rule_config (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    rule_type VARCHAR(100) NOT NULL COMMENT '规则类型',
    alert_domain VARCHAR(50) NOT NULL COMMENT '业务域',
    alert_category VARCHAR(50) NOT NULL COMMENT '细分类标签',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1启用，0停用',
    dedup_hours INT NOT NULL DEFAULT 24 COMMENT '去重窗口小时数',
    window_days INT NULL COMMENT '规则窗口天数',
    threshold_ratio DECIMAL(10,4) NULL COMMENT '阈值比例',
    severity_override VARCHAR(20) NULL COMMENT '严重度覆盖',
    created_by BIGINT NULL COMMENT '创建人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '更新人',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    remark VARCHAR(500) NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_alert_rule_config (tenant_id, rule_type),
    KEY idx_alert_rule_config_enabled (tenant_id, enabled, deleted_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预警规则配置表';

INSERT IGNORE INTO alert_rule_config
    (id, tenant_id, rule_type, alert_domain, alert_category, enabled, dedup_hours, window_days, threshold_ratio, severity_override, created_by, remark)
VALUES
    (121001, 0, 'DYNAMIC_COST_EXCEEDS_TARGET', 'COST', 'COST_DYNAMIC', 1, 24, NULL, 1.0000, NULL, 1, 'M2规则治理默认配置'),
    (121002, 0, 'MATERIAL_EXCEEDS_BUDGET', 'COST', 'COST_MATERIAL', 1, 24, NULL, 1.0000, NULL, 1, 'M2规则治理默认配置'),
    (121003, 0, 'SUBCONTRACT_EXCEEDS_CONTRACT', 'COST', 'COST_SUBCONTRACT', 1, 24, NULL, 1.0000, NULL, 1, 'M2规则治理默认配置'),
    (121004, 0, 'CONTRACT_OVERDUE', 'CONTRACT', 'CONTRACT_TERM', 1, 24, NULL, NULL, NULL, 1, 'M2规则治理默认配置'),
    (121005, 0, 'PAYMENT_EXCEEDS_RATIO', 'PAYMENT', 'PAYMENT_RATIO', 1, 24, NULL, 1.0000, NULL, 1, 'M2规则治理默认配置'),
    (121006, 0, 'WARRANTY_EARLY_RELEASE', 'CONTRACT', 'CONTRACT_WARRANTY', 1, 24, NULL, NULL, NULL, 1, 'M2规则治理默认配置'),
    (121007, 0, 'CONTRACT_EXPIRING', 'CONTRACT', 'CONTRACT_TERM', 1, 24, 30, NULL, NULL, 1, 'M2规则治理默认配置'),
    (121008, 0, 'VARIATION_UNCONFIRMED', 'VARIATION', 'VARIATION_CONFIRM', 1, 24, 30, NULL, NULL, 1, 'M2规则治理默认配置'),
    (121009, 0, 'PURCHASE_DELIVERY_OVERDUE', 'PURCHASE', 'PURCHASE_DELIVERY', 1, 24, NULL, NULL, NULL, 1, 'M2规则治理默认配置');
