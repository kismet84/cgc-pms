ALTER TABLE pay_application
    ADD COLUMN integrity_version VARCHAR(32) NOT NULL DEFAULT 'LEGACY_UNVERIFIED';

CREATE INDEX idx_pay_application_integrity
    ON pay_application(tenant_id, integrity_version, approval_status);
