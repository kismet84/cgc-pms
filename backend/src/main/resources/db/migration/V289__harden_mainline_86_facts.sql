ALTER TABLE var_order_item
    ADD COLUMN wbs_task_id BIGINT NULL COMMENT '关联当前项目生效WBS任务' AFTER cost_subject_id,
    ADD KEY idx_var_order_item_wbs (tenant_id, wbs_task_id),
    ADD CONSTRAINT fk_var_order_item_wbs FOREIGN KEY (wbs_task_id)
        REFERENCES project_wbs_task (id) ON DELETE RESTRICT;

ALTER TABLE revenue_audit_event
    ADD COLUMN command_key VARCHAR(128) NULL AFTER business_id,
    ADD UNIQUE KEY uk_revenue_audit_command
        (tenant_id, event_type, business_type, business_id, command_key);

ALTER TABLE finance_audit_event
    ADD COLUMN command_key VARCHAR(128) NULL AFTER business_id,
    ADD UNIQUE KEY uk_finance_audit_command
        (tenant_id, event_type, business_type, business_id, command_key);
