-- V62__add_department_id_to_org_position.sql
-- 为岗位表增加所属部门关联
-- 数据库：MySQL 8.0+

SET NAMES utf8mb4;

ALTER TABLE org_position
    ADD COLUMN department_id BIGINT NULL COMMENT '所属部门ID' AFTER company_id,
    ADD KEY idx_op_department (department_id);
