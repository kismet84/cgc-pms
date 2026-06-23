-- V12__init_phase2_tables.sql
-- 建筑工程总包项目全过程管理系统 - 第2阶段业务表（材料/分包/签证/结算/成本汇总）
-- 数据库：MySQL 8.0+
-- ID 策略：后端雪花 ID / ASSIGN_ID，数据库不使用 AUTO_INCREMENT

-- ----------------------------
-- 材料字典表
-- ----------------------------
CREATE TABLE IF NOT EXISTS md_material (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    material_code VARCHAR(64) NOT NULL,
    material_name VARCHAR(200) NOT NULL,
    category_id BIGINT NULL,
    specification VARCHAR(200) NULL,
    unit VARCHAR(20) NULL,
    brand VARCHAR(100) NULL,
    default_tax_rate DECIMAL(6,2) NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ENABLE',
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE (tenant_id, material_code),
    KEY idx_md_material_category (category_id)
);

-- ----------------------------
-- 采购订单表
-- ----------------------------
CREATE TABLE IF NOT EXISTS mat_purchase_order (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL,
    request_id BIGINT NULL,
    contract_id BIGINT NULL,
    partner_id BIGINT NULL,
    order_code VARCHAR(64) NOT NULL,
    order_type VARCHAR(50) NULL,
    order_date DATE NULL,
    delivery_date DATE NULL,
    total_amount DECIMAL(18,2) NULL,
    approval_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    order_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE (tenant_id, order_code),
    KEY idx_mat_po_project (project_id),
    KEY idx_mat_po_contract (contract_id),
    KEY idx_mat_po_partner (partner_id)
);

-- ----------------------------
-- 采购订单明细表
-- ----------------------------
CREATE TABLE IF NOT EXISTS mat_purchase_order_item (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    order_id BIGINT NOT NULL,
    project_id BIGINT NULL,
    material_id BIGINT NULL,
    material_name VARCHAR(200) NULL,
    specification VARCHAR(200) NULL,
    unit VARCHAR(20) NULL,
    quantity DECIMAL(18,4) NULL,
    unit_price DECIMAL(18,4) NULL,
    amount DECIMAL(18,2) NULL,
    received_quantity DECIMAL(18,4) NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    KEY idx_mat_poi_order (order_id),
    KEY idx_mat_poi_material (material_id)
);

-- ----------------------------
-- 材料验收表
-- ----------------------------
CREATE TABLE IF NOT EXISTS mat_receipt (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL,
    order_id BIGINT NULL,
    contract_id BIGINT NULL,
    partner_id BIGINT NULL,
    receipt_code VARCHAR(64) NOT NULL,
    receipt_date DATE NULL,
    warehouse_id BIGINT NULL,
    receiver_id BIGINT NULL,
    quality_status VARCHAR(50) NULL,
    total_amount DECIMAL(18,2) NULL,
    approval_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    cost_generated_flag SMALLINT NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE (tenant_id, receipt_code),
    KEY idx_mat_receipt_project (project_id),
    KEY idx_mat_receipt_order (order_id),
    KEY idx_mat_receipt_contract (contract_id)
);

-- ----------------------------
-- 材料验收明细表
-- ----------------------------
CREATE TABLE IF NOT EXISTS mat_receipt_item (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    receipt_id BIGINT NOT NULL,
    order_item_id BIGINT NULL,
    material_id BIGINT NULL,
    actual_quantity DECIMAL(18,4) NULL,
    qualified_quantity DECIMAL(18,4) NULL,
    unit_price DECIMAL(18,4) NULL,
    amount DECIMAL(18,2) NULL,
    use_location VARCHAR(200) NULL,
    batch_no VARCHAR(100) NULL,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    KEY idx_mat_ri_receipt (receipt_id),
    KEY idx_mat_ri_material (material_id)
);

-- ----------------------------
-- 分包任务表
-- ----------------------------
CREATE TABLE IF NOT EXISTS sub_task (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL,
    contract_id BIGINT NULL,
    partner_id BIGINT NULL,
    task_code VARCHAR(64) NOT NULL,
    task_name VARCHAR(200) NOT NULL,
    work_area VARCHAR(200) NULL,
    planned_start_date DATE NULL,
    planned_end_date DATE NULL,
    actual_start_date DATE NULL,
    actual_end_date DATE NULL,
    progress_percent DECIMAL(6,2) NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'NOT_STARTED',
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE (tenant_id, task_code),
    KEY idx_sub_task_project (project_id),
    KEY idx_sub_task_contract (contract_id)
);

-- ----------------------------
-- 分包计量表
-- ----------------------------
CREATE TABLE IF NOT EXISTS sub_measure (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL,
    contract_id BIGINT NULL,
    partner_id BIGINT NULL,
    measure_code VARCHAR(64) NOT NULL,
    measure_period VARCHAR(50) NULL,
    measure_date DATE NULL,
    reported_amount DECIMAL(18,2) NULL,
    approved_amount DECIMAL(18,2) NULL,
    deduction_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    net_amount DECIMAL(18,2) NULL,
    approval_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    cost_generated_flag SMALLINT NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE (tenant_id, measure_code),
    KEY idx_sub_measure_project (project_id),
    KEY idx_sub_measure_contract (contract_id)
);

-- ----------------------------
-- 分包计量明细表
-- ----------------------------
CREATE TABLE IF NOT EXISTS sub_measure_item (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    measure_id BIGINT NOT NULL,
    contract_item_id BIGINT NULL,
    item_name VARCHAR(200) NULL,
    unit VARCHAR(20) NULL,
    contract_quantity DECIMAL(18,4) NULL,
    current_quantity DECIMAL(18,4) NULL,
    cumulative_quantity DECIMAL(18,4) NULL,
    unit_price DECIMAL(18,4) NULL,
    amount DECIMAL(18,2) NULL,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    KEY idx_sub_mi_measure (measure_id)
);

-- ----------------------------
-- 变更签证表
-- ----------------------------
CREATE TABLE IF NOT EXISTS var_order (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL,
    contract_id BIGINT NULL,
    partner_id BIGINT NULL,
    var_code VARCHAR(64) NOT NULL,
    var_name VARCHAR(200) NOT NULL,
    var_type VARCHAR(50) NULL,
    direction VARCHAR(20) NULL,
    reported_amount DECIMAL(18,2) NULL,
    approved_amount DECIMAL(18,2) NULL,
    confirmed_amount DECIMAL(18,2) NULL,
    owner_confirm_flag SMALLINT NOT NULL DEFAULT 0,
    impact_days INT NOT NULL DEFAULT 0,
    approval_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    cost_generated_flag SMALLINT NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE (tenant_id, var_code),
    KEY idx_var_order_project (project_id),
    KEY idx_var_order_contract (contract_id)
);

-- ----------------------------
-- 变更签证明细表
-- ----------------------------
CREATE TABLE IF NOT EXISTS var_order_item (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    var_order_id BIGINT NOT NULL,
    item_name VARCHAR(200) NULL,
    unit VARCHAR(20) NULL,
    quantity DECIMAL(18,4) NULL,
    unit_price DECIMAL(18,4) NULL,
    amount DECIMAL(18,2) NULL,
    cost_subject_id BIGINT NULL,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    KEY idx_var_oi_order (var_order_id)
);

-- ----------------------------
-- 付款依据关联表
-- ----------------------------
CREATE TABLE IF NOT EXISTS pay_application_basis (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    pay_application_id BIGINT NOT NULL,
    basis_type VARCHAR(50) NOT NULL,
    basis_id BIGINT NOT NULL,
    basis_amount DECIMAL(18,2) NOT NULL,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    KEY idx_pab_application (pay_application_id),
    KEY idx_pab_basis (basis_type, basis_id)
);

-- ----------------------------
-- 动态成本汇总表
-- ----------------------------
CREATE TABLE IF NOT EXISTS cost_summary (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL,
    summary_date DATE NOT NULL,
    cost_subject_id BIGINT NULL,
    target_cost DECIMAL(18,2) NOT NULL DEFAULT 0,
    contract_locked_cost DECIMAL(18,2) NOT NULL DEFAULT 0,
    actual_cost DECIMAL(18,2) NOT NULL DEFAULT 0,
    paid_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    estimated_remaining_cost DECIMAL(18,2) NOT NULL DEFAULT 0,
    dynamic_cost DECIMAL(18,2) NOT NULL DEFAULT 0,
    contract_income DECIMAL(18,2) NOT NULL DEFAULT 0,
    expected_profit DECIMAL(18,2) NOT NULL DEFAULT 0,
    cost_deviation DECIMAL(18,2) NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE (project_id, summary_date, cost_subject_id),
    KEY idx_cs_project (project_id),
    KEY idx_cs_date (summary_date)
);

-- ----------------------------
-- 结算主表
-- ----------------------------
CREATE TABLE IF NOT EXISTS stl_settlement (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL,
    contract_id BIGINT NULL,
    partner_id BIGINT NULL,
    settlement_code VARCHAR(64) NOT NULL,
    settlement_type VARCHAR(50) NULL,
    contract_amount DECIMAL(18,2) NULL,
    change_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    measured_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    deduction_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    paid_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    final_amount DECIMAL(18,2) NULL,
    approval_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE (tenant_id, settlement_code),
    UNIQUE (tenant_id, contract_id),
    KEY idx_stl_project (project_id),
    KEY idx_stl_contract (contract_id)
);

-- ----------------------------
-- 结算明细表
-- ----------------------------
CREATE TABLE IF NOT EXISTS stl_settlement_item (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    settlement_id BIGINT NOT NULL,
    item_name VARCHAR(200) NULL,
    unit VARCHAR(20) NULL,
    quantity DECIMAL(18,4) NULL,
    unit_price DECIMAL(18,4) NULL,
    amount DECIMAL(18,2) NULL,
    cost_subject_id BIGINT NULL,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    KEY idx_stl_si_settlement (settlement_id)
);
