-- H2 Schema for local development (MySQL compatibility mode)
-- Core tables needed for application startup

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    username VARCHAR(64) NOT NULL,
    password VARCHAR(255) NOT NULL,
    real_name VARCHAR(100),
    phone VARCHAR(50),
    email VARCHAR(100),
    avatar VARCHAR(500),
    status VARCHAR(50) NOT NULL DEFAULT 'ENABLE',
    is_admin SMALLINT DEFAULT 0,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    PRIMARY KEY (id),
    UNIQUE (tenant_id, username)
);

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    role_code VARCHAR(64) NOT NULL,
    role_name VARCHAR(100) NOT NULL,
    role_type VARCHAR(50),
    status VARCHAR(50) NOT NULL DEFAULT 'ENABLE',
    data_scope VARCHAR(50),
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    PRIMARY KEY (id),
    UNIQUE (tenant_id, role_code)
);

CREATE TABLE IF NOT EXISTS sys_menu (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    parent_id BIGINT DEFAULT 0,
    menu_name VARCHAR(100) NOT NULL,
    menu_type VARCHAR(50) NOT NULL DEFAULT 'MENU',
    path VARCHAR(200),
    component VARCHAR(200),
    perms VARCHAR(200),
    icon VARCHAR(100),
    order_num INT DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'ENABLE',
    visible SMALLINT DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS sys_role_menu (
    id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (role_id, menu_id)
);

CREATE TABLE IF NOT EXISTS pm_project (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    org_id BIGINT,
    project_code VARCHAR(64) NOT NULL,
    project_name VARCHAR(200) NOT NULL,
    project_type VARCHAR(50),
    project_address VARCHAR(300),
    owner_unit VARCHAR(200),
    supervisor_unit VARCHAR(200),
    design_unit VARCHAR(200),
    contract_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    target_cost DECIMAL(18,2) NOT NULL DEFAULT 0,
    planned_start_date DATE,
    planned_end_date DATE,
    actual_start_date DATE,
    actual_end_date DATE,
    project_manager_id BIGINT,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    approval_status VARCHAR(50),
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    PRIMARY KEY (id),
    UNIQUE (tenant_id, project_code)
);

CREATE TABLE IF NOT EXISTS md_partner (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    partner_code VARCHAR(64) NOT NULL,
    partner_name VARCHAR(200) NOT NULL,
    partner_type VARCHAR(50) NOT NULL,
    credit_code VARCHAR(100),
    legal_person VARCHAR(100),
    contact_name VARCHAR(100),
    contact_phone VARCHAR(50),
    bank_name VARCHAR(200),
    bank_account VARCHAR(100),
    qualification_level VARCHAR(100),
    blacklist_flag SMALLINT NOT NULL DEFAULT 0,
    risk_level VARCHAR(50),
    status VARCHAR(50) NOT NULL DEFAULT 'ENABLE',
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    PRIMARY KEY (id),
    UNIQUE (tenant_id, partner_code)
);

CREATE TABLE IF NOT EXISTS ct_contract (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    org_id BIGINT,
    project_id BIGINT NOT NULL,
    partner_id BIGINT,
    contract_code VARCHAR(64) NOT NULL,
    contract_name VARCHAR(200) NOT NULL,
    contract_type VARCHAR(50) NOT NULL,
    party_a VARCHAR(200),
    party_b VARCHAR(200),
    contract_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    current_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    tax_rate DECIMAL(6,2) NOT NULL DEFAULT 0,
    tax_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    amount_without_tax DECIMAL(18,2) NOT NULL DEFAULT 0,
    signed_date DATE,
    start_date DATE,
    end_date DATE,
    payment_method VARCHAR(100),
    settlement_method VARCHAR(100),
    warranty_rate DECIMAL(6,2) NOT NULL DEFAULT 0,
    warranty_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    contract_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    approval_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    PRIMARY KEY (id),
    UNIQUE (tenant_id, contract_code)
);
