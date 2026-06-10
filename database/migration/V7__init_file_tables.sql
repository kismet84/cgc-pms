-- V7__init_file_tables.sql
-- 建筑工程总包项目全过程管理系统 - 文件管理表
-- 数据库：MySQL 8.0+
-- ID 策略：后端雪花 ID / ASSIGN_ID，数据库不使用 AUTO_INCREMENT

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 文件表（通用文件存储，不耦合特定业务）
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_file (
    id BIGINT NOT NULL COMMENT '文件ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    business_type VARCHAR(50) NOT NULL COMMENT '业务类型（如CONTRACT、PROJECT等）',
    business_id BIGINT NOT NULL COMMENT '业务ID',
    file_name VARCHAR(255) NOT NULL COMMENT '存储文件名（UUID.扩展名）',
    original_name VARCHAR(500) NOT NULL COMMENT '原始文件名',
    file_size BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
    content_type VARCHAR(200) NULL COMMENT '文件MIME类型',
    storage_path VARCHAR(500) NOT NULL COMMENT 'MinIO对象路径（businessType/businessId/fileName）',
    bucket_name VARCHAR(100) NOT NULL DEFAULT 'cgc-pms' COMMENT 'MinIO桶名称',
    created_by BIGINT NULL COMMENT '创建人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '更新人',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    remark VARCHAR(500) NULL COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_sys_file_tenant (tenant_id),
    KEY idx_sys_file_business (business_type, business_id),
    KEY idx_sys_file_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统文件表';

SET FOREIGN_KEY_CHECKS = 1;
