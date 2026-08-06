-- Keep legacy Spring fixtures compatible with current field-delivery entities.

ALTER TABLE site_daily_log ADD COLUMN client_request_id VARCHAR(64) NULL;
ALTER TABLE site_daily_log ADD COLUMN request_hash CHAR(64) NULL;
ALTER TABLE site_daily_log ADD COLUMN version INT DEFAULT 0 NOT NULL;
CREATE UNIQUE INDEX uk_site_daily_client_request ON site_daily_log(tenant_id, created_by, client_request_id);

ALTER TABLE qs_issue ADD COLUMN client_request_id VARCHAR(64) NULL;
ALTER TABLE qs_issue ADD COLUMN request_hash CHAR(64) NULL;
CREATE UNIQUE INDEX uk_qs_issue_client_request ON qs_issue(tenant_id, created_by, client_request_id);

ALTER TABLE qs_rectification ADD COLUMN client_request_id VARCHAR(64) NULL;
ALTER TABLE qs_rectification ADD COLUMN request_hash CHAR(64) NULL;
CREATE UNIQUE INDEX uk_qs_rectification_client_request ON qs_rectification(tenant_id, created_by, client_request_id);
