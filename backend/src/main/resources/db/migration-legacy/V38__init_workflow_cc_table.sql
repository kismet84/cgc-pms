-- V38__init_workflow_cc_table.sql
-- 建筑工程总包项目全过程管理系统 - 审批抄送表
-- 数据库：MySQL 8.0+
-- ID 策略：后端雪花 ID / ASSIGN_ID，数据库不使用 AUTO_INCREMENT
-- 说明：附加 join 表实现抄送，不修改 wf_instance / wf_task / wf_record 及 WorkflowEngine 核心

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 审批抄送表
-- ----------------------------
CREATE TABLE IF NOT EXISTS wf_cc (
    id BIGINT NOT NULL COMMENT '抄送ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    instance_id BIGINT NOT NULL COMMENT '审批实例ID，关联wf_instance.id',
    cc_user_id BIGINT NOT NULL COMMENT '抄送人用户ID',
    cc_user_name VARCHAR(100) NOT NULL COMMENT '抄送人姓名（冗余，避免联表）',
    business_type VARCHAR(100) NULL COMMENT '业务类型',
    business_id BIGINT NULL COMMENT '业务ID',
    title VARCHAR(500) NULL COMMENT '审批标题（冗余）',
    is_read TINYINT NOT NULL DEFAULT 0 COMMENT '已读标记：0未读，1已读',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '抄送时间',
    PRIMARY KEY (id),
    KEY idx_wc_tenant_ccuser (tenant_id, cc_user_id),
    KEY idx_wc_instance (instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批抄送表';

SET FOREIGN_KEY_CHECKS = 1;
