-- V3__init_workflow_tables.sql
-- 建筑工程总包项目全过程管理系统 - 工作流/审批引擎表
-- 数据库：MySQL 8.0+
-- ID 策略：后端雪花 ID / ASSIGN_ID，数据库不使用 AUTO_INCREMENT

-- ----------------------------
-- 审批模板表
-- ----------------------------
CREATE TABLE IF NOT EXISTS wf_template (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    template_code VARCHAR(64) NOT NULL,
    template_name VARCHAR(200) NOT NULL,
    business_type VARCHAR(50) NOT NULL,
    enabled SMALLINT NOT NULL DEFAULT 1,
    amount_min DECIMAL(18,2) NULL,
    amount_max DECIMAL(18,2) NULL,
    condition_rule TEXT NULL,
    form_schema TEXT NULL,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE (tenant_id, template_code),
    KEY idx_wf_template_business (business_type, enabled)
);

-- ----------------------------
-- 审批模板节点表
-- ----------------------------
CREATE TABLE IF NOT EXISTS wf_template_node (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    template_id BIGINT NOT NULL,
    node_code VARCHAR(64) NOT NULL,
    node_name VARCHAR(200) NOT NULL,
    node_order INT NOT NULL,
    node_type VARCHAR(50) NOT NULL DEFAULT 'APPROVAL',
    approve_mode VARCHAR(50) NOT NULL DEFAULT 'SEQUENTIAL',
    approver_config TEXT NOT NULL,
    pass_rule_json TEXT NULL,
    reject_rule_json TEXT NULL,
    condition_rule TEXT NULL,
    node_config TEXT NULL,
    allow_transfer SMALLINT NOT NULL DEFAULT 1,
    allow_add_sign SMALLINT NOT NULL DEFAULT 1,
    timeout_hours INT NULL,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE (template_id, node_code),
    KEY idx_wf_template_node_template (template_id, node_order)
);

-- ----------------------------
-- 审批实例表
-- ----------------------------
CREATE TABLE IF NOT EXISTS wf_instance (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    template_id BIGINT NOT NULL,
    business_type VARCHAR(50) NOT NULL,
    business_id BIGINT NOT NULL,
    project_id BIGINT NULL,
    contract_id BIGINT NULL,
    title VARCHAR(300) NOT NULL,
    amount DECIMAL(18,2) NULL,
    instance_status VARCHAR(50) NOT NULL DEFAULT 'RUNNING',
    current_round INT NOT NULL DEFAULT 1,
    resubmit_count INT NOT NULL DEFAULT 0,
    business_revision INT NOT NULL DEFAULT 1,
    initiator_id BIGINT NOT NULL,
    business_summary TEXT NULL,
    variables TEXT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at TIMESTAMP NULL,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE (business_type, business_id),
    KEY idx_wf_instance_initiator (initiator_id, instance_status),
    KEY idx_wf_instance_project (project_id),
    KEY idx_wf_instance_status (instance_status, current_round)
);

-- ----------------------------
-- 审批节点实例表
-- ----------------------------
CREATE TABLE IF NOT EXISTS wf_node_instance (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    instance_id BIGINT NOT NULL,
    template_node_id BIGINT NULL,
    node_code VARCHAR(64) NOT NULL,
    node_name VARCHAR(200) NOT NULL,
    node_order INT NOT NULL,
    approve_mode VARCHAR(50) NOT NULL,
    node_status VARCHAR(50) NOT NULL DEFAULT 'WAITING',
    round_no INT NOT NULL DEFAULT 1,
    pass_rule_json TEXT NULL,
    reject_rule_json TEXT NULL,
    started_at TIMESTAMP NULL,
    ended_at TIMESTAMP NULL,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    KEY idx_wf_node_instance_instance (instance_id, round_no, node_order),
    KEY idx_wf_node_instance_status (node_status)
);

-- ----------------------------
-- 审批任务表
-- ----------------------------
CREATE TABLE IF NOT EXISTS wf_task (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    instance_id BIGINT NOT NULL,
    node_instance_id BIGINT NOT NULL,
    business_type VARCHAR(50) NOT NULL,
    business_id BIGINT NOT NULL,
    approver_id BIGINT NOT NULL,
    approver_name VARCHAR(100) NULL,
    task_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    round_no INT NOT NULL DEFAULT 1,
    task_version INT NOT NULL DEFAULT 1,
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    handled_at TIMESTAMP NULL,
    action_type VARCHAR(50) NULL,
    comment VARCHAR(1000) NULL,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    KEY idx_wf_task_todo (approver_id, task_status, received_at),
    KEY idx_wf_task_instance (instance_id, round_no),
    KEY idx_wf_task_node (node_instance_id),
    KEY idx_wf_task_business (business_type, business_id)
);

-- ----------------------------
-- 审批记录表
-- ----------------------------
CREATE TABLE IF NOT EXISTS wf_record (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    instance_id BIGINT NOT NULL,
    node_instance_id BIGINT NULL,
    task_id BIGINT NULL,
    round_no INT NOT NULL DEFAULT 1,
    business_type VARCHAR(50) NOT NULL,
    business_id BIGINT NOT NULL,
    node_code VARCHAR(64) NULL,
    node_name VARCHAR(200) NULL,
    action_type VARCHAR(50) NOT NULL,
    action_name VARCHAR(100) NOT NULL,
    operator_id BIGINT NOT NULL,
    operator_name VARCHAR(100) NULL,
    comment VARCHAR(1000) NULL,
    record_status VARCHAR(50) NOT NULL DEFAULT 'EFFECTIVE',
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    KEY idx_wf_record_instance (instance_id, round_no, created_at),
    KEY idx_wf_record_task (task_id),
    KEY idx_wf_record_node (node_instance_id),
    KEY idx_wf_record_business (business_type, business_id)
);

-- ----------------------------
-- 审批幂等表
-- ----------------------------
CREATE TABLE IF NOT EXISTS wf_idempotency (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    user_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    business_type VARCHAR(50) NULL,
    business_id BIGINT NULL,
    request_hash VARCHAR(128) NULL,
    response_json TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expired_at TIMESTAMP NULL,
    PRIMARY KEY (id),
    UNIQUE (tenant_id, user_id, idempotency_key),
    KEY idx_wf_idempotency_expired (expired_at)
);
