-- V38__init_workflow_cc_table.sql
-- 建筑工程总包项目全过程管理系统 - 审批抄送表
-- 数据库：MySQL 8.0+
-- ID 策略：后端雪花 ID / ASSIGN_ID，数据库不使用 AUTO_INCREMENT
-- 说明：附加 join 表实现抄送，不修改 wf_instance / wf_task / wf_record 及 WorkflowEngine 核心

-- ----------------------------
-- 审批抄送表
-- ----------------------------
CREATE TABLE IF NOT EXISTS wf_cc (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    instance_id BIGINT NOT NULL,
    cc_user_id BIGINT NOT NULL,
    cc_user_name VARCHAR(100) NOT NULL,
    business_type VARCHAR(100) NULL,
    business_id BIGINT NULL,
    title VARCHAR(500) NULL,
    is_read SMALLINT NOT NULL DEFAULT 0,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_wc_tenant_ccuser (tenant_id, cc_user_id),
    KEY idx_wc_instance (instance_id)
);
