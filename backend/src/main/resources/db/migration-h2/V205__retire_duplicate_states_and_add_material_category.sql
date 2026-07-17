UPDATE expense_application SET status = approval_status WHERE status IS DISTINCT FROM approval_status;
ALTER TABLE expense_application DROP COLUMN status;

UPDATE stl_settlement SET status = approval_status WHERE status IS DISTINCT FROM approval_status;
ALTER TABLE stl_settlement DROP COLUMN status;

ALTER TABLE collection_record DROP CONSTRAINT fk_collection_reversal;
ALTER TABLE collection_record DROP COLUMN reversal_of_id;

CREATE TABLE md_material_category (
    id BIGINT NOT NULL,
    tenant_id BIGINT DEFAULT 0 NOT NULL,
    parent_id BIGINT,
    category_code VARCHAR(64) NOT NULL,
    category_name VARCHAR(128) NOT NULL,
    level_no INT DEFAULT 1 NOT NULL,
    order_num INT DEFAULT 0 NOT NULL,
    status VARCHAR(32) DEFAULT 'ENABLE' NOT NULL,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by BIGINT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted_flag TINYINT DEFAULT 0 NOT NULL,
    remark VARCHAR(500),
    PRIMARY KEY(id),
    CONSTRAINT uk_material_category_tenant_id UNIQUE(tenant_id,id),
    CONSTRAINT uk_material_category_code UNIQUE(tenant_id,category_code),
    CONSTRAINT fk_material_category_parent FOREIGN KEY(tenant_id,parent_id)
        REFERENCES md_material_category(tenant_id,id),
    CONSTRAINT ck_material_category_level CHECK(level_no >= 1),
    CONSTRAINT ck_material_category_status CHECK(status IN ('ENABLE','DISABLE'))
);
CREATE INDEX idx_material_category_parent ON md_material_category(tenant_id,parent_id,order_num);

ALTER TABLE md_material ADD CONSTRAINT fk_md_material_category FOREIGN KEY(tenant_id,category_id)
    REFERENCES md_material_category(tenant_id,id);
