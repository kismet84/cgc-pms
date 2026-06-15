-- V51__fix_project_unique_with_deleted_flag.sql
-- Add deleted_flag to pm_project unique key so logically-deleted projects can be re-created
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
ALTER TABLE pm_project DROP INDEX uk_pm_project_code;
ALTER TABLE pm_project ADD UNIQUE KEY uk_pm_project_code (tenant_id, project_code, deleted_flag);
SET FOREIGN_KEY_CHECKS = 1;
