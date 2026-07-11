-- H2 mirror for the single-predecessor Finish-to-Start relation.

ALTER TABLE sub_task
    ADD COLUMN predecessor_task_id BIGINT NULL;

CREATE INDEX idx_sub_task_predecessor ON sub_task (predecessor_task_id);

ALTER TABLE sub_task
    ADD CONSTRAINT fk_sub_task_predecessor
    FOREIGN KEY (predecessor_task_id) REFERENCES sub_task (id);
