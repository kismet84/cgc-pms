-- H2 Schema for Phase 2+3 business tables (MySQL compatibility mode)
-- Auto-created by Spring Boot sql.init when application-local profile is active with H2

-- ====== V2: Contract Items & Payment Terms ======
CREATE TABLE IF NOT EXISTS ct_contract_item (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    contract_id BIGINT NOT NULL,
    item_code VARCHAR(64),
    item_name VARCHAR(200),
    item_spec VARCHAR(200),
    unit VARCHAR(20),
    quantity DECIMAL(18,4),
    unit_price DECIMAL(18,4),
    amount DECIMAL(18,2),
    tax_rate DECIMAL(6,2),
    tax_amount DECIMAL(18,2),
    amount_without_tax DECIMAL(18,2),
    sort_order INT DEFAULT 0,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    PRIMARY KEY (id),
    KEY idx_ct_ci_contract (contract_id)
);

CREATE TABLE IF NOT EXISTS ct_contract_payment_term (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    contract_id BIGINT NOT NULL,
    term_name VARCHAR(200),
    payment_ratio DECIMAL(6,4),
    payment_amount DECIMAL(18,2),
    trigger_condition VARCHAR(500),
    planned_date DATE,
    sort_order INT DEFAULT 0,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    PRIMARY KEY (id),
    KEY idx_ct_cpt_contract (contract_id)
);

-- ====== V4: Cost & Payment ======
CREATE TABLE IF NOT EXISTS cost_subject (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    parent_id BIGINT NOT NULL DEFAULT 0,
    subject_code VARCHAR(64) NOT NULL,
    subject_name VARCHAR(200) NOT NULL,
    subject_type VARCHAR(50),
    level INT NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'ENABLE',
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    PRIMARY KEY (id),
    UNIQUE (tenant_id, subject_code),
    KEY idx_cost_subject_parent (parent_id),
    KEY idx_cost_subject_type (subject_type)
);

CREATE TABLE IF NOT EXISTS cost_item (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    org_id BIGINT,
    project_id BIGINT NOT NULL,
    contract_id BIGINT,
    partner_id BIGINT,
    cost_subject_id BIGINT,
    cost_type VARCHAR(50) NOT NULL,
    amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    tax_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    amount_without_tax DECIMAL(18,2) NOT NULL DEFAULT 0,
    source_type VARCHAR(50) NOT NULL,
    source_id BIGINT NOT NULL,
    source_item_id BIGINT NOT NULL DEFAULT 0,
    cost_date DATE NOT NULL,
    cost_status VARCHAR(50) NOT NULL DEFAULT 'CONFIRMED',
    generated_flag SMALLINT NOT NULL DEFAULT 1,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    PRIMARY KEY (id),
    UNIQUE (source_type, source_id, source_item_id, cost_type),
    KEY idx_cost_project (project_id),
    KEY idx_cost_contract (contract_id),
    KEY idx_cost_source (source_type, source_id),
    KEY idx_cost_subject (cost_subject_id),
    KEY idx_cost_date (cost_date)
);

CREATE TABLE IF NOT EXISTS cost_ledger (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL,
    contract_id BIGINT,
    partner_id BIGINT,
    cost_subject_id BIGINT,
    cost_item_id BIGINT,
    cost_type VARCHAR(50),
    amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    source_type VARCHAR(50),
    source_id BIGINT,
    ledger_date DATE NOT NULL,
    direction VARCHAR(20) DEFAULT 'DEBIT',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_cost_ledger_project (project_id),
    KEY idx_cost_ledger_date (ledger_date)
);

-- pay_application with V19 remark column
CREATE TABLE IF NOT EXISTS pay_application (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL,
    contract_id BIGINT,
    partner_id BIGINT,
    apply_code VARCHAR(64) NOT NULL,
    apply_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    approved_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    actual_pay_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    pay_type VARCHAR(50) NOT NULL,
    pay_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    approval_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    apply_reason VARCHAR(1000),
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    PRIMARY KEY (id),
    UNIQUE (tenant_id, apply_code),
    KEY idx_pay_app_project (project_id),
    KEY idx_pay_app_contract (contract_id),
    KEY idx_pay_app_partner (partner_id),
    KEY idx_pay_app_status (pay_status, approval_status)
);

CREATE TABLE IF NOT EXISTS pay_record (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT,
    pay_application_id BIGINT NOT NULL,
    contract_id BIGINT,
    partner_id BIGINT,
    pay_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    pay_date DATE NOT NULL,
    pay_method VARCHAR(50),
    voucher_no VARCHAR(100),
    pay_status VARCHAR(50) NOT NULL DEFAULT 'SUCCESS',
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    PRIMARY KEY (id),
    KEY idx_pay_rec_app (pay_application_id),
    KEY idx_pay_rec_contract (contract_id),
    KEY idx_pay_rec_partner (partner_id),
    KEY idx_pay_rec_date (pay_date)
);

-- ====== V12: Phase 2 Business Tables ======
CREATE TABLE IF NOT EXISTS md_material (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    material_code VARCHAR(64) NOT NULL,
    material_name VARCHAR(200) NOT NULL,
    category_id BIGINT,
    specification VARCHAR(200),
    unit VARCHAR(20),
    brand VARCHAR(100),
    default_tax_rate DECIMAL(6,2),
    status VARCHAR(50) NOT NULL DEFAULT 'ENABLE',
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    PRIMARY KEY (id),
    UNIQUE (tenant_id, material_code),
    KEY idx_md_mat_cat (category_id)
);

CREATE TABLE IF NOT EXISTS mat_purchase_order (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL,
    request_id BIGINT,
    contract_id BIGINT,
    partner_id BIGINT,
    order_code VARCHAR(64) NOT NULL,
    order_type VARCHAR(50),
    order_date DATE,
    delivery_date DATE,
    total_amount DECIMAL(18,2),
    approval_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    order_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    PRIMARY KEY (id),
    UNIQUE (tenant_id, order_code),
    KEY idx_mpo_project (project_id),
    KEY idx_mpo_contract (contract_id),
    KEY idx_mpo_partner (partner_id)
);

CREATE TABLE IF NOT EXISTS mat_purchase_order_item (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    order_id BIGINT NOT NULL,
    project_id BIGINT,
    material_id BIGINT,
    material_name VARCHAR(200),
    specification VARCHAR(200),
    unit VARCHAR(20),
    quantity DECIMAL(18,4),
    unit_price DECIMAL(18,4),
    amount DECIMAL(18,2),
    received_quantity DECIMAL(18,4) NOT NULL DEFAULT 0,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    PRIMARY KEY (id),
    KEY idx_mpoi_order (order_id),
    KEY idx_mpoi_material (material_id)
);

CREATE TABLE IF NOT EXISTS mat_receipt (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL,
    order_id BIGINT,
    contract_id BIGINT,
    partner_id BIGINT,
    receipt_code VARCHAR(64) NOT NULL,
    receipt_date DATE,
    warehouse_id BIGINT,
    receiver_id BIGINT,
    quality_status VARCHAR(50),
    total_amount DECIMAL(18,2),
    approval_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    cost_generated_flag SMALLINT NOT NULL DEFAULT 0,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    PRIMARY KEY (id),
    UNIQUE (tenant_id, receipt_code),
    KEY idx_mr_project (project_id),
    KEY idx_mr_order (order_id),
    KEY idx_mr_contract (contract_id)
);

CREATE TABLE IF NOT EXISTS mat_receipt_item (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    receipt_id BIGINT NOT NULL,
    order_item_id BIGINT,
    material_id BIGINT,
    actual_quantity DECIMAL(18,4),
    qualified_quantity DECIMAL(18,4),
    unit_price DECIMAL(18,4),
    amount DECIMAL(18,2),
    use_location VARCHAR(200),
    batch_no VARCHAR(100),
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    PRIMARY KEY (id),
    KEY idx_mri_receipt (receipt_id),
    KEY idx_mri_material (material_id)
);

CREATE TABLE IF NOT EXISTS sub_task (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL,
    contract_id BIGINT,
    partner_id BIGINT,
    task_code VARCHAR(64) NOT NULL,
    task_name VARCHAR(200) NOT NULL,
    work_area VARCHAR(200),
    planned_start_date DATE,
    planned_end_date DATE,
    actual_start_date DATE,
    actual_end_date DATE,
    progress_percent DECIMAL(6,2) NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'NOT_STARTED',
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    PRIMARY KEY (id),
    UNIQUE (tenant_id, task_code),
    KEY idx_sub_task_project (project_id),
    KEY idx_sub_task_contract (contract_id)
);

CREATE TABLE IF NOT EXISTS sub_measure (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL,
    contract_id BIGINT,
    partner_id BIGINT,
    measure_code VARCHAR(64) NOT NULL,
    measure_period VARCHAR(50),
    measure_date DATE,
    reported_amount DECIMAL(18,2),
    approved_amount DECIMAL(18,2),
    deduction_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    net_amount DECIMAL(18,2),
    approval_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    cost_generated_flag SMALLINT NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    PRIMARY KEY (id),
    UNIQUE (tenant_id, measure_code),
    KEY idx_sub_measure_project (project_id),
    KEY idx_sub_measure_contract (contract_id)
);

CREATE TABLE IF NOT EXISTS sub_measure_item (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    measure_id BIGINT NOT NULL,
    contract_item_id BIGINT,
    item_name VARCHAR(200),
    unit VARCHAR(20),
    contract_quantity DECIMAL(18,4),
    current_quantity DECIMAL(18,4),
    cumulative_quantity DECIMAL(18,4),
    unit_price DECIMAL(18,4),
    amount DECIMAL(18,2),
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    PRIMARY KEY (id),
    KEY idx_smi_measure (measure_id)
);

CREATE TABLE IF NOT EXISTS var_order (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL,
    contract_id BIGINT,
    partner_id BIGINT,
    var_code VARCHAR(64) NOT NULL,
    var_name VARCHAR(200) NOT NULL,
    var_type VARCHAR(50),
    direction VARCHAR(20),
    reported_amount DECIMAL(18,2),
    approved_amount DECIMAL(18,2),
    confirmed_amount DECIMAL(18,2),
    owner_confirm_flag SMALLINT NOT NULL DEFAULT 0,
    impact_days INT NOT NULL DEFAULT 0,
    approval_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    cost_generated_flag SMALLINT NOT NULL DEFAULT 0,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    PRIMARY KEY (id),
    UNIQUE (tenant_id, var_code),
    KEY idx_var_order_project (project_id),
    KEY idx_var_order_contract (contract_id)
);

CREATE TABLE IF NOT EXISTS var_order_item (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    var_order_id BIGINT NOT NULL,
    item_name VARCHAR(200),
    unit VARCHAR(20),
    quantity DECIMAL(18,4),
    unit_price DECIMAL(18,4),
    amount DECIMAL(18,2),
    cost_subject_id BIGINT,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    PRIMARY KEY (id),
    KEY idx_voi_order (var_order_id)
);

CREATE TABLE IF NOT EXISTS pay_application_basis (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    pay_application_id BIGINT NOT NULL,
    basis_type VARCHAR(50) NOT NULL,
    basis_id BIGINT NOT NULL,
    basis_amount DECIMAL(18,2) NOT NULL,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    PRIMARY KEY (id),
    KEY idx_pab_app (pay_application_id),
    KEY idx_pab_basis (basis_type, basis_id)
);

CREATE TABLE IF NOT EXISTS cost_summary (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL,
    summary_date DATE NOT NULL,
    cost_subject_id BIGINT,
    cost_target_id BIGINT,
    target_cost DECIMAL(18,2) NOT NULL DEFAULT 0,
    contract_locked_cost DECIMAL(18,2) NOT NULL DEFAULT 0,
    actual_cost DECIMAL(18,2) NOT NULL DEFAULT 0,
    paid_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    estimated_remaining_cost DECIMAL(18,2) NOT NULL DEFAULT 0,
    dynamic_cost DECIMAL(18,2) NOT NULL DEFAULT 0,
    contract_income DECIMAL(18,2) NOT NULL DEFAULT 0,
    expected_profit DECIMAL(18,2) NOT NULL DEFAULT 0,
    cost_deviation DECIMAL(18,2) NOT NULL DEFAULT 0,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    PRIMARY KEY (id),
    UNIQUE (project_id, summary_date, cost_subject_id),
    KEY idx_cs_project (project_id),
    KEY idx_cs_date (summary_date)
);

-- stl_settlement with V24 enhanced columns
CREATE TABLE IF NOT EXISTS stl_settlement (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL,
    contract_id BIGINT,
    partner_id BIGINT,
    settlement_code VARCHAR(64) NOT NULL,
    settlement_type VARCHAR(50),
    contract_amount DECIMAL(18,2),
    change_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    measured_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    deduction_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    paid_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    final_amount DECIMAL(18,2),
    unpaid_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    warranty_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    settlement_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    finalized_at TIMESTAMP,
    approval_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    PRIMARY KEY (id),
    UNIQUE (tenant_id, settlement_code),
    KEY idx_stl_project (project_id),
    KEY idx_stl_contract (contract_id)
);

-- stl_settlement_item with V24 enhanced columns
CREATE TABLE IF NOT EXISTS stl_settlement_item (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    settlement_id BIGINT NOT NULL,
    item_name VARCHAR(200),
    unit VARCHAR(20),
    quantity DECIMAL(18,4),
    unit_price DECIMAL(18,4),
    amount DECIMAL(18,2),
    cost_subject_id BIGINT,
    source_type VARCHAR(50),
    source_id BIGINT,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    PRIMARY KEY (id),
    KEY idx_stl_si_settlement (settlement_id)
);

-- ====== V22: Cost Target ======
CREATE TABLE IF NOT EXISTS cost_target (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL,
    version_no VARCHAR(50) NOT NULL,
    version_name VARCHAR(200),
    total_target_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    is_active SMALLINT NOT NULL DEFAULT 0,
    approval_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    effective_date DATE,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    PRIMARY KEY (id),
    KEY idx_cost_target_project (project_id),
    KEY idx_cost_target_active (project_id, is_active)
);

CREATE TABLE IF NOT EXISTS cost_target_item (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    target_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    cost_subject_id BIGINT NOT NULL,
    target_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    created_by BIGINT,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    PRIMARY KEY (id),
    KEY idx_cost_ti_target (target_id),
    KEY idx_cost_ti_subject (cost_subject_id),
    KEY idx_cost_ti_project (project_id)
);

-- ====== V23: Contract Change ======
CREATE TABLE IF NOT EXISTS ct_contract_change (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL,
    contract_id BIGINT NOT NULL,
    change_code VARCHAR(64) NOT NULL,
    change_name VARCHAR(200) NOT NULL,
    change_type VARCHAR(50) NOT NULL,
    before_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    change_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    after_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    reason VARCHAR(500),
    approval_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    effective_flag SMALLINT NOT NULL DEFAULT 0,
    cost_generated_flag SMALLINT NOT NULL DEFAULT 0,
    created_by BIGINT,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    PRIMARY KEY (id),
    UNIQUE (tenant_id, change_code),
    KEY idx_change_contract (contract_id),
    KEY idx_change_project (project_id)
);

-- ====== V24: Alert Log ======
CREATE TABLE IF NOT EXISTS alert_log (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL,
    rule_type VARCHAR(100) NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    message VARCHAR(2000),
    triggered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_read SMALLINT NOT NULL DEFAULT 0,
    created_by BIGINT,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    PRIMARY KEY (id),
    KEY idx_alert_project (project_id),
    KEY idx_alert_tenant (tenant_id),
    KEY idx_alert_read (is_read),
    KEY idx_alert_triggered (triggered_at)
);

-- ====== V8: sys_dict tables ======
CREATE TABLE IF NOT EXISTS sys_dict (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    dict_code VARCHAR(64) NOT NULL,
    dict_name VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ENABLE',
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    PRIMARY KEY (id),
    UNIQUE (tenant_id, dict_code)
);

CREATE TABLE IF NOT EXISTS sys_dict_item (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    dict_id BIGINT NOT NULL,
    item_code VARCHAR(64),
    item_name VARCHAR(100) NOT NULL,
    item_value VARCHAR(500),
    sort_order INT DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'ENABLE',
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    PRIMARY KEY (id),
    KEY idx_sdi_dict (dict_id)
);

-- ====== Alterations from later migrations (added directly to H2 DDL above) ======
-- ct_contract already has: paid_amount (V18), cost_generated_flag (V20), settlement_amount (V29)
-- These columns are defined in the H2 schema.sql ct_contract table definition

-- ====== Approval Templates (V9, V13-V17, V28-V30) - minimal inserts needed ======
-- Insert templates without JSON_OBJECT by using H2-compatible syntax

-- wf_template rows for Phase 3 business types
MERGE INTO wf_template (id, tenant_id, template_code, template_name, business_type, enabled, amount_min, amount_max, condition_rule, form_schema, created_by, remark) KEY(id) VALUES
(50007, 0, 'TPL-CT-CHANGE-001', '合同变更审批流程', 'CT_CHANGE', 1, 0.00, 999999999.99, NULL, NULL, 1, '合同变更审批标准流程');

MERGE INTO wf_template (id, tenant_id, template_code, template_name, business_type, enabled, amount_min, amount_max, condition_rule, form_schema, created_by, remark) KEY(id) VALUES
(50008, 0, 'TPL-SETTLEMENT-001', '结算审批流程', 'SETTLEMENT', 1, 0.00, 999999999.99, NULL, NULL, 1, '结算审批标准流程');

MERGE INTO wf_template (id, tenant_id, template_code, template_name, business_type, enabled, amount_min, amount_max, condition_rule, form_schema, created_by, remark) KEY(id) VALUES
(50009, 0, 'TPL-COST-TARGET-001', '目标成本审批流程', 'COST_TARGET', 1, 0.00, 999999999.99, NULL, NULL, 1, '目标成本审批标准流程');

MERGE INTO wf_template (id, tenant_id, template_code, template_name, business_type, enabled, amount_min, amount_max, condition_rule, form_schema, created_by, remark) KEY(id) VALUES
(50010, 0, 'TPL-VAR-ORDER-001', '签证变更审批流程', 'VAR_ORDER', 1, 0.00, 999999999.99, NULL, NULL, 1, '签证变更审批标准流程');

MERGE INTO wf_template (id, tenant_id, template_code, template_name, business_type, enabled, amount_min, amount_max, condition_rule, form_schema, created_by, remark) KEY(id) VALUES
(50011, 0, 'TPL-SUB-MEASURE-001', '分包计量审批流程', 'SUB_MEASURE', 1, 0.00, 999999999.99, NULL, NULL, 1, '分包计量审批标准流程');

-- template nodes for CT_CHANGE (50007)
MERGE INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours) KEY(id) VALUES
(50701, 0, 50007, 'N1', '项目经理审批', 1, 'APPROVAL', 'SEQUENTIAL', '{"type":"USER","userId":1}', NULL, 1, 1, 48);
MERGE INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours) KEY(id) VALUES
(50702, 0, 50007, 'N2', '部门经理审批', 2, 'APPROVAL', 'SEQUENTIAL', '{"type":"USER","userId":1}', NULL, 1, 1, 48);
MERGE INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours) KEY(id) VALUES
(50703, 0, 50007, 'N3', '总经理审批', 3, 'APPROVAL', 'SEQUENTIAL', '{"type":"USER","userId":1}', NULL, 1, 1, 72);

-- template nodes for SETTLEMENT (50008)
MERGE INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours) KEY(id) VALUES
(50801, 0, 50008, 'N1', '项目经理审批', 1, 'APPROVAL', 'SEQUENTIAL', '{"type":"USER","userId":1}', NULL, 1, 1, 48);
MERGE INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours) KEY(id) VALUES
(50802, 0, 50008, 'N2', '部门经理审批', 2, 'APPROVAL', 'SEQUENTIAL', '{"type":"USER","userId":1}', NULL, 1, 1, 48);
MERGE INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours) KEY(id) VALUES
(50803, 0, 50008, 'N3', '总经理审批', 3, 'APPROVAL', 'SEQUENTIAL', '{"type":"USER","userId":1}', NULL, 1, 1, 72);

-- template nodes for COST_TARGET (50009)
MERGE INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours) KEY(id) VALUES
(50901, 0, 50009, 'N1', '项目经理审批', 1, 'APPROVAL', 'SEQUENTIAL', '{"type":"USER","userId":1}', NULL, 1, 1, 48);
MERGE INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours) KEY(id) VALUES
(50902, 0, 50009, 'N2', '部门经理审批', 2, 'APPROVAL', 'SEQUENTIAL', '{"type":"USER","userId":1}', NULL, 1, 1, 48);
MERGE INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours) KEY(id) VALUES
(50903, 0, 50009, 'N3', '总经理审批', 3, 'APPROVAL', 'SEQUENTIAL', '{"type":"USER","userId":1}', NULL, 1, 1, 72);

-- template nodes for VAR_ORDER (50010)
MERGE INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours) KEY(id) VALUES
(51001, 0, 50010, 'N1', '项目经理审批', 1, 'APPROVAL', 'SEQUENTIAL', '{"type":"USER","userId":1}', NULL, 1, 1, 48);
MERGE INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours) KEY(id) VALUES
(51002, 0, 50010, 'N2', '部门经理审批', 2, 'APPROVAL', 'SEQUENTIAL', '{"type":"USER","userId":1}', NULL, 1, 1, 48);
MERGE INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours) KEY(id) VALUES
(51003, 0, 50010, 'N3', '总经理审批', 3, 'APPROVAL', 'SEQUENTIAL', '{"type":"USER","userId":1}', NULL, 1, 1, 72);

-- template nodes for SUB_MEASURE (50011)
MERGE INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours) KEY(id) VALUES
(51101, 0, 50011, 'N1', '项目经理审批', 1, 'APPROVAL', 'SEQUENTIAL', '{"type":"USER","userId":1}', NULL, 1, 1, 48);
MERGE INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours) KEY(id) VALUES
(51102, 0, 50011, 'N2', '部门经理审批', 2, 'APPROVAL', 'SEQUENTIAL', '{"type":"USER","userId":1}', NULL, 1, 1, 48);
MERGE INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours) KEY(id) VALUES
(51103, 0, 50011, 'N3', '总经理审批', 3, 'APPROVAL', 'SEQUENTIAL', '{"type":"USER","userId":1}', NULL, 1, 1, 72);
