-- V23__init_contract_change_table.sql
-- 建筑工程总包项目全过程管理系统 - 合同变更表
-- 数据库：MySQL 8.0+
-- ID 策略：后端雪花 ID / ASSIGN_ID，数据库不使用 AUTO_INCREMENT
--
-- CT_CHANGE 是正式合同变更（调整合同金额/工期/条款），不同于 VAR_ORDER（现场签证）。

-- ----------------------------
-- 合同变更表
-- ----------------------------
CREATE TABLE IF NOT EXISTS ct_contract_change (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL,
    contract_id BIGINT NOT NULL,
    change_code VARCHAR(64) NOT NULL,
    change_name VARCHAR(200) NOT NULL,
    change_type VARCHAR(50) NOT NULL,
    before_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    change_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    after_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    reason TEXT NULL,
    approval_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    effective_flag SMALLINT NOT NULL DEFAULT 0,
    cost_generated_flag SMALLINT NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE (tenant_id, change_code),
    KEY idx_change_contract (contract_id),
    KEY idx_change_project (project_id)
);
