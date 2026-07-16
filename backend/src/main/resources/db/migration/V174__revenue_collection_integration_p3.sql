ALTER TABLE bank_receipt ADD COLUMN collection_record_id BIGINT NULL;
ALTER TABLE bank_receipt ADD CONSTRAINT fk_bank_receipt_collection FOREIGN KEY(collection_record_id) REFERENCES collection_record(id) ON DELETE RESTRICT;
CREATE UNIQUE INDEX uk_bank_receipt_collection ON bank_receipt(tenant_id,collection_record_id);
CREATE TABLE collection_forecast(
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL DEFAULT 0,project_id BIGINT NOT NULL,contract_id BIGINT NULL,forecast_date DATE NOT NULL,
 scenario VARCHAR(32) NOT NULL,expected_amount DECIMAL(18,2) NOT NULL,confidence DECIMAL(5,4) NOT NULL DEFAULT 1,
 source_type VARCHAR(32) NOT NULL,source_id BIGINT NULL,status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',version INT NOT NULL DEFAULT 0,
 created_by BIGINT NULL,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(id),KEY idx_collection_forecast(tenant_id,scenario,forecast_date),
 CONSTRAINT fk_collection_forecast_project FOREIGN KEY(project_id) REFERENCES pm_project(id) ON DELETE RESTRICT,
 CONSTRAINT fk_collection_forecast_contract FOREIGN KEY(contract_id) REFERENCES ct_contract(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE customer_credit_profile(
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL DEFAULT 0,customer_id BIGINT NOT NULL,credit_limit DECIMAL(18,2) NOT NULL DEFAULT 0,
 risk_level VARCHAR(16) NOT NULL DEFAULT 'NORMAL',dso_days INT NOT NULL DEFAULT 0,overdue_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
 score DECIMAL(8,2) NOT NULL DEFAULT 100,formula_version VARCHAR(64) NOT NULL DEFAULT 'CUSTOMER_CREDIT_V1',refreshed_at DATETIME NOT NULL,
 PRIMARY KEY(id),UNIQUE KEY uk_customer_credit(tenant_id,customer_id),
 CONSTRAINT fk_customer_credit_partner FOREIGN KEY(customer_id) REFERENCES md_partner(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE revenue_external_sync(
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL DEFAULT 0,endpoint_id BIGINT NOT NULL,business_type VARCHAR(64) NOT NULL,business_id BIGINT NOT NULL,
 message_id BIGINT NULL,sync_status VARCHAR(32) NOT NULL,idempotency_key VARCHAR(128) NOT NULL,last_error VARCHAR(1000) NULL,
 synced_at DATETIME NULL,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(id),UNIQUE KEY uk_revenue_external_sync(tenant_id,endpoint_id,idempotency_key),
 CONSTRAINT fk_revenue_sync_endpoint FOREIGN KEY(endpoint_id) REFERENCES finance_integration_endpoint(id) ON DELETE RESTRICT,
 CONSTRAINT fk_revenue_sync_message FOREIGN KEY(message_id) REFERENCES finance_integration_message(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
