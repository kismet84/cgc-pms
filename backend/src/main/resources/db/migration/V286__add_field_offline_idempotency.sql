-- Offline retries remain server-authoritative and idempotent per tenant and actor.

ALTER TABLE site_daily_log
    ADD COLUMN client_request_id VARCHAR(64) NULL AFTER on_site_headcount,
    ADD COLUMN request_hash CHAR(64) NULL AFTER client_request_id,
    ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER request_hash,
    ADD UNIQUE KEY uk_site_daily_client_request (tenant_id, created_by, client_request_id);

ALTER TABLE qs_issue
    ADD COLUMN client_request_id VARCHAR(64) NULL AFTER due_date,
    ADD COLUMN request_hash CHAR(64) NULL AFTER client_request_id,
    ADD UNIQUE KEY uk_qs_issue_client_request (tenant_id, created_by, client_request_id);

ALTER TABLE qs_rectification
    ADD COLUMN client_request_id VARCHAR(64) NULL AFTER planned_complete_date,
    ADD COLUMN request_hash CHAR(64) NULL AFTER client_request_id,
    ADD UNIQUE KEY uk_qs_rectification_client_request (tenant_id, created_by, client_request_id);
