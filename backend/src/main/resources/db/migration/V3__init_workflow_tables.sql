-- V3__init_workflow_tables.sql
-- 建筑工程总包项目全过程管理系统 - 工作流/审批引擎表
-- 数据库：MySQL 8.0+
-- ID 策略：后端雪花 ID / ASSIGN_ID，数据库不使用 AUTO_INCREMENT

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 审批模板表
-- ----------------------------
CREATE TABLE IF NOT EXISTS wf_template (
    id BIGINT NOT NULL COMMENT '审批模板ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    template_code VARCHAR(64) NOT NULL COMMENT '模板编码',
    template_name VARCHAR(200) NOT NULL COMMENT '模板名称',
    business_type VARCHAR(50) NOT NULL COMMENT '业务类型',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：0否，1是',
    amount_min DECIMAL(18,2) NULL COMMENT '适用金额下限',
    amount_max DECIMAL(18,2) NULL COMMENT '适用金额上限',
    condition_rule JSON NULL COMMENT '流程匹配条件',
    form_schema JSON NULL COMMENT '动态表单Schema',
    created_by BIGINT NULL COMMENT '创建人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '更新人',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    remark VARCHAR(500) NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wf_template_code (tenant_id, template_code),
    KEY idx_wf_template_business (business_type, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批模板表';

-- ----------------------------
-- 审批模板节点表
-- ----------------------------
CREATE TABLE IF NOT EXISTS wf_template_node (
    id BIGINT NOT NULL COMMENT '模板节点ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    template_id BIGINT NOT NULL COMMENT '模板ID',
    node_code VARCHAR(64) NOT NULL COMMENT '节点编码',
    node_name VARCHAR(200) NOT NULL COMMENT '节点名称',
    node_order INT NOT NULL COMMENT '节点顺序',
    node_type VARCHAR(50) NOT NULL DEFAULT 'APPROVAL' COMMENT '节点类型',
    approve_mode VARCHAR(50) NOT NULL DEFAULT 'SEQUENTIAL' COMMENT '审批模式',
    approver_config JSON NOT NULL COMMENT '审批人配置',
    pass_rule_json JSON NULL COMMENT '节点通过规则',
    reject_rule_json JSON NULL COMMENT '节点驳回规则',
    condition_rule JSON NULL COMMENT '节点执行条件',
    node_config JSON NULL COMMENT '节点扩展配置',
    allow_transfer TINYINT NOT NULL DEFAULT 1 COMMENT '是否允许转办：0否，1是',
    allow_add_sign TINYINT NOT NULL DEFAULT 1 COMMENT '是否允许加签：0否，1是',
    timeout_hours INT NULL COMMENT '超时时间（小时）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wf_template_node_code (template_id, node_code),
    KEY idx_wf_template_node_template (template_id, node_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批模板节点表';

-- ----------------------------
-- 审批实例表
-- ----------------------------
CREATE TABLE IF NOT EXISTS wf_instance (
    id BIGINT NOT NULL COMMENT '审批实例ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    template_id BIGINT NOT NULL COMMENT '审批模板ID',
    business_type VARCHAR(50) NOT NULL COMMENT '业务类型',
    business_id BIGINT NOT NULL COMMENT '业务单据ID',
    project_id BIGINT NULL COMMENT '项目ID',
    contract_id BIGINT NULL COMMENT '合同ID',
    title VARCHAR(300) NOT NULL COMMENT '审批标题',
    amount DECIMAL(18,2) NULL COMMENT '审批金额',
    instance_status VARCHAR(50) NOT NULL DEFAULT 'RUNNING' COMMENT '实例状态',
    current_round INT NOT NULL DEFAULT 1 COMMENT '当前审批轮次',
    resubmit_count INT NOT NULL DEFAULT 0 COMMENT '重新提交次数',
    business_revision INT NOT NULL DEFAULT 1 COMMENT '业务版本',
    initiator_id BIGINT NOT NULL COMMENT '发起人ID',
    business_summary JSON NULL COMMENT '业务摘要',
    variables JSON NULL COMMENT '流程变量',
    started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发起时间',
    ended_at DATETIME NULL COMMENT '结束时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wf_instance_business (business_type, business_id),
    KEY idx_wf_instance_initiator (initiator_id, instance_status),
    KEY idx_wf_instance_project (project_id),
    KEY idx_wf_instance_status (instance_status, current_round)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批实例表';

-- ----------------------------
-- 审批节点实例表
-- ----------------------------
CREATE TABLE IF NOT EXISTS wf_node_instance (
    id BIGINT NOT NULL COMMENT '节点实例ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    instance_id BIGINT NOT NULL COMMENT '审批实例ID',
    template_node_id BIGINT NULL COMMENT '模板节点ID',
    node_code VARCHAR(64) NOT NULL COMMENT '节点编码',
    node_name VARCHAR(200) NOT NULL COMMENT '节点名称',
    node_order INT NOT NULL COMMENT '节点顺序',
    approve_mode VARCHAR(50) NOT NULL COMMENT '审批模式',
    node_status VARCHAR(50) NOT NULL DEFAULT 'WAITING' COMMENT '节点状态',
    round_no INT NOT NULL DEFAULT 1 COMMENT '审批轮次',
    pass_rule_json JSON NULL COMMENT '节点通过规则',
    reject_rule_json JSON NULL COMMENT '节点驳回规则',
    started_at DATETIME NULL COMMENT '开始时间',
    ended_at DATETIME NULL COMMENT '结束时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    PRIMARY KEY (id),
    KEY idx_wf_node_instance_instance (instance_id, round_no, node_order),
    KEY idx_wf_node_instance_status (node_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批节点实例表';

-- ----------------------------
-- 审批任务表
-- ----------------------------
CREATE TABLE IF NOT EXISTS wf_task (
    id BIGINT NOT NULL COMMENT '审批任务ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    instance_id BIGINT NOT NULL COMMENT '审批实例ID',
    node_instance_id BIGINT NOT NULL COMMENT '节点实例ID',
    business_type VARCHAR(50) NOT NULL COMMENT '业务类型',
    business_id BIGINT NOT NULL COMMENT '业务单据ID',
    approver_id BIGINT NOT NULL COMMENT '审批人ID',
    approver_name VARCHAR(100) NULL COMMENT '审批人姓名',
    task_status VARCHAR(50) NOT NULL DEFAULT 'PENDING' COMMENT '任务状态',
    round_no INT NOT NULL DEFAULT 1 COMMENT '审批轮次',
    task_version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    received_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '接收时间',
    handled_at DATETIME NULL COMMENT '处理时间',
    action_type VARCHAR(50) NULL COMMENT '处理动作',
    comment VARCHAR(1000) NULL COMMENT '审批意见',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    PRIMARY KEY (id),
    KEY idx_wf_task_todo (approver_id, task_status, received_at),
    KEY idx_wf_task_instance (instance_id, round_no),
    KEY idx_wf_task_node (node_instance_id),
    KEY idx_wf_task_business (business_type, business_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批任务表';

-- ----------------------------
-- 审批记录表
-- ----------------------------
CREATE TABLE IF NOT EXISTS wf_record (
    id BIGINT NOT NULL COMMENT '审批记录ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    instance_id BIGINT NOT NULL COMMENT '审批实例ID',
    node_instance_id BIGINT NULL COMMENT '节点实例ID，提交/撤回等可为空',
    task_id BIGINT NULL COMMENT '审批任务ID',
    round_no INT NOT NULL DEFAULT 1 COMMENT '审批轮次',
    business_type VARCHAR(50) NOT NULL COMMENT '业务类型',
    business_id BIGINT NOT NULL COMMENT '业务单据ID',
    node_code VARCHAR(64) NULL COMMENT '节点编码',
    node_name VARCHAR(200) NULL COMMENT '节点名称',
    action_type VARCHAR(50) NOT NULL COMMENT '动作类型',
    action_name VARCHAR(100) NOT NULL COMMENT '动作名称',
    operator_id BIGINT NOT NULL COMMENT '操作人ID',
    operator_name VARCHAR(100) NULL COMMENT '操作人姓名',
    comment VARCHAR(1000) NULL COMMENT '审批意见',
    record_status VARCHAR(50) NOT NULL DEFAULT 'EFFECTIVE' COMMENT '记录状态',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    PRIMARY KEY (id),
    KEY idx_wf_record_instance (instance_id, round_no, created_at),
    KEY idx_wf_record_task (task_id),
    KEY idx_wf_record_node (node_instance_id),
    KEY idx_wf_record_business (business_type, business_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批记录表';

-- ----------------------------
-- 审批幂等表
-- ----------------------------
CREATE TABLE IF NOT EXISTS wf_idempotency (
    id BIGINT NOT NULL COMMENT '幂等记录ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    idempotency_key VARCHAR(128) NOT NULL COMMENT '幂等键',
    business_type VARCHAR(50) NULL COMMENT '业务类型',
    business_id BIGINT NULL COMMENT '业务ID',
    request_hash VARCHAR(128) NULL COMMENT '请求摘要',
    response_json JSON NULL COMMENT '首次响应结果',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    expired_at DATETIME NULL COMMENT '过期时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wf_idempotency (tenant_id, user_id, idempotency_key),
    KEY idx_wf_idempotency_expired (expired_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批幂等表';

SET FOREIGN_KEY_CHECKS = 1;
