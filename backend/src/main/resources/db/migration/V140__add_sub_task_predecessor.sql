-- 为现有分包任务增加一个可空的单前置 Finish-to-Start 依赖。
-- 历史任务保持 NULL，不做回填；同租户/同项目与循环校验由服务层 fail-close。

ALTER TABLE sub_task
    ADD COLUMN predecessor_task_id BIGINT NULL COMMENT '单一前置分包任务ID'
    AFTER partner_id;

CREATE INDEX idx_sub_task_predecessor ON sub_task (predecessor_task_id);

ALTER TABLE sub_task
    ADD CONSTRAINT fk_sub_task_predecessor
    FOREIGN KEY (predecessor_task_id) REFERENCES sub_task (id)
    ON DELETE RESTRICT;
