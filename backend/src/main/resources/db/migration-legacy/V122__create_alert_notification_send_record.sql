CREATE TABLE IF NOT EXISTS alert_notification_send_record (
    id BIGINT NOT NULL COMMENT '发送记录ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    alert_id BIGINT NOT NULL COMMENT '预警ID',
    event_type VARCHAR(50) NOT NULL COMMENT '事件类型：ALERT_CREATED / STATUS_CHANGED',
    channel VARCHAR(50) NOT NULL COMMENT '渠道：IN_APP / EMAIL / WECHAT',
    target_user_id BIGINT NULL COMMENT '目标用户ID',
    biz_notification_id BIGINT NULL COMMENT '站内信ID',
    send_status VARCHAR(50) NOT NULL COMMENT '发送状态：SENT / SKIPPED / FAILED',
    fail_reason VARCHAR(500) NULL COMMENT '失败或跳过原因',
    requested_at DATETIME NOT NULL COMMENT '请求发送时间',
    completed_at DATETIME NULL COMMENT '完成时间',
    PRIMARY KEY (id),
    KEY idx_ansr_alert_event (tenant_id, alert_id, event_type),
    KEY idx_ansr_channel_status (tenant_id, channel, send_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预警通知渠道发送记录';
