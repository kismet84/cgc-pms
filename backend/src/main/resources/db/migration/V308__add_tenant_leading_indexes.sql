-- 租户隔离索引补齐：租户插件给每条查询追加 tenant_id = ?，
-- 但以下表的索引都不以 tenant_id 开头，多租户下会退化为按业务列扫描再过滤租户。
-- 每张表只补一条与既有热点访问路径同列序的索引，原索引保留（避免破坏现有查询计划）。

ALTER TABLE bid_cost
    ADD KEY idx_bid_cost_tenant_project (tenant_id, project_id);

ALTER TABLE bid_deposit
    ADD KEY idx_bid_deposit_tenant_bid (tenant_id, bid_cost_id);

ALTER TABLE ct_contract_item
    ADD KEY idx_ct_contract_item_tenant_contract (tenant_id, contract_id, sort_order);

ALTER TABLE ct_contract_payment_term
    ADD KEY idx_ct_payment_term_tenant_contract (tenant_id, contract_id, sort_order);

ALTER TABLE overhead_allocation_record
    ADD KEY idx_alloc_tenant_date (tenant_id, allocation_date);

ALTER TABLE pay_application_basis
    ADD KEY idx_pab_tenant_application (tenant_id, pay_application_id);

ALTER TABLE revenue_import_row
    ADD KEY idx_revenue_import_row_tenant_batch (tenant_id, batch_id, row_no);

ALTER TABLE stl_settlement_item
    ADD KEY idx_stl_si_tenant_settlement (tenant_id, settlement_id);

ALTER TABLE sys_dict_data
    ADD KEY idx_sys_dict_data_tenant_type (tenant_id, dict_type_id, order_num);

ALTER TABLE var_order_item
    ADD KEY idx_var_oi_tenant_order (tenant_id, var_order_id);

ALTER TABLE wf_node_instance
    ADD KEY idx_wf_node_instance_tenant_instance (tenant_id, instance_id, round_no, node_order);

ALTER TABLE wf_record
    ADD KEY idx_wf_record_tenant_instance (tenant_id, instance_id, round_no, created_at);

ALTER TABLE wf_task
    ADD KEY idx_wf_task_tenant_todo (tenant_id, approver_id, task_status, received_at);
