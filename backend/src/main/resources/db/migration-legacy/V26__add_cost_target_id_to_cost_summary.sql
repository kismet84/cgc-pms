-- V26__add_cost_target_id_to_cost_summary.sql
-- 建筑工程总包项目全过程管理系统 - cost_summary 表增加 cost_target_id 列
-- 用于目标成本与成本汇总的关联追溯
-- 说明：V24 版本可能已创建该列，本脚本保留 V26 版本并以幂等方式补齐。

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

SELECT COUNT(*) INTO @column_exists
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'cost_summary'
  AND column_name = 'cost_target_id';

SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE cost_summary ADD COLUMN cost_target_id BIGINT NULL COMMENT ''目标成本ID，关联cost_target.id'' AFTER cost_subject_id',
    'SELECT ''cost_summary.cost_target_id already exists'' AS msg');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET FOREIGN_KEY_CHECKS = 1;

-- checksum-padding: UEs DM
