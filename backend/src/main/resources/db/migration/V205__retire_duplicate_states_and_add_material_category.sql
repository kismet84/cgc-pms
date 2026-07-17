SET NAMES utf8mb4;

-- approval_status is the sole workflow state authority; legacy API status is now derived.
UPDATE expense_application SET status = approval_status WHERE NOT (status <=> approval_status);
ALTER TABLE expense_application DROP COLUMN status;

UPDATE stl_settlement SET status = approval_status WHERE NOT (status <=> approval_status);
ALTER TABLE stl_settlement DROP COLUMN status;

-- collection_reversal is the sole reversal fact; no runtime code reads the old self-reference.
ALTER TABLE collection_record DROP FOREIGN KEY fk_collection_reversal;
ALTER TABLE collection_record DROP COLUMN reversal_of_id;

CREATE TABLE md_material_category (
    id BIGINT NOT NULL COMMENT '材料分类ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    parent_id BIGINT NULL COMMENT '上级分类ID',
    category_code VARCHAR(64) NOT NULL COMMENT '分类编码，租户内永久唯一',
    category_name VARCHAR(128) NOT NULL COMMENT '分类名称',
    level_no INT NOT NULL DEFAULT 1 COMMENT '树层级，从1开始',
    order_num INT NOT NULL DEFAULT 0 COMMENT '同级排序号',
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLE' COMMENT '状态：ENABLE/DISABLE',
    created_by BIGINT NULL COMMENT '创建人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '更新人',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
    remark VARCHAR(500) NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_material_category_tenant_id (tenant_id,id),
    UNIQUE KEY uk_material_category_code (tenant_id,category_code),
    KEY idx_material_category_parent (tenant_id,parent_id,order_num),
    CONSTRAINT fk_material_category_parent FOREIGN KEY (tenant_id,parent_id)
        REFERENCES md_material_category(tenant_id,id) ON DELETE RESTRICT,
    CONSTRAINT ck_material_category_level CHECK(level_no >= 1),
    CONSTRAINT ck_material_category_status CHECK(status IN ('ENABLE','DISABLE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='材料分类主数据';

ALTER TABLE md_material
    ADD CONSTRAINT fk_md_material_category FOREIGN KEY (tenant_id,category_id)
        REFERENCES md_material_category(tenant_id,id) ON DELETE RESTRICT;
