ALTER TABLE md_material ADD COLUMN IF NOT EXISTS tax_inclusive_info_price DECIMAL(19,6) NULL;
ALTER TABLE md_material ADD COLUMN IF NOT EXISTS info_price_period CHAR(7) NULL;
ALTER TABLE md_material ADD COLUMN IF NOT EXISTS info_price_source VARCHAR(255) NULL;
ALTER TABLE md_material ADD COLUMN IF NOT EXISTS info_price_verification_status VARCHAR(32) NULL;
ALTER TABLE md_material ADD COLUMN IF NOT EXISTS info_price_external_row_key VARCHAR(128) NULL;
ALTER TABLE md_material ADD COLUMN IF NOT EXISTS info_price_review_required TINYINT DEFAULT 0 NOT NULL;

ALTER TABLE md_material ADD CONSTRAINT IF NOT EXISTS ck_md_material_info_price
    CHECK (tax_inclusive_info_price IS NULL OR tax_inclusive_info_price > 0);
ALTER TABLE md_material ADD CONSTRAINT IF NOT EXISTS ck_md_material_info_period
    CHECK (info_price_period IS NULL OR REGEXP_LIKE(info_price_period, '^[0-9]{4}-(0[1-9]|1[0-2])$'));
ALTER TABLE md_material ADD CONSTRAINT IF NOT EXISTS ck_md_material_info_review
    CHECK (info_price_review_required IN (0, 1));

CREATE INDEX IF NOT EXISTS idx_md_material_info_match
    ON md_material (tenant_id, material_name, deleted_flag);
CREATE INDEX IF NOT EXISTS idx_mat_ri_purchase_price
    ON mat_receipt_item (tenant_id, material_id, deleted_flag, receipt_id, id);
CREATE INDEX IF NOT EXISTS idx_mat_receipt_purchase_price
    ON mat_receipt (tenant_id, approval_status, deleted_flag, receipt_date, id);
