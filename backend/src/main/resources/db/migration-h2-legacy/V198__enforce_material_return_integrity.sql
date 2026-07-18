ALTER TABLE pm_project ADD CONSTRAINT uk_pm_project_tenant_id UNIQUE (tenant_id,id);
ALTER TABLE ct_contract ADD CONSTRAINT uk_ct_contract_tenant_id UNIQUE (tenant_id,id);
ALTER TABLE mat_warehouse ADD CONSTRAINT uk_mat_warehouse_tenant_id UNIQUE (tenant_id,id);
ALTER TABLE mat_requisition ADD CONSTRAINT uk_mat_requisition_tenant_id UNIQUE (tenant_id,id);
ALTER TABLE mat_requisition_item ADD CONSTRAINT uk_mat_requisition_item_tenant_id UNIQUE (tenant_id,id);
ALTER TABLE mat_stock_txn ADD CONSTRAINT uk_mat_stock_txn_tenant_id UNIQUE (tenant_id,id);
ALTER TABLE cost_item ADD CONSTRAINT uk_cost_item_tenant_id UNIQUE (tenant_id,id);
ALTER TABLE md_material ADD CONSTRAINT uk_md_material_tenant_id UNIQUE (tenant_id,id);
ALTER TABLE mat_material_return ADD CONSTRAINT uk_material_return_tenant_id UNIQUE (tenant_id,id);

ALTER TABLE mat_material_return ADD CONSTRAINT fk_material_return_project FOREIGN KEY (tenant_id,project_id) REFERENCES pm_project (tenant_id,id) ON DELETE RESTRICT;
ALTER TABLE mat_material_return ADD CONSTRAINT fk_material_return_contract FOREIGN KEY (tenant_id,contract_id) REFERENCES ct_contract (tenant_id,id) ON DELETE RESTRICT;
ALTER TABLE mat_material_return ADD CONSTRAINT fk_material_return_warehouse FOREIGN KEY (tenant_id,warehouse_id) REFERENCES mat_warehouse (tenant_id,id) ON DELETE RESTRICT;
ALTER TABLE mat_material_return ADD CONSTRAINT fk_material_return_requisition FOREIGN KEY (tenant_id,requisition_id) REFERENCES mat_requisition (tenant_id,id) ON DELETE RESTRICT;
ALTER TABLE mat_material_return ADD CONSTRAINT ck_material_return_status CHECK (status IN ('CONFIRMED','REVERSED'));
ALTER TABLE mat_material_return ADD CONSTRAINT ck_material_return_amount CHECK (total_amount >= 0);

ALTER TABLE mat_material_return_item ADD CONSTRAINT uk_material_return_item_source UNIQUE (tenant_id,return_id,requisition_item_id,original_stock_txn_id);
ALTER TABLE mat_material_return_item ADD CONSTRAINT fk_material_return_item_header FOREIGN KEY (tenant_id,return_id) REFERENCES mat_material_return (tenant_id,id) ON DELETE RESTRICT;
ALTER TABLE mat_material_return_item ADD CONSTRAINT fk_material_return_item_requisition FOREIGN KEY (tenant_id,requisition_item_id) REFERENCES mat_requisition_item (tenant_id,id) ON DELETE RESTRICT;
ALTER TABLE mat_material_return_item ADD CONSTRAINT fk_material_return_item_stock_txn FOREIGN KEY (tenant_id,original_stock_txn_id) REFERENCES mat_stock_txn (tenant_id,id) ON DELETE RESTRICT;
ALTER TABLE mat_material_return_item ADD CONSTRAINT fk_material_return_item_cost FOREIGN KEY (tenant_id,original_cost_item_id) REFERENCES cost_item (tenant_id,id) ON DELETE RESTRICT;
ALTER TABLE mat_material_return_item ADD CONSTRAINT fk_material_return_item_material FOREIGN KEY (tenant_id,material_id) REFERENCES md_material (tenant_id,id) ON DELETE RESTRICT;
ALTER TABLE mat_material_return_item ADD CONSTRAINT ck_material_return_item_quantity CHECK (quantity > 0);
ALTER TABLE mat_material_return_item ADD CONSTRAINT ck_material_return_item_value CHECK (unit_cost >= 0 AND amount >= 0);
