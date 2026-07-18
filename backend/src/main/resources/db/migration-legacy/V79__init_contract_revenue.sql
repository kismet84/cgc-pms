-- V79__init_contract_revenue.sql
-- 业主收入确认表 + cost_summary 增加 confirmed_revenue 字段
-- 与 stl_settlement (分包结算) 业务隔离
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS contract_revenue (
    id BIGINT NOT NULL COMMENT '收入确认ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL COMMENT '项目ID',
    contract_id BIGINT NOT NULL COMMENT '主合同ID（对甲方的总包合同）',
    revenue_code VARCHAR(64) NOT NULL COMMENT '收入确认单号',
    revenue_date DATE NOT NULL COMMENT '收入确认日期',

    -- 履约进度
    progress_percent DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '累计履约进度(%)',
    progress_desc VARCHAR(500) NULL COMMENT '进度描述',

    -- 本期确认收入
    revenue_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '本期确认收入（不含税）',
    revenue_tax DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '销项税额',
    revenue_amount_with_tax DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '含税收入',

    -- 业主结算
    billed_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '本期向业主结算金额',
    billed_tax DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '结算税额',

    -- 状态
    approval_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING/APPROVED/REJECTED',

    -- 关联的 cost_item ID
    cost_item_id BIGINT NULL COMMENT '审批通过后生成的 cost_item 记录ID',

    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_revenue_code (tenant_id, revenue_code, deleted_flag),
    KEY idx_revenue_contract (contract_id),
    KEY idx_revenue_date (revenue_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业主收入确认表';

-- cost_summary 扩展：增加已确认收入字段
SELECT COUNT(*) INTO @col_exists
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'cost_summary'
  AND column_name = 'confirmed_revenue';

SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE cost_summary ADD COLUMN confirmed_revenue DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT ''累计已确认收入（按履约进度，来源contract_revenue）'' AFTER contract_income',
    'SELECT ''column confirmed_revenue already exists'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET FOREIGN_KEY_CHECKS = 1;
