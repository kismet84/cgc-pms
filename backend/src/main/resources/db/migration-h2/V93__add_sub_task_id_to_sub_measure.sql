-- V93__add_sub_task_id_to_sub_measure.sql
-- H2 compatible: 为 sub_measure 增加 sub_task_id 显式关联字段

ALTER TABLE sub_measure
    ADD COLUMN sub_task_id BIGINT NULL;

CREATE INDEX idx_sub_measure_sub_task_id ON sub_measure (sub_task_id);
