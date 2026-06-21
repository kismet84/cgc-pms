-- V82__fix_mat_stock_unique_with_deleted_flag.sql (H2 version)
-- Column backfill mirrors db/migration/V82. The mat_stock unique constraint is
-- rebuilt by Java migration com.cgcpms.common.migration.V82__fix_mat_stock_unique_with_deleted_flag
-- because H2 auto-generates the original UNIQUE constraint name.
ALTER TABLE accounting_entry_line ADD COLUMN IF NOT EXISTS created_by BIGINT NULL;
ALTER TABLE accounting_entry_line ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE accounting_entry_line ADD COLUMN IF NOT EXISTS updated_by BIGINT NULL;
ALTER TABLE accounting_entry_line ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE accounting_entry_line ADD COLUMN IF NOT EXISTS deleted_flag SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE accounting_entry_line ADD COLUMN IF NOT EXISTS remark VARCHAR(500) NULL;
