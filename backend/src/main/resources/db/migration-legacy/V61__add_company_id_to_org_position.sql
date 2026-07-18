-- V61__add_company_id_to_org_position.sql
-- 为岗位表增加所属公司关联
-- 数据库：MySQL 8.0+

SET NAMES utf8mb4;

ALTER TABLE org_position
    ADD COLUMN company_id BIGINT NULL COMMENT '所属公司ID' AFTER tenant_id,
    ADD KEY idx_op_company (company_id);
