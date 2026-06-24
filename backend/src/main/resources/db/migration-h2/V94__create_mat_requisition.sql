-- V94__create_mat_requisition.sql (H2 version)
-- 领料申请主表 + 明细表 — H2 兼容语法

-- 主表
CREATE TABLE mat_requisition (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL,
    contract_id BIGINT NULL,
    partner_id BIGINT NULL,
    requisition_code VARCHAR(50) NOT NULL,
    requisition_date DATE NOT NULL,
    warehouse_id BIGINT NOT NULL,
    requisitioner_id BIGINT NULL,
    approval_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    total_amount DECIMAL(18,4) NULL DEFAULT 0.0000,
    stock_out_flag SMALLINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL
);
CREATE UNIQUE INDEX uk_req_code ON mat_requisition (tenant_id, requisition_code);
CREATE INDEX idx_mr_tenant ON mat_requisition (tenant_id);
CREATE INDEX idx_mr_project ON mat_requisition (project_id);
CREATE INDEX idx_mr_contract ON mat_requisition (contract_id);
CREATE INDEX idx_mr_warehouse ON mat_requisition (warehouse_id);
CREATE INDEX idx_mr_approval_status ON mat_requisition (approval_status);

-- 明细表
CREATE TABLE mat_requisition_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    requisition_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    quantity DECIMAL(18,4) NOT NULL DEFAULT 0.0000,
    unit_price DECIMAL(18,4) NULL DEFAULT 0.0000,
    amount DECIMAL(18,4) NULL DEFAULT 0.0000,
    use_location VARCHAR(200) NULL,
    batch_no VARCHAR(100) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL
);
CREATE INDEX idx_mri_tenant ON mat_requisition_item (tenant_id);
CREATE INDEX idx_mri_requisition ON mat_requisition_item (requisition_id);
CREATE INDEX idx_mri_material ON mat_requisition_item (material_id);
