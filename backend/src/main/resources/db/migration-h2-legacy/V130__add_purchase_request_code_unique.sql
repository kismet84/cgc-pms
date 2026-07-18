CREATE UNIQUE INDEX IF NOT EXISTS uk_mat_pr_code
    ON mat_purchase_request (tenant_id, request_code, deleted_flag);
