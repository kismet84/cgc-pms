-- Legacy H2 counterpart of V270__close_construction_stage_loop.sql.
ALTER TABLE qs_inspection_record ADD COLUMN IF NOT EXISTS wbs_task_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_qs_inspection_wbs ON qs_inspection_record (tenant_id, wbs_task_id);
ALTER TABLE qs_inspection_record ADD CONSTRAINT fk_qs_inspection_wbs FOREIGN KEY (tenant_id, wbs_task_id) REFERENCES project_wbs_task (tenant_id, id);

ALTER TABLE sub_task ADD COLUMN IF NOT EXISTS wbs_task_id BIGINT;
CREATE UNIQUE INDEX IF NOT EXISTS uk_sub_task_tenant_id ON sub_task (tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_sub_task_wbs ON sub_task (tenant_id, wbs_task_id);
ALTER TABLE sub_task ADD CONSTRAINT fk_sub_task_wbs FOREIGN KEY (tenant_id, wbs_task_id) REFERENCES project_wbs_task (tenant_id, id);
ALTER TABLE sub_measure ADD CONSTRAINT fk_sub_measure_sub_task FOREIGN KEY (tenant_id, sub_task_id) REFERENCES sub_task (tenant_id, id);

ALTER TABLE mat_requisition_item ADD COLUMN IF NOT EXISTS wbs_task_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_mat_requisition_item_wbs ON mat_requisition_item (tenant_id, wbs_task_id);
ALTER TABLE mat_requisition_item ADD CONSTRAINT fk_mat_requisition_item_wbs FOREIGN KEY (tenant_id, wbs_task_id) REFERENCES project_wbs_task (tenant_id, id);

ALTER TABLE mat_stock_txn ADD COLUMN IF NOT EXISTS wbs_task_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_mat_stock_txn_wbs ON mat_stock_txn (tenant_id, wbs_task_id);
ALTER TABLE mat_stock_txn ADD CONSTRAINT fk_mat_stock_txn_wbs FOREIGN KEY (tenant_id, wbs_task_id) REFERENCES project_wbs_task (tenant_id, id);

ALTER TABLE production_measurement_line ADD COLUMN IF NOT EXISTS wbs_task_id BIGINT;
DROP INDEX IF EXISTS uk_production_measure_item;
DROP INDEX IF EXISTS uk_production_measure_change;
CREATE UNIQUE INDEX IF NOT EXISTS uk_production_measure_item_wbs
    ON production_measurement_line (tenant_id, measurement_id, contract_item_id, wbs_task_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_production_measure_change_wbs
    ON production_measurement_line (tenant_id, measurement_id, contract_change_id, wbs_task_id);
CREATE INDEX IF NOT EXISTS idx_production_measure_line_wbs
    ON production_measurement_line (tenant_id, wbs_task_id);
ALTER TABLE production_measurement_line ADD CONSTRAINT fk_production_measure_line_wbs FOREIGN KEY (tenant_id, wbs_task_id) REFERENCES project_wbs_task (tenant_id, id);

ALTER TABLE cost_item ADD COLUMN IF NOT EXISTS wbs_task_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_cost_item_wbs ON cost_item (tenant_id, wbs_task_id);
ALTER TABLE cost_item ADD CONSTRAINT fk_cost_item_wbs FOREIGN KEY (tenant_id, wbs_task_id) REFERENCES project_wbs_task (tenant_id, id);

UPDATE sys_dict_data SET order_num=6 WHERE tenant_id=0 AND id=100104;
UPDATE sys_dict_data SET order_num=7 WHERE tenant_id=0 AND id=100105;
UPDATE sys_dict_data SET order_num=8 WHERE tenant_id=0 AND id=100103;

INSERT INTO sys_dict_data
    (id, tenant_id, dict_type_id, dict_label, dict_value, css_class, list_class,
     order_num, status, created_at, updated_at)
SELECT * FROM (VALUES
    (2700101, 0, 1001, '完工收尾', 'COMPLETION', NULL, 'warning', 4, 'ENABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2700102, 0, 1001, '质保阶段', 'WARRANTY', NULL, 'info', 5, 'ENABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
) AS seed(id, tenant_id, dict_type_id, dict_label, dict_value, css_class, list_class, order_num, status, created_at, updated_at)
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data existing WHERE existing.tenant_id=seed.tenant_id AND existing.id=seed.id);
