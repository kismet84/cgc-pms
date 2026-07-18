-- V22__init_cost_target_tables.sql
-- 建筑工程总包项目全过程管理系统 - 成本目标表
-- 数据库：MySQL 8.0+
-- ID 策略：后端雪花 ID / ASSIGN_ID，数据库不使用 AUTO_INCREMENT
-- 
-- 业务约束说明：
--   同一项目仅允许一个生效版本（is_active=1）。
--   MySQL 8.0 不支持部分索引（WHERE 条件索引），无法通过数据库层唯一约束强制此规则。
--   因此，唯一性由应用层保证：切换版本时须先将旧版本 is_active 置 0，再激活新版本。
--   推荐在 Service 层使用事务 + SELECT FOR UPDATE 或 Redis 分布式锁确保并发安全。

-- ----------------------------
-- 目标成本表
-- ----------------------------
CREATE TABLE IF NOT EXISTS cost_target (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL,
    version_no VARCHAR(50) NOT NULL,
    version_name VARCHAR(200) NULL,
    total_target_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    is_active SMALLINT NOT NULL DEFAULT 0,
    approval_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    effective_date DATE NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT NULL,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark TEXT NULL,
    PRIMARY KEY (id),
    KEY idx_cost_target_project (project_id),
    KEY idx_cost_target_active (project_id, is_active)
);

-- ----------------------------
-- 目标成本明细表
-- ----------------------------
CREATE TABLE IF NOT EXISTS cost_target_item (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    target_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    cost_subject_id BIGINT NOT NULL,
    target_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    KEY idx_cost_target_item_target (target_id),
    KEY idx_cost_target_item_subject (cost_subject_id),
    KEY idx_cost_target_item_project (project_id)
);
