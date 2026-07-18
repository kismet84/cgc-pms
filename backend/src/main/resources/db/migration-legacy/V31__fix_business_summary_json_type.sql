-- V31__fix_business_summary_json_type.sql
-- 建筑工程总包项目全过程管理系统 - 修复 wf_instance.business_summary JSON → TEXT
-- 数据库：MySQL 8.0+
--
-- 背景：wf_instance.business_summary 原定义为 JSON 类型，
-- 但 WorkflowEngine.submit() 传入的是纯文本字符串（非 JSON 格式），
-- 导致 MySQL 8.0 JSON 严格校验失败。
-- 将此列改为 TEXT 类型以兼容纯文本摘要值。

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

ALTER TABLE wf_instance
    MODIFY COLUMN business_summary TEXT NULL COMMENT '业务摘要';

SET FOREIGN_KEY_CHECKS = 1;
