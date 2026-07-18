-- V80__init_bid_cost_and_overhead.sql
-- 招投标前期费用头表 + 投标保证金表 + 间接费用分摊规则表 + 分摊记录表
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ================================================================
-- 招投标前期费用头表（仅存状态，金额由 cost_item 聚合）
-- ================================================================
CREATE TABLE IF NOT EXISTS bid_cost (
    id BIGINT NOT NULL COMMENT '投标项目ID',
    tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NULL COMMENT '中标后关联的项目ID，未中标时为NULL',
    bid_project_name VARCHAR(200) NOT NULL COMMENT '投标项目名称',
    bid_status VARCHAR(50) NOT NULL DEFAULT 'BIDDING' COMMENT 'BIDDING投标中/WON已中标/LOST未中标',
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    KEY idx_bid_project (project_id),
    KEY idx_bid_status (bid_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='招投标前期费用头表 - 金额由cost_item聚合';

-- ================================================================
-- 投标保证金表（独立于合同履约成本核算）
-- ================================================================
CREATE TABLE IF NOT EXISTS bid_deposit (
    id BIGINT NOT NULL COMMENT '保证金ID',
    tenant_id BIGINT NOT NULL DEFAULT 0,
    bid_cost_id BIGINT NOT NULL COMMENT '关联投标项目',
    deposit_type VARCHAR(50) NOT NULL COMMENT 'BID投标保证金/PERFORMANCE履约保证金',
    deposit_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    returned_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '已退回金额',
    deposit_status VARCHAR(50) NOT NULL DEFAULT 'PAID' COMMENT 'PAID已缴/RETURNED已退回/FORFEITED已没收',
    paid_date DATE NULL,
    returned_date DATE NULL,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    KEY idx_bid_deposit_bid (bid_cost_id),
    KEY idx_bid_deposit_status (deposit_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投标保证金表';

-- ================================================================
-- 间接费用分摊规则表
-- ================================================================
CREATE TABLE IF NOT EXISTS overhead_allocation_rule (
    id BIGINT NOT NULL COMMENT '分摊规则ID',
    tenant_id BIGINT NOT NULL DEFAULT 0,
    cost_subject_id BIGINT NOT NULL COMMENT '间接费用科目ID（5401.04.xx）',
    allocation_basis VARCHAR(50) NOT NULL COMMENT 'DIRECT_LABOR直接人工比例/CONTRACT_AMOUNT合同额比例/USAGE实际使用',
    allocation_cycle VARCHAR(20) NOT NULL DEFAULT 'MONTHLY' COMMENT 'MONTHLY按月/PER_OCCURRENCE按次',
    status VARCHAR(50) NOT NULL DEFAULT 'ENABLE',
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark TEXT NULL COMMENT '分摊说明/备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_allocation_subject (tenant_id, cost_subject_id, deleted_flag),
    KEY idx_allocation_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='间接费用分摊规则表';

-- ================================================================
-- 间接费用分摊记录表
-- ================================================================
CREATE TABLE IF NOT EXISTS overhead_allocation_record (
    id BIGINT NOT NULL COMMENT '分摊记录ID',
    tenant_id BIGINT NOT NULL DEFAULT 0,
    rule_id BIGINT NOT NULL COMMENT '分摊规则ID',
    source_project_id BIGINT NOT NULL COMMENT '费用发生项目ID',
    target_project_id BIGINT NOT NULL COMMENT '分摊目标项目ID',
    cost_subject_id BIGINT NOT NULL COMMENT '科目ID',
    allocation_date DATE NOT NULL COMMENT '分摊日期',
    source_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '原始费用金额',
    allocated_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '分摊金额',
    allocation_ratio DECIMAL(5,4) NOT NULL DEFAULT 0 COMMENT '分摊比例',
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/CONFIRMED/POSTED',
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    KEY idx_alloc_rule (rule_id),
    KEY idx_alloc_date (allocation_date),
    KEY idx_alloc_target_project (target_project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='间接费用分摊记录表';

SET FOREIGN_KEY_CHECKS = 1;
