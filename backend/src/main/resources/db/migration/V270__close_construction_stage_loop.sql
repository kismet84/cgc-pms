-- Mainline 68: WBS traceability and explicit construction lifecycle stages.

ALTER TABLE qs_inspection_record
    ADD COLUMN wbs_task_id BIGINT NULL,
    ADD KEY idx_qs_inspection_wbs (tenant_id, wbs_task_id),
    ADD CONSTRAINT fk_qs_inspection_wbs FOREIGN KEY (tenant_id, wbs_task_id)
        REFERENCES project_wbs_task (tenant_id, id) ON DELETE RESTRICT;

ALTER TABLE sub_task
    ADD COLUMN wbs_task_id BIGINT NULL,
    ADD UNIQUE KEY uk_sub_task_tenant_id (tenant_id, id),
    ADD KEY idx_sub_task_wbs (tenant_id, wbs_task_id),
    ADD CONSTRAINT fk_sub_task_wbs FOREIGN KEY (tenant_id, wbs_task_id)
        REFERENCES project_wbs_task (tenant_id, id) ON DELETE RESTRICT;

ALTER TABLE sub_measure
    ADD CONSTRAINT fk_sub_measure_sub_task FOREIGN KEY (tenant_id, sub_task_id)
        REFERENCES sub_task (tenant_id, id) ON DELETE RESTRICT;

ALTER TABLE mat_requisition_item
    ADD COLUMN wbs_task_id BIGINT NULL,
    ADD KEY idx_mat_requisition_item_wbs (tenant_id, wbs_task_id),
    ADD CONSTRAINT fk_mat_requisition_item_wbs FOREIGN KEY (tenant_id, wbs_task_id)
        REFERENCES project_wbs_task (tenant_id, id) ON DELETE RESTRICT;

ALTER TABLE mat_stock_txn
    ADD COLUMN wbs_task_id BIGINT NULL,
    ADD KEY idx_mat_stock_txn_wbs (tenant_id, wbs_task_id),
    ADD CONSTRAINT fk_mat_stock_txn_wbs FOREIGN KEY (tenant_id, wbs_task_id)
        REFERENCES project_wbs_task (tenant_id, id) ON DELETE RESTRICT;

ALTER TABLE production_measurement_line
    ADD COLUMN wbs_task_id BIGINT NULL,
    DROP INDEX uk_production_measure_item,
    DROP INDEX uk_production_measure_change,
    ADD UNIQUE KEY uk_production_measure_item_wbs
        (tenant_id, measurement_id, contract_item_id, wbs_task_id),
    ADD UNIQUE KEY uk_production_measure_change_wbs
        (tenant_id, measurement_id, contract_change_id, wbs_task_id),
    ADD KEY idx_production_measure_line_wbs (tenant_id, wbs_task_id),
    ADD CONSTRAINT fk_production_measure_line_wbs FOREIGN KEY (tenant_id, wbs_task_id)
        REFERENCES project_wbs_task (tenant_id, id) ON DELETE RESTRICT;

ALTER TABLE cost_item
    ADD COLUMN wbs_task_id BIGINT NULL,
    ADD KEY idx_cost_item_wbs (tenant_id, wbs_task_id),
    ADD CONSTRAINT fk_cost_item_wbs FOREIGN KEY (tenant_id, wbs_task_id)
        REFERENCES project_wbs_task (tenant_id, id) ON DELETE RESTRICT;

UPDATE sys_dict_data SET order_num=6 WHERE tenant_id=0 AND id=100104;
UPDATE sys_dict_data SET order_num=7 WHERE tenant_id=0 AND id=100105;
UPDATE sys_dict_data SET order_num=8 WHERE tenant_id=0 AND id=100103;

INSERT INTO sys_dict_data
    (id, tenant_id, dict_type_id, dict_label, dict_value, css_class, list_class,
     order_num, status, created_at, updated_at)
VALUES
    (2700101, 0, 1001, '完工收尾', 'COMPLETION', NULL, 'warning', 4, 'ENABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2700102, 0, 1001, '质保阶段', 'WARRANTY', NULL, 'info', 5, 'ENABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
