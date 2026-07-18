ALTER TABLE mat_purchase_request
    ADD UNIQUE KEY uk_mat_pr_code (tenant_id, request_code, deleted_flag);
