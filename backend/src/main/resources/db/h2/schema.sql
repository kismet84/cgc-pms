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
    paid_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    settlement_amount DECIMAL(18,2),
    cost_generated_flag SMALLINT NOT NULL DEFAULT 0,
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

-- ====== Workflow Tables ======

CREATE TABLE IF NOT EXISTS wf_template (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    template_code VARCHAR(64) NOT NULL,
    template_name VARCHAR(200) NOT NULL,
    business_type VARCHAR(50) NOT NULL,
    enabled SMALLINT NOT NULL DEFAULT 1,
    amount_min DECIMAL(18,2),
    amount_max DECIMAL(18,2),
    condition_rule VARCHAR(2000),
    form_schema VARCHAR(2000),
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS wf_template_node (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    template_id BIGINT NOT NULL,
    node_code VARCHAR(64) NOT NULL,
    node_name VARCHAR(200) NOT NULL,
    node_order INT NOT NULL,
    node_type VARCHAR(50) NOT NULL DEFAULT 'APPROVAL',
    approve_mode VARCHAR(50) NOT NULL DEFAULT 'SEQUENTIAL',
    approver_config VARCHAR(2000) NOT NULL DEFAULT '{}',
    pass_rule_json VARCHAR(2000),
    reject_rule_json VARCHAR(2000),
    condition_rule VARCHAR(2000),
    node_config VARCHAR(2000),
    allow_transfer SMALLINT NOT NULL DEFAULT 1,
    allow_add_sign SMALLINT NOT NULL DEFAULT 1,
    timeout_hours INT,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS wf_instance (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    template_id BIGINT NOT NULL,
    business_type VARCHAR(50) NOT NULL,
    business_id BIGINT NOT NULL,
    project_id BIGINT,
    contract_id BIGINT,
    title VARCHAR(300) NOT NULL,
    amount DECIMAL(18,2),
    instance_status VARCHAR(50) NOT NULL DEFAULT 'RUNNING',
    current_round INT NOT NULL DEFAULT 1,
    resubmit_count INT NOT NULL DEFAULT 0,
    business_revision INT NOT NULL DEFAULT 1,
    initiator_id BIGINT NOT NULL,
    business_summary VARCHAR(2000),
    variables VARCHAR(2000),
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at TIMESTAMP,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS wf_node_instance (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    instance_id BIGINT NOT NULL,
    template_node_id BIGINT,
    node_code VARCHAR(64) NOT NULL,
    node_name VARCHAR(200) NOT NULL,
    node_order INT NOT NULL,
    approve_mode VARCHAR(50) NOT NULL,
    node_status VARCHAR(50) NOT NULL DEFAULT 'WAITING',
    round_no INT NOT NULL DEFAULT 1,
    pass_rule_json VARCHAR(2000),
    reject_rule_json VARCHAR(2000),
    started_at TIMESTAMP,
    ended_at TIMESTAMP,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS wf_task (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    instance_id BIGINT NOT NULL,
    node_instance_id BIGINT NOT NULL,
    business_type VARCHAR(50) NOT NULL,
    business_id BIGINT NOT NULL,
    approver_id BIGINT NOT NULL,
    approver_name VARCHAR(100),
    task_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    round_no INT NOT NULL DEFAULT 1,
    task_version INT NOT NULL DEFAULT 1,
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    handled_at TIMESTAMP,
    action_type VARCHAR(50),
    comment VARCHAR(1000),
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS wf_record (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    instance_id BIGINT NOT NULL,
    node_instance_id BIGINT,
    task_id BIGINT,
    round_no INT NOT NULL DEFAULT 1,
    business_type VARCHAR(50) NOT NULL,
    business_id BIGINT NOT NULL,
    node_code VARCHAR(64),
    node_name VARCHAR(200),
    action_type VARCHAR(50) NOT NULL,
    action_name VARCHAR(100) NOT NULL,
    operator_id BIGINT NOT NULL,
    operator_name VARCHAR(100),
    comment VARCHAR(1000),
    record_status VARCHAR(50) NOT NULL DEFAULT 'EFFECTIVE',
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS wf_idempotency (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    user_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    business_type VARCHAR(50),
    business_id BIGINT,
    request_hash VARCHAR(128),
    response_json VARCHAR(2000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expired_at TIMESTAMP,
    PRIMARY KEY (id)
);

-- sys_dict_type table (matches MySQL V5 migration — sys_dict_type, not sys_dict)
CREATE TABLE IF NOT EXISTS sys_dict_type (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    dict_code VARCHAR(100) NOT NULL,
    dict_name VARCHAR(200) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ENABLE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (tenant_id, dict_code)
);

-- sys_dict_data table (matches MySQL V5 migration — sys_dict_data, not sys_dict_item)
CREATE TABLE IF NOT EXISTS sys_dict_data (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    dict_type_id BIGINT NOT NULL,
    dict_label VARCHAR(200) NOT NULL,
    dict_value VARCHAR(200) NOT NULL,
    css_class VARCHAR(100),
    list_class VARCHAR(100),
    order_num INT NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'ENABLE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (dict_type_id, dict_value)
);
-- ====== File Management Tables ======

CREATE TABLE IF NOT EXISTS sys_file (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    business_type VARCHAR(50) NOT NULL,
    business_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    original_name VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL DEFAULT 0,
    content_type VARCHAR(200),
    storage_path VARCHAR(500) NOT NULL,
    bucket_name VARCHAR(100) NOT NULL DEFAULT 'cgc-pms',
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    PRIMARY KEY (id)
);

