ALTER TABLE mat_warehouse
    ADD CONSTRAINT uk_mat_warehouse_code
        UNIQUE (tenant_id, project_id, warehouse_code, deleted_flag);
