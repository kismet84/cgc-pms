ALTER TABLE sys_file ADD COLUMN virus_scan_status VARCHAR(20) NOT NULL DEFAULT 'NOT_SCANNED';
ALTER TABLE sys_file ADD COLUMN virus_scan_detail VARCHAR(255);
ALTER TABLE sys_file ADD COLUMN virus_scanned_at TIMESTAMP(3);

CREATE INDEX idx_sys_file_virus_scan_status
    ON sys_file (tenant_id, virus_scan_status, deleted_flag);
