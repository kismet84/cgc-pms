-- V94__create_mat_requisition.sql
-- 领料申请主表 + 明细表

-- 主表
CREATE TABLE mat_requisition (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '领料申请ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    contract_id BIGINT NULL COMMENT '关联合同ID',
    partner_id BIGINT NULL COMMENT '合作方/供应商ID',
    requisition_code VARCHAR(50) NOT NULL COMMENT '申请编号 REQ-yyyyMMdd-XXX',
    requisition_date DATE NOT NULL COMMENT '申请日期',
    warehouse_id BIGINT NOT NULL COMMENT '仓库ID',
    requisitioner_id BIGINT NULL COMMENT '领料人ID',
    approval_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT' COMMENT '审批状态：DRAFT/APPROVING/APPROVED/REJECTED',
    total_amount DECIMAL(18,4) NULL DEFAULT 0.0000 COMMENT '总金额（明细金额合计）',
    stock_out_flag TINYINT NOT NULL DEFAULT 0 COMMENT '出库标记：0未出库，1已出库',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
    updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    remark VARCHAR(500) NULL COMMENT '备注',
    UNIQUE KEY uk_req_code (tenant_id, requisition_code),
    KEY idx_mr_tenant (tenant_id),
    KEY idx_mr_project (project_id),
    KEY idx_mr_contract (contract_id),
    KEY idx_mr_warehouse (warehouse_id),
    KEY idx_mr_approval_status (approval_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='领料申请主表';

-- 明细表
CREATE TABLE mat_requisition_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '领料申请明细ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    requisition_id BIGINT NOT NULL COMMENT '领料申请ID',
    material_id BIGINT NOT NULL COMMENT '物料ID',
    quantity DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT '申请数量',
    unit_price DECIMAL(18,4) NULL DEFAULT 0.0000 COMMENT '参考单价',
    amount DECIMAL(18,4) NULL DEFAULT 0.0000 COMMENT '金额（quantity × unit_price）',
    use_location VARCHAR(200) NULL COMMENT '使用部位/用途',
    batch_no VARCHAR(100) NULL COMMENT '批次号',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
    updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    remark VARCHAR(500) NULL COMMENT '备注',
    KEY idx_mri_tenant (tenant_id),
    KEY idx_mri_requisition (requisition_id),
    KEY idx_mri_material (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='领料申请明细表';
