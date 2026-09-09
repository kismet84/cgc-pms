-- H2 mirror of V308: tenant-leading indexes for tables whose indexes did not start with tenant_id.

CREATE INDEX idx_bid_cost_tenant_project ON bid_cost (tenant_id, project_id);
CREATE INDEX idx_bid_deposit_tenant_bid ON bid_deposit (tenant_id, bid_cost_id);
CREATE INDEX idx_ct_contract_item_tenant_contract ON ct_contract_item (tenant_id, contract_id, sort_order);
CREATE INDEX idx_ct_payment_term_tenant_contract ON ct_contract_payment_term (tenant_id, contract_id, sort_order);
CREATE INDEX idx_alloc_tenant_date ON overhead_allocation_record (tenant_id, allocation_date);
CREATE INDEX idx_pab_tenant_application ON pay_application_basis (tenant_id, pay_application_id);
CREATE INDEX idx_revenue_import_row_tenant_batch ON revenue_import_row (tenant_id, batch_id, row_no);
CREATE INDEX idx_stl_si_tenant_settlement ON stl_settlement_item (tenant_id, settlement_id);
CREATE INDEX idx_sys_dict_data_tenant_type ON sys_dict_data (tenant_id, dict_type_id, order_num);
CREATE INDEX idx_var_oi_tenant_order ON var_order_item (tenant_id, var_order_id);
CREATE INDEX idx_wf_node_instance_tenant_instance ON wf_node_instance (tenant_id, instance_id, round_no, node_order);
CREATE INDEX idx_wf_record_tenant_instance ON wf_record (tenant_id, instance_id, round_no, created_at);
CREATE INDEX idx_wf_task_tenant_todo ON wf_task (tenant_id, approver_id, task_status, received_at);
