-- V24__enhance_settlement_alert_summary.sql
-- H2-compatible version

-- ============================================================
-- A. 结算表字段增强
-- ============================================================

ALTER TABLE stl_settlement
    ADD COLUMN unpaid_amount DECIMAL(18,2) NOT NULL DEFAULT 0;
ALTER TABLE stl_settlement
    ADD COLUMN warranty_amount DECIMAL(18,2) NOT NULL DEFAULT 0;
ALTER TABLE stl_settlement
    ADD COLUMN settlement_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT';
ALTER TABLE stl_settlement
    ADD COLUMN finalized_at TIMESTAMP NULL;

ALTER TABLE stl_settlement_item
    ADD COLUMN source_type VARCHAR(50) NULL;
ALTER TABLE stl_settlement_item
    ADD COLUMN source_id BIGINT NULL;

-- ============================================================
-- B. 预警记录表
-- ============================================================

CREATE TABLE IF NOT EXISTS alert_log (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL,
    rule_type VARCHAR(100) NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    message TEXT NULL,
    triggered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_read SMALLINT NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    KEY idx_alert_project (project_id),
    KEY idx_alert_tenant (tenant_id),
    KEY idx_alert_read (is_read),
    KEY idx_alert_triggered (triggered_at)
);

-- ============================================================
-- C. 成本汇总表增强
-- ============================================================

ALTER TABLE cost_summary
    ADD COLUMN cost_target_id BIGINT NULL;

CREATE INDEX IF NOT EXISTS idx_summary_tenant_project ON cost_summary(tenant_id, project_id);
CREATE INDEX IF NOT EXISTS idx_summary_subject ON cost_summary(project_id, cost_subject_id);
