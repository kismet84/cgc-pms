-- 间接费月度执行事实：唯一键是并发/重复执行的最终数据库门禁。
CREATE TABLE overhead_allocation_run (
    id BIGINT NOT NULL COMMENT '执行ID（雪花ID）',
    tenant_id BIGINT NOT NULL,
    rule_id BIGINT NOT NULL COMMENT '分摊规则ID',
    period DATE NOT NULL COMMENT '目标自然月月末',
    trigger_type VARCHAR(20) NOT NULL COMMENT 'MANUAL/SCHEDULED',
    executed_by BIGINT NULL COMMENT '手工执行用户；定时任务为空',
    run_status VARCHAR(30) NOT NULL COMMENT 'PENDING/SUCCESS/SKIPPED_ZERO/SKIPPED_NO_WEIGHT',
    allocated_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    cost_item_count INT NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_overhead_run_period (tenant_id, rule_id, period, deleted_flag),
    KEY idx_overhead_run_tenant_period (tenant_id, period),
    KEY idx_overhead_run_status (run_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='间接费月度分摊执行事实';
