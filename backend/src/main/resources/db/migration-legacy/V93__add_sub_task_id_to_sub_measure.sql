-- V93__add_sub_task_id_to_sub_measure.sql
-- 为 sub_measure 增加 sub_task_id 显式关联字段
-- 设计决策：本阶段 sub_task_id 为可空字段，历史数据允许保留 NULL，不做强制回填

ALTER TABLE sub_measure
    ADD COLUMN sub_task_id BIGINT NULL COMMENT '关联分包任务ID'
    AFTER partner_id;

-- 添加索引以加速按任务维度查询计量
-- 不做物理外键约束，原因：
--   1. 兼容历史数据（sub_task_id 可能为 NULL）
--   2. 渐进迁移策略，未来可能升级为条件必填
--   3. Service 层校验完整覆盖非法引用场景
CREATE INDEX idx_sub_measure_sub_task_id ON sub_measure (sub_task_id);
