ALTER TABLE md_material
    ADD COLUMN tax_inclusive_info_price DECIMAL(19,6) NULL COMMENT '当前含税信息价',
    ADD COLUMN info_price_period CHAR(7) NULL COMMENT '当前信息价月份 YYYY-MM',
    ADD COLUMN info_price_source VARCHAR(255) NULL COMMENT '当前信息价来源',
    ADD COLUMN info_price_verification_status VARCHAR(32) NULL COMMENT '当前信息价校核状态',
    ADD COLUMN info_price_external_row_key VARCHAR(128) NULL COMMENT '外部行稳定标识',
    ADD COLUMN info_price_review_required TINYINT NOT NULL DEFAULT 0 COMMENT '是否需要人工复核';

ALTER TABLE md_material
    ADD CONSTRAINT ck_md_material_info_price
        CHECK (tax_inclusive_info_price IS NULL OR tax_inclusive_info_price > 0),
    ADD CONSTRAINT ck_md_material_info_period
        CHECK (info_price_period IS NULL OR info_price_period REGEXP '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    ADD CONSTRAINT ck_md_material_info_review
        CHECK (info_price_review_required IN (0, 1));

CREATE INDEX idx_md_material_info_match
    ON md_material (tenant_id, material_name, deleted_flag);

CREATE INDEX idx_mat_ri_purchase_price
    ON mat_receipt_item (tenant_id, material_id, deleted_flag, receipt_id, id);

CREATE INDEX idx_mat_receipt_purchase_price
    ON mat_receipt (tenant_id, approval_status, deleted_flag, receipt_date, id);
