CREATE TABLE receivable_adjustment (
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL DEFAULT 0,receivable_id BIGINT NOT NULL,adjustment_type VARCHAR(32) NOT NULL,
 amount DECIMAL(18,2) NOT NULL,reason VARCHAR(500) NOT NULL,idempotency_key VARCHAR(128) NOT NULL,status VARCHAR(32) NOT NULL,
 created_by BIGINT NULL,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(id),UNIQUE KEY uk_receivable_adjustment_key(tenant_id,idempotency_key),
 CONSTRAINT fk_receivable_adjustment_ar FOREIGN KEY(receivable_id) REFERENCES account_receivable(id) ON DELETE RESTRICT,
 CONSTRAINT ck_receivable_adjustment_amount CHECK(amount>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE collection_reversal (
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL DEFAULT 0,collection_id BIGINT NOT NULL,idempotency_key VARCHAR(128) NOT NULL,
 reason VARCHAR(500) NOT NULL,status VARCHAR(32) NOT NULL,created_by BIGINT NULL,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(id),UNIQUE KEY uk_collection_reversal_key(tenant_id,idempotency_key),UNIQUE KEY uk_collection_reversal_record(tenant_id,collection_id),
 CONSTRAINT fk_collection_reversal_record FOREIGN KEY(collection_id) REFERENCES collection_record(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE collection_schedule (
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL DEFAULT 0,project_id BIGINT NOT NULL,contract_id BIGINT NOT NULL,
 receivable_id BIGINT NULL,planned_date DATE NOT NULL,planned_amount DECIMAL(18,2) NOT NULL,collected_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
 reminder_days INT NOT NULL DEFAULT 7,status VARCHAR(32) NOT NULL DEFAULT 'PLANNED',note VARCHAR(500) NOT NULL,version INT NOT NULL DEFAULT 0,
 created_by BIGINT NULL,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_by BIGINT NULL,updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 PRIMARY KEY(id),KEY idx_collection_schedule_due(tenant_id,status,planned_date),
 CONSTRAINT fk_collection_schedule_project FOREIGN KEY(project_id) REFERENCES pm_project(id) ON DELETE RESTRICT,
 CONSTRAINT fk_collection_schedule_contract FOREIGN KEY(contract_id) REFERENCES ct_contract(id) ON DELETE RESTRICT,
 CONSTRAINT fk_collection_schedule_receivable FOREIGN KEY(receivable_id) REFERENCES account_receivable(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE revenue_reconciliation_run (
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL DEFAULT 0,business_date DATE NOT NULL,status VARCHAR(32) NOT NULL,
 issue_count INT NOT NULL DEFAULT 0,started_at DATETIME NOT NULL,finished_at DATETIME NULL,created_by BIGINT NULL,
 PRIMARY KEY(id),UNIQUE KEY uk_revenue_recon_day(tenant_id,business_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE revenue_reconciliation_issue (
 id BIGINT NOT NULL,tenant_id BIGINT NOT NULL DEFAULT 0,run_id BIGINT NOT NULL,dimension_type VARCHAR(64) NOT NULL,
 business_id BIGINT NULL,issue_code VARCHAR(64) NOT NULL,expected_amount DECIMAL(18,2) NULL,actual_amount DECIMAL(18,2) NULL,
 status VARCHAR(32) NOT NULL DEFAULT 'OPEN',detail VARCHAR(1000) NOT NULL,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(id),KEY idx_revenue_recon_issue(tenant_id,run_id,status),
 CONSTRAINT fk_revenue_recon_issue_run FOREIGN KEY(run_id) REFERENCES revenue_reconciliation_run(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
