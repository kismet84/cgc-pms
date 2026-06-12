-- V2__init_project_partner_contract.sql
-- 建筑工程总包项目全过程管理系统 - 项目/合作方/合同业务核心表
-- 数据库：MySQL 8.0+
-- ID 策略：后端雪花 ID / ASSIGN_ID，数据库不使用 AUTO_INCREMENT

-- ----------------------------
-- 项目表
-- ----------------------------
CREATE TABLE IF NOT EXISTS pm_project (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    org_id BIGINT NULL,
    project_code VARCHAR(64) NOT NULL,
    project_name VARCHAR(200) NOT NULL,
    project_type VARCHAR(50) NULL,
    project_address VARCHAR(300) NULL,
    owner_unit VARCHAR(200) NULL,
    supervisor_unit VARCHAR(200) NULL,
    design_unit VARCHAR(200) NULL,
    contract_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    target_cost DECIMAL(18,2) NOT NULL DEFAULT 0,
    planned_start_date DATE NULL,
    planned_end_date DATE NULL,
    actual_start_date DATE NULL,
    actual_end_date DATE NULL,
    project_manager_id BIGINT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    approval_status VARCHAR(50) NULL,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE (tenant_id, project_code),
    KEY idx_pm_project_name (project_name),
    KEY idx_pm_project_status (status),
    KEY idx_pm_project_manager (project_manager_id)
);

-- ----------------------------
-- 合作方表
-- ----------------------------
CREATE TABLE IF NOT EXISTS md_partner (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    partner_code VARCHAR(64) NOT NULL,
    partner_name VARCHAR(200) NOT NULL,
    partner_type VARCHAR(50) NOT NULL,
    credit_code VARCHAR(100) NULL,
    legal_person VARCHAR(100) NULL,
    contact_name VARCHAR(100) NULL,
    contact_phone VARCHAR(50) NULL,
    bank_name VARCHAR(200) NULL,
    bank_account VARCHAR(100) NULL,
    qualification_level VARCHAR(100) NULL,
    blacklist_flag SMALLINT NOT NULL DEFAULT 0,
    risk_level VARCHAR(50) NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ENABLE',
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE (tenant_id, partner_code),
    KEY idx_md_partner_name (partner_name),
    KEY idx_md_partner_type (partner_type),
    KEY idx_md_partner_blacklist (blacklist_flag)
);

-- ----------------------------
-- 合同主表
-- ----------------------------
CREATE TABLE IF NOT EXISTS ct_contract (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    org_id BIGINT NULL,
    project_id BIGINT NOT NULL,
    partner_id BIGINT NULL,
    contract_code VARCHAR(64) NOT NULL,
    contract_name VARCHAR(200) NOT NULL,
    contract_type VARCHAR(50) NOT NULL,
    party_a VARCHAR(200) NULL,
    party_b VARCHAR(200) NULL,
    contract_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    current_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    tax_rate DECIMAL(6,2) NOT NULL DEFAULT 0,
    tax_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    amount_without_tax DECIMAL(18,2) NOT NULL DEFAULT 0,
    signed_date DATE NULL,
    start_date DATE NULL,
    end_date DATE NULL,
    payment_method VARCHAR(100) NULL,
    settlement_method VARCHAR(100) NULL,
    warranty_rate DECIMAL(6,2) NOT NULL DEFAULT 0,
    warranty_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    contract_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    approval_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE (tenant_id, contract_code),
    KEY idx_ct_contract_project (project_id),
    KEY idx_ct_contract_partner (partner_id),
    KEY idx_ct_contract_type (contract_type),
    KEY idx_ct_contract_status (contract_status, approval_status)
);

-- ----------------------------
-- 合同明细表
-- ----------------------------
CREATE TABLE IF NOT EXISTS ct_contract_item (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    contract_id BIGINT NOT NULL,
    item_code VARCHAR(64) NULL,
    item_name VARCHAR(200) NOT NULL,
    item_spec VARCHAR(300) NULL,
    unit VARCHAR(50) NULL,
    quantity DECIMAL(18,4) NOT NULL DEFAULT 0,
    unit_price DECIMAL(18,4) NOT NULL DEFAULT 0,
    amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    tax_rate DECIMAL(6,2) NOT NULL DEFAULT 0,
    tax_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    amount_without_tax DECIMAL(18,2) NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_ct_contract_item_contract (contract_id, sort_order),
    KEY idx_ct_contract_item_code (item_code)
);

-- ----------------------------
-- 合同付款条款表
-- ----------------------------
CREATE TABLE IF NOT EXISTS ct_contract_payment_term (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    contract_id BIGINT NOT NULL,
    term_name VARCHAR(200) NOT NULL,
    payment_ratio DECIMAL(6,2) NOT NULL DEFAULT 0,
    payment_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    payment_condition VARCHAR(500) NULL,
    planned_date DATE NULL,
    actual_date DATE NULL,
    term_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_ct_payment_term_contract (contract_id, sort_order),
    KEY idx_ct_payment_term_status (term_status)
);
