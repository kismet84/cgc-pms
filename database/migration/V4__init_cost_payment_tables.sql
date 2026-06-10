-- V4__init_cost_payment_tables.sql
-- 建筑工程总包项目全过程管理系统 - 成本/付款相关表
-- 数据库：MySQL 8.0+
-- ID 策略：后端雪花 ID / ASSIGN_ID，数据库不使用 AUTO_INCREMENT

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 成本科目表
-- ----------------------------
CREATE TABLE IF NOT EXISTS cost_subject (
    id BIGINT NOT NULL COMMENT '成本科目ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '父科目ID，0表示根节点',
    subject_code VARCHAR(64) NOT NULL COMMENT '科目编码',
    subject_name VARCHAR(200) NOT NULL COMMENT '科目名称',
    subject_type VARCHAR(50) NULL COMMENT '科目类型：材料/分包/机械/人工/管理费等',
    level INT NOT NULL DEFAULT 1 COMMENT '科目层级',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
    status VARCHAR(50) NOT NULL DEFAULT 'ENABLE' COMMENT '状态：ENABLE启用，DISABLE禁用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_cost_subject_code (tenant_id, subject_code),
    KEY idx_cost_subject_parent (parent_id),
    KEY idx_cost_subject_type (subject_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='成本科目表';

-- ----------------------------
-- 成本明细表
-- ----------------------------
CREATE TABLE IF NOT EXISTS cost_item (
    id BIGINT NOT NULL COMMENT '成本ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    org_id BIGINT NULL COMMENT '所属组织ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    contract_id BIGINT NULL COMMENT '合同ID',
    partner_id BIGINT NULL COMMENT '合作方ID',
    cost_subject_id BIGINT NULL COMMENT '成本科目ID',
    cost_type VARCHAR(50) NOT NULL COMMENT '材料/分包/机械/人工/签证/管理费等',
    amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '成本金额',
    tax_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '税额',
    amount_without_tax DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '不含税金额',
    source_type VARCHAR(50) NOT NULL COMMENT '来源类型，如 MAT_RECEIPT/SUB_MEASURE/VAR_ORDER',
    source_id BIGINT NOT NULL COMMENT '来源单据主表ID',
    source_item_id BIGINT NOT NULL DEFAULT 0 COMMENT '来源单据明细ID，不按明细拆分时为0',
    cost_date DATE NOT NULL COMMENT '成本发生日期',
    cost_status VARCHAR(50) NOT NULL DEFAULT 'CONFIRMED' COMMENT '暂估/已确认/已结算/已冲销',
    generated_flag TINYINT NOT NULL DEFAULT 1 COMMENT '是否系统生成：0否，1是',
    created_by BIGINT NULL COMMENT '创建人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '更新人',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    remark VARCHAR(500) NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_cost_source_item (source_type, source_id, source_item_id, cost_type),
    KEY idx_cost_project (project_id),
    KEY idx_cost_contract (contract_id),
    KEY idx_cost_source (source_type, source_id),
    KEY idx_cost_subject (cost_subject_id),
    KEY idx_cost_date (cost_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='成本明细表';

-- ----------------------------
-- 付款申请表
-- ----------------------------
CREATE TABLE IF NOT EXISTS pay_application (
    id BIGINT NOT NULL COMMENT '付款申请ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    contract_id BIGINT NULL COMMENT '合同ID',
    partner_id BIGINT NULL COMMENT '合作方ID',
    apply_code VARCHAR(64) NOT NULL COMMENT '申请单编号',
    apply_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '申请金额',
    approved_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '批准金额',
    actual_pay_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '实际付款金额',
    pay_type VARCHAR(50) NOT NULL COMMENT '付款类型：预付款/进度款/结算款/质保金等',
    pay_status VARCHAR(50) NOT NULL DEFAULT 'PENDING' COMMENT '付款状态：PENDING待付，PARTIAL部分付款，PAID已付清',
    approval_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT' COMMENT '审批状态',
    apply_reason VARCHAR(1000) NULL COMMENT '申请事由',
    created_by BIGINT NULL COMMENT '创建人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '更新人',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pay_application_code (tenant_id, apply_code),
    KEY idx_pay_application_project (project_id),
    KEY idx_pay_application_contract (contract_id),
    KEY idx_pay_application_partner (partner_id),
    KEY idx_pay_application_status (pay_status, approval_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='付款申请表';

-- ----------------------------
-- 付款记录表
-- ----------------------------
CREATE TABLE IF NOT EXISTS pay_record (
    id BIGINT NOT NULL COMMENT '付款记录ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    pay_application_id BIGINT NOT NULL COMMENT '付款申请ID',
    contract_id BIGINT NULL COMMENT '合同ID',
    partner_id BIGINT NULL COMMENT '合作方ID',
    pay_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '付款金额',
    pay_date DATE NOT NULL COMMENT '付款日期',
    pay_method VARCHAR(50) NULL COMMENT '付款方式：银行转账/承兑汇票/现金等',
    voucher_no VARCHAR(100) NULL COMMENT '付款凭证号',
    pay_status VARCHAR(50) NOT NULL DEFAULT 'SUCCESS' COMMENT '付款状态：SUCCESS成功，FAILED失败，PROCESSING处理中',
    created_by BIGINT NULL COMMENT '创建人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '更新人',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    PRIMARY KEY (id),
    KEY idx_pay_record_application (pay_application_id),
    KEY idx_pay_record_contract (contract_id),
    KEY idx_pay_record_partner (partner_id),
    KEY idx_pay_record_date (pay_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='付款记录表';

SET FOREIGN_KEY_CHECKS = 1;
