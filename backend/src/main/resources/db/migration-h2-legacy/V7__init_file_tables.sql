-- V7__init_file_tables.sql
-- 建筑工程总包项目全过程管理系统 - 文件管理表
-- 数据库：MySQL 8.0+
-- ID 策略：后端雪花 ID / ASSIGN_ID，数据库不使用 AUTO_INCREMENT

-- ----------------------------
-- 文件表（通用文件存储，不耦合特定业务）
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_file (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    business_type VARCHAR(50) NOT NULL,
    business_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    original_name VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL DEFAULT 0,
    content_type VARCHAR(200) NULL,
    storage_path VARCHAR(500) NOT NULL,
    bucket_name VARCHAR(100) NOT NULL DEFAULT 'cgc-pms',
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    KEY idx_sys_file_tenant (tenant_id),
    KEY idx_sys_file_business (business_type, business_id),
    KEY idx_sys_file_created_at (created_at)
);
