ALTER TABLE var_order_item ADD COLUMN wbs_task_id BIGINT NULL;
CREATE INDEX idx_var_order_item_wbs ON var_order_item (tenant_id, wbs_task_id);
ALTER TABLE var_order_item ADD CONSTRAINT fk_var_order_item_wbs
    FOREIGN KEY (wbs_task_id) REFERENCES project_wbs_task (id);

ALTER TABLE revenue_audit_event ADD COLUMN command_key VARCHAR(128) NULL;
CREATE UNIQUE INDEX uk_revenue_audit_command ON revenue_audit_event
    (tenant_id, event_type, business_type, business_id, command_key);

ALTER TABLE finance_audit_event ADD COLUMN command_key VARCHAR(128) NULL;
CREATE UNIQUE INDEX uk_finance_audit_command ON finance_audit_event
    (tenant_id, event_type, business_type, business_id, command_key);
