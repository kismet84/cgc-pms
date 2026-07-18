ALTER TABLE sys_file
    ADD COLUMN virus_scan_status VARCHAR(20) NOT NULL DEFAULT 'NOT_SCANNED' COMMENT '病毒扫描状态' AFTER bucket_name,
    ADD COLUMN virus_scan_detail VARCHAR(255) NULL COMMENT '病毒特征或失败摘要' AFTER virus_scan_status,
    ADD COLUMN virus_scanned_at DATETIME(3) NULL COMMENT '病毒扫描完成时间' AFTER virus_scan_detail;

CREATE INDEX idx_sys_file_virus_scan_status
    ON sys_file (tenant_id, virus_scan_status, deleted_flag);
