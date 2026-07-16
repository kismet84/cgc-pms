CREATE TABLE revenue_dashboard_snapshot(
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL DEFAULT 0,project_id BIGINT NOT NULL,snapshot_date DATE NOT NULL,formula_version VARCHAR(64) NOT NULL,
 confirmed_revenue DECIMAL(18,2) NOT NULL,settled_amount DECIMAL(18,2) NOT NULL,receivable_amount DECIMAL(18,2) NOT NULL,
 outstanding_amount DECIMAL(18,2) NOT NULL,overdue_amount DECIMAL(18,2) NOT NULL,collected_amount DECIMAL(18,2) NOT NULL,
 invoiced_amount DECIMAL(18,2) NOT NULL,collection_rate DECIMAL(12,6) NOT NULL,refreshed_at DATETIME NOT NULL,refresh_mode VARCHAR(32) NOT NULL,
 PRIMARY KEY(id),UNIQUE KEY uk_revenue_snapshot(tenant_id,project_id,snapshot_date),
 CONSTRAINT fk_revenue_snapshot_project FOREIGN KEY(project_id) REFERENCES pm_project(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE sales_invoice_review(
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL DEFAULT 0,invoice_id BIGINT NOT NULL,raw_result_json LONGTEXT NOT NULL,
 confidence DECIMAL(5,4) NOT NULL,comparison_json LONGTEXT NULL,review_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
 reviewer_id BIGINT NULL,reviewed_at DATETIME NULL,review_note VARCHAR(500) NULL,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(id),KEY idx_sales_invoice_review(tenant_id,review_status,confidence),
 CONSTRAINT fk_sales_invoice_review_invoice FOREIGN KEY(invoice_id) REFERENCES sales_invoice(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE revenue_import_batch(
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL DEFAULT 0,import_type VARCHAR(32) NOT NULL,project_id BIGINT NOT NULL,file_name VARCHAR(255) NOT NULL,
 file_hash VARCHAR(64) NOT NULL,status VARCHAR(32) NOT NULL,total_rows INT NOT NULL,valid_rows INT NOT NULL,invalid_rows INT NOT NULL,
 diff_summary_json LONGTEXT NULL,created_by BIGINT NULL,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,applied_at DATETIME NULL,
 PRIMARY KEY(id),UNIQUE KEY uk_revenue_import_hash(tenant_id,import_type,project_id,file_hash),
 CONSTRAINT fk_revenue_import_project FOREIGN KEY(project_id) REFERENCES pm_project(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE revenue_import_row(
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL DEFAULT 0,batch_id BIGINT NOT NULL,row_no INT NOT NULL,input_json LONGTEXT NOT NULL,
 diff_json LONGTEXT NULL,validation_status VARCHAR(32) NOT NULL,validation_message VARCHAR(1000) NULL,
 PRIMARY KEY(id),UNIQUE KEY uk_revenue_import_row(batch_id,row_no),
 CONSTRAINT fk_revenue_import_row_batch FOREIGN KEY(batch_id) REFERENCES revenue_import_batch(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE revenue_audit_event(
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL DEFAULT 0,event_type VARCHAR(64) NOT NULL,business_type VARCHAR(64) NOT NULL,
 business_id BIGINT NOT NULL,project_id BIGINT NULL,operator_id BIGINT NULL,event_at DATETIME NOT NULL,archive_bucket VARCHAR(32) NOT NULL DEFAULT 'HOT',
 payload_json LONGTEXT NOT NULL,payload_hash VARCHAR(64) NOT NULL,
 PRIMARY KEY(id),KEY idx_revenue_audit_search(tenant_id,business_type,business_id,event_at),KEY idx_revenue_audit_archive(tenant_id,archive_bucket,event_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
