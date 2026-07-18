-- V58__fix_cost_subject_unique_with_deleted_flag.sql
-- 逻辑删除后唯一约束应包含 deleted_flag，允许同一科目编码在删除后重新创建
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
ALTER TABLE cost_subject DROP INDEX uk_cost_subject_code;
ALTER TABLE cost_subject ADD UNIQUE KEY uk_cost_subject_code (tenant_id, subject_code, deleted_flag);
SET FOREIGN_KEY_CHECKS = 1;
