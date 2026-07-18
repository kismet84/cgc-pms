-- V4__init_cost_payment_tables.sql
-- 建筑工程总包项目全过程管理系统 - 成本/付款相关表
-- 数据库：MySQL 8.0+
-- ID 策略：后端雪花 ID / ASSIGN_ID，数据库不使用 AUTO_INCREMENT

-- ----------------------------
-- 成本科目表
-- ----------------------------
CREATE TABLE IF NOT EXISTS cost_subject (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    parent_id BIGINT NOT NULL DEFAULT 0,
    subject_code VARCHAR(64) NOT NULL,
    subject_name VARCHAR(200) NOT NULL,
    subject_type VARCHAR(50) NULL,
    level INT NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'ENABLE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE (tenant_id, subject_code),
    KEY idx_cost_subject_parent (parent_id),
    KEY idx_cost_subject_type (subject_type)
);

-- ----------------------------
-- 成本明细表
-- ----------------------------
CREATE TABLE IF NOT EXISTS cost_item (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    org_id BIGINT NULL,
    project_id BIGINT NOT NULL,
    contract_id BIGINT NULL,
    partner_id BIGINT NULL,
    cost_subject_id BIGINT NULL,
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
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE (source_type, source_id, source_item_id, cost_type),
    KEY idx_cost_project (project_id),
    KEY idx_cost_contract (contract_id),
    KEY idx_cost_source (source_type, source_id),
    KEY idx_cost_subject (cost_subject_id),
    KEY idx_cost_date (cost_date)
);

-- ----------------------------
-- 付款申请表
-- ----------------------------
CREATE TABLE IF NOT EXISTS pay_application (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    project_id BIGINT NOT NULL,
    contract_id BIGINT NULL,
    partner_id BIGINT NULL,
    apply_code VARCHAR(64) NOT NULL,
    apply_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    approved_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    actual_pay_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    pay_type VARCHAR(50) NOT NULL,
    pay_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    approval_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    apply_reason VARCHAR(1000) NULL,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE (tenant_id, apply_code),
    KEY idx_pay_application_project (project_id),
    KEY idx_pay_application_contract (contract_id),
    KEY idx_pay_application_partner (partner_id),
    KEY idx_pay_application_status (pay_status, approval_status)
);

-- ----------------------------
-- 付款记录表
-- ----------------------------
CREATE TABLE IF NOT EXISTS pay_record (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    pay_application_id BIGINT NOT NULL,
    contract_id BIGINT NULL,
    partner_id BIGINT NULL,
    pay_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    pay_date DATE NOT NULL,
    pay_method VARCHAR(50) NULL,
    voucher_no VARCHAR(100) NULL,
    pay_status VARCHAR(50) NOT NULL DEFAULT 'SUCCESS',
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_pay_record_application (pay_application_id),
    KEY idx_pay_record_contract (contract_id),
    KEY idx_pay_record_partner (partner_id),
    KEY idx_pay_record_date (pay_date)
);
