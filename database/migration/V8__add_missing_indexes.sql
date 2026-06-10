-- V8__add_missing_indexes.sql
-- 建筑工程总包项目全过程管理系统 - 补充列表排序缺失索引
-- 背景：各业务列表均按 created_at DESC 排序，但缺少对应索引，数据量增长后会触发 filesort。
-- 数据库：MySQL 8.0+

SET NAMES utf8mb4;

-- 用户列表排序
CREATE INDEX idx_sys_user_created_at ON sys_user (created_at);

-- 项目列表排序
CREATE INDEX idx_pm_project_created_at ON pm_project (created_at);

-- 合作方列表排序
CREATE INDEX idx_md_partner_created_at ON md_partner (created_at);

-- 合同列表排序
CREATE INDEX idx_ct_contract_created_at ON ct_contract (created_at);
