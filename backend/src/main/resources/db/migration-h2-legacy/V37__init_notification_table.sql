-- V37__init_notification_table.sql
-- 建筑工程总包项目全过程管理系统 - 站内消息通知表
-- 数据库：MySQL 8.0+
-- ID 策略：后端雪花 ID / ASSIGN_ID，数据库不使用 AUTO_INCREMENT
-- 说明：user_id + tenant_id 冗余存储，SSE 推送/定时任务路径不可读取 UserContext

-- ----------------------------
-- 站内消息通知表
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_notification (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    user_id BIGINT NOT NULL,
    title VARCHAR(500) NOT NULL,
    content TEXT NULL,
    biz_type VARCHAR(100) NULL,
    biz_id BIGINT NULL,
    notify_type VARCHAR(50) NOT NULL DEFAULT 'INFO',
    is_read SMALLINT NOT NULL DEFAULT 0,
    read_time TIMESTAMP NULL,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_sn_tenant_user_read (tenant_id, user_id, is_read),
    KEY idx_sn_biz (biz_type, biz_id)
);
