-- V26__add_cost_target_id_to_cost_summary.sql
-- 建筑工程总包项目全过程管理系统 - cost_summary 表增加 cost_target_id 列
-- 用于目标成本与成本汇总的关联追溯

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

ALTER TABLE cost_summary
    ADD COLUMN cost_target_id BIGINT NULL COMMENT '目标成本ID，关联cost_target.id'
    AFTER cost_subject_id;

SET FOREIGN_KEY_CHECKS = 1;
