-- V77__extend_cost_subject_add_account_category.sql
-- 成本科目扩展：增加 account_category 列区分成本/收入/结算/应收类别
-- 遵循 V58/V75 唯一键模式：(tenant_id, subject_code, account_category, deleted_flag)
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 先检查列是否已存在（幂等）
SELECT COUNT(*) INTO @col_exists
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'cost_subject'
  AND column_name = 'account_category';

SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE cost_subject ADD COLUMN account_category VARCHAR(20) NOT NULL DEFAULT ''COST'' COMMENT ''科目大类：COST成本，REVENUE收入，SETTLEMENT结算，RECEIVABLE应收''',
    'SELECT ''column account_category already exists'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 变更唯一键：增加 account_category，保留 deleted_flag
ALTER TABLE cost_subject DROP INDEX uk_cost_subject_code;
ALTER TABLE cost_subject ADD UNIQUE KEY uk_cost_subject_code
    (tenant_id, subject_code, account_category, deleted_flag);

SET FOREIGN_KEY_CHECKS = 1;
