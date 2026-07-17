-- P0: alert acknowledgement, SLA and immutable lifecycle trace
ALTER TABLE alert_log
    ADD COLUMN read_by BIGINT NULL COMMENT '首次阅读人' AFTER is_read,
    ADD COLUMN read_at DATETIME NULL COMMENT '首次阅读时间' AFTER read_by,
    ADD COLUMN acknowledged_by BIGINT NULL COMMENT '接单责任人' AFTER read_at,
    ADD COLUMN acknowledged_at DATETIME NULL COMMENT '接单时间' AFTER acknowledged_by,
    ADD COLUMN response_due_at DATETIME NULL COMMENT '响应期限' AFTER acknowledged_at,
    ADD COLUMN resolution_due_at DATETIME NULL COMMENT '处置期限' AFTER response_due_at,
    ADD COLUMN escalation_level INT NOT NULL DEFAULT 0 COMMENT '升级级别：0未升级/1响应超时/2处置超时' AFTER resolution_due_at,
    ADD COLUMN last_escalated_at DATETIME NULL COMMENT '最近升级时间' AFTER escalation_level,
    ADD COLUMN processed_by BIGINT NULL COMMENT '处理人' AFTER processed_at,
    ADD COLUMN archived_by BIGINT NULL COMMENT '归档/失效操作人' AFTER archived_at,
    ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本' AFTER status_remark;

UPDATE alert_log
SET read_by = CASE WHEN is_read = 1 THEN COALESCE(updated_by, created_by) ELSE NULL END,
    read_at = CASE WHEN is_read = 1 THEN COALESCE(updated_at, created_at, triggered_at) ELSE NULL END,
    acknowledged_by = CASE WHEN process_status IN ('PROCESSED','ARCHIVED') THEN COALESCE(updated_by, created_by) ELSE NULL END,
    acknowledged_at = CASE WHEN process_status IN ('PROCESSED','ARCHIVED') THEN COALESCE(processed_at, updated_at, triggered_at) ELSE NULL END,
    processed_by = CASE WHEN process_status IN ('PROCESSED','ARCHIVED') THEN COALESCE(updated_by, created_by) ELSE NULL END,
    archived_by = CASE WHEN process_status IN ('ARCHIVED','INVALID') THEN COALESCE(updated_by, created_by) ELSE NULL END,
    response_due_at = DATE_ADD(COALESCE(triggered_at, created_at), INTERVAL
        CASE severity WHEN 'HIGH' THEN 4 WHEN 'LOW' THEN 72 ELSE 24 END HOUR),
    resolution_due_at = DATE_ADD(COALESCE(triggered_at, created_at), INTERVAL
        CASE severity WHEN 'HIGH' THEN 24 WHEN 'LOW' THEN 168 ELSE 72 END HOUR),
    escalation_level = 0
WHERE deleted_flag = 0;

CREATE INDEX idx_alert_ack_due ON alert_log(tenant_id, process_status, acknowledged_at, response_due_at);
CREATE INDEX idx_alert_escalation_due ON alert_log(tenant_id, process_status, escalation_level, resolution_due_at);

CREATE TABLE alert_lifecycle_event (
    id BIGINT NOT NULL COMMENT '生命周期事件ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    alert_id BIGINT NOT NULL COMMENT '预警ID',
    event_type VARCHAR(50) NOT NULL COMMENT 'CREATED/READ/ACKNOWLEDGED/ESCALATED_L1/ESCALATED_L2/STATUS_CHANGED/AUTO_ARCHIVED',
    from_status VARCHAR(20) NULL COMMENT '原状态',
    to_status VARCHAR(20) NULL COMMENT '目标状态',
    operator_id BIGINT NULL COMMENT '操作人',
    remark VARCHAR(500) NULL COMMENT '操作说明',
    occurred_at DATETIME NOT NULL COMMENT '发生时间',
    payload_json TEXT NOT NULL COMMENT '事件快照JSON',
    payload_hash CHAR(64) NOT NULL COMMENT '事件快照SHA-256',
    PRIMARY KEY (id),
    CONSTRAINT fk_alert_lifecycle_alert FOREIGN KEY (alert_id) REFERENCES alert_log(id) ON DELETE CASCADE,
    KEY idx_alert_lifecycle_trace (tenant_id, alert_id, occurred_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预警不可变生命周期事件';
