-- V81__init_accounting_entry.sql
-- 会计凭证主表 + 明细表
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS accounting_entry (
    id BIGINT NOT NULL COMMENT '凭证ID',
    tenant_id BIGINT NOT NULL DEFAULT 0,
    entry_code VARCHAR(64) NOT NULL COMMENT '凭证号',
    entry_date DATE NOT NULL COMMENT '凭证日期',
    entry_type VARCHAR(50) NOT NULL COMMENT 'BID_COST/MATERIAL/LABOR/OVERHEAD/REVENUE/SETTLEMENT',
    source_type VARCHAR(50) NOT NULL COMMENT '来源类型（与cost_item.source_type对应）',
    source_id BIGINT NOT NULL COMMENT '来源单据ID',
    entry_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/POSTED/REVERSED',
    total_debit DECIMAL(18,2) NOT NULL DEFAULT 0,
    total_credit DECIMAL(18,2) NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_entry_code (tenant_id, entry_code, deleted_flag),
    KEY idx_entry_source (source_type, source_id),
    KEY idx_entry_date (entry_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会计凭证主表';

CREATE TABLE IF NOT EXISTS accounting_entry_line (
    id BIGINT NOT NULL COMMENT '分录行ID',
    tenant_id BIGINT NOT NULL DEFAULT 0,
    entry_id BIGINT NOT NULL COMMENT '凭证ID',
    line_no INT NOT NULL DEFAULT 1 COMMENT '行号',
    direction VARCHAR(10) NOT NULL COMMENT 'DEBIT借方 / CREDIT贷方',
    cost_subject_id BIGINT NOT NULL COMMENT '科目ID（关联cost_subject.id）',
    amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '金额',
    summary VARCHAR(500) NULL COMMENT '摘要',
    PRIMARY KEY (id),
    KEY idx_entry_line (entry_id),
    KEY idx_entry_line_subject (cost_subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会计凭证明细表';

SET FOREIGN_KEY_CHECKS = 1;
