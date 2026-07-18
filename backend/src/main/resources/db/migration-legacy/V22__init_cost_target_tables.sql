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

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 目标成本表
-- ----------------------------
CREATE TABLE IF NOT EXISTS cost_target (
    id BIGINT NOT NULL COMMENT '目标成本ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    version_no VARCHAR(50) NOT NULL COMMENT '版本号',
    version_name VARCHAR(200) NULL COMMENT '版本名称',
    total_target_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '目标成本总额',
    is_active TINYINT NOT NULL DEFAULT 0 COMMENT '是否生效版本：0否，1是。同一项目仅允许一个生效版本',
    approval_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT' COMMENT '审批状态：DRAFT草稿，APPROVING审批中，APPROVED已通过，REJECTED已驳回',
    effective_date DATE NULL COMMENT '生效日期',
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT' COMMENT '业务状态：DRAFT草稿，APPROVING审批中，ACTIVE已生效，CANCELLED已作废',
    created_by BIGINT NULL COMMENT '创建人',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '更新人',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    remark TEXT NULL COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_cost_target_project (project_id),
    KEY idx_cost_target_active (project_id, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='目标成本表';

-- ----------------------------
-- 目标成本明细表
-- ----------------------------
CREATE TABLE IF NOT EXISTS cost_target_item (
    id BIGINT NOT NULL COMMENT '目标成本明细ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    target_id BIGINT NOT NULL COMMENT '目标成本ID，关联cost_target.id',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    cost_subject_id BIGINT NOT NULL COMMENT '成本科目ID，关联cost_subject.id',
    target_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '目标金额',
    created_by BIGINT NULL COMMENT '创建人',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '更新人',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    remark VARCHAR(500) NULL COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_cost_target_item_target (target_id),
    KEY idx_cost_target_item_subject (cost_subject_id),
    KEY idx_cost_target_item_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='目标成本明细表';

SET FOREIGN_KEY_CHECKS = 1;
