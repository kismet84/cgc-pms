-- V37__init_notification_table.sql
-- 建筑工程总包项目全过程管理系统 - 站内消息通知表
-- 数据库：MySQL 8.0+
-- ID 策略：后端雪花 ID / ASSIGN_ID，数据库不使用 AUTO_INCREMENT
-- 说明：user_id + tenant_id 冗余存储，SSE 推送/定时任务路径不可读取 UserContext

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 站内消息通知表
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_notification (
    id BIGINT NOT NULL COMMENT '通知ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID（显式冗余，非 UserContext）',
    user_id BIGINT NOT NULL COMMENT '接收人用户ID',
    title VARCHAR(500) NOT NULL COMMENT '通知标题',
    content TEXT NULL COMMENT '通知内容',
    biz_type VARCHAR(100) NULL COMMENT '业务类型：WORKFLOW_APPROVAL审批，WORKFLOW_REJECT驳回，WORKFLOW_CC抄送，ALERT预警，SYSTEM系统',
    biz_id BIGINT NULL COMMENT '业务ID（审批实例/预警等）',
    notify_type VARCHAR(50) NOT NULL DEFAULT 'INFO' COMMENT '通知类型：INFO信息，WARNING警告，ERROR错误',
    is_read TINYINT NOT NULL DEFAULT 0 COMMENT '已读标记：0未读，1已读',
    read_time DATETIME NULL COMMENT '阅读时间',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_sn_tenant_user_read (tenant_id, user_id, is_read),
    KEY idx_sn_biz (biz_type, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站内消息通知表';

SET FOREIGN_KEY_CHECKS = 1;
