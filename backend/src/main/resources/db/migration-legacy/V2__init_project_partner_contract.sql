-- V2__init_project_partner_contract.sql
-- 建筑工程总包项目全过程管理系统 - 项目/合作方/合同业务核心表
-- 数据库：MySQL 8.0+
-- ID 策略：后端雪花 ID / ASSIGN_ID，数据库不使用 AUTO_INCREMENT

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 项目表
-- ----------------------------
CREATE TABLE IF NOT EXISTS pm_project (
    id BIGINT NOT NULL COMMENT '项目ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    org_id BIGINT NULL COMMENT '所属组织/项目部ID',
    project_code VARCHAR(64) NOT NULL COMMENT '项目编号',
    project_name VARCHAR(200) NOT NULL COMMENT '项目名称',
    project_type VARCHAR(50) NULL COMMENT '项目类型',
    project_address VARCHAR(300) NULL COMMENT '项目地址',
    owner_unit VARCHAR(200) NULL COMMENT '建设单位',
    supervisor_unit VARCHAR(200) NULL COMMENT '监理单位',
    design_unit VARCHAR(200) NULL COMMENT '设计单位',
    contract_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '总包合同金额',
    target_cost DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '目标成本',
    planned_start_date DATE NULL COMMENT '计划开工日期',
    planned_end_date DATE NULL COMMENT '计划竣工日期',
    actual_start_date DATE NULL COMMENT '实际开工日期',
    actual_end_date DATE NULL COMMENT '实际竣工日期',
    project_manager_id BIGINT NULL COMMENT '项目经理用户ID',
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT' COMMENT '项目状态',
    approval_status VARCHAR(50) NULL COMMENT '审批状态',
    created_by BIGINT NULL COMMENT '创建人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '更新人',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    remark VARCHAR(500) NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pm_project_code (tenant_id, project_code),
    KEY idx_pm_project_name (project_name),
    KEY idx_pm_project_status (status),
    KEY idx_pm_project_manager (project_manager_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目表';

-- ----------------------------
-- 合作方表
-- ----------------------------
CREATE TABLE IF NOT EXISTS md_partner (
    id BIGINT NOT NULL COMMENT '合作方ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    partner_code VARCHAR(64) NOT NULL COMMENT '合作方编号',
    partner_name VARCHAR(200) NOT NULL COMMENT '合作方名称',
    partner_type VARCHAR(50) NOT NULL COMMENT '供应商/分包商/租赁商/服务商等',
    credit_code VARCHAR(100) NULL COMMENT '统一社会信用代码',
    legal_person VARCHAR(100) NULL COMMENT '法人',
    contact_name VARCHAR(100) NULL COMMENT '联系人',
    contact_phone VARCHAR(50) NULL COMMENT '联系电话',
    bank_name VARCHAR(200) NULL COMMENT '开户行',
    bank_account VARCHAR(100) NULL COMMENT '银行账号',
    qualification_level VARCHAR(100) NULL COMMENT '资质等级',
    blacklist_flag TINYINT NOT NULL DEFAULT 0 COMMENT '是否黑名单：0否，1是',
    risk_level VARCHAR(50) NULL COMMENT '风险等级',
    status VARCHAR(50) NOT NULL DEFAULT 'ENABLE' COMMENT '状态',
    created_by BIGINT NULL COMMENT '创建人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '更新人',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    remark VARCHAR(500) NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_md_partner_code (tenant_id, partner_code),
    KEY idx_md_partner_name (partner_name),
    KEY idx_md_partner_type (partner_type),
    KEY idx_md_partner_blacklist (blacklist_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='合作方表';

-- ----------------------------
-- 合同主表
-- ----------------------------
CREATE TABLE IF NOT EXISTS ct_contract (
    id BIGINT NOT NULL COMMENT '合同ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    org_id BIGINT NULL COMMENT '所属组织ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    partner_id BIGINT NULL COMMENT '合作方ID',
    contract_code VARCHAR(64) NOT NULL COMMENT '合同编号',
    contract_name VARCHAR(200) NOT NULL COMMENT '合同名称',
    contract_type VARCHAR(50) NOT NULL COMMENT '总包/分包/采购/租赁/服务等',
    party_a VARCHAR(200) NULL COMMENT '甲方',
    party_b VARCHAR(200) NULL COMMENT '乙方',
    contract_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '原合同金额',
    current_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '当前合同金额=原合同金额+已生效变更',
    tax_rate DECIMAL(6,2) NOT NULL DEFAULT 0 COMMENT '税率',
    tax_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '税额',
    amount_without_tax DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '不含税金额',
    signed_date DATE NULL COMMENT '签订日期',
    start_date DATE NULL COMMENT '合同开始日期',
    end_date DATE NULL COMMENT '合同结束日期',
    payment_method VARCHAR(100) NULL COMMENT '付款方式',
    settlement_method VARCHAR(100) NULL COMMENT '结算方式',
    warranty_rate DECIMAL(6,2) NOT NULL DEFAULT 0 COMMENT '质保金比例',
    warranty_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '质保金金额',
    contract_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT' COMMENT '合同业务状态',
    approval_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT' COMMENT '审批状态',
    created_by BIGINT NULL COMMENT '创建人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '更新人',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    remark VARCHAR(500) NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ct_contract_code (tenant_id, contract_code),
    KEY idx_ct_contract_project (project_id),
    KEY idx_ct_contract_partner (partner_id),
    KEY idx_ct_contract_type (contract_type),
    KEY idx_ct_contract_status (contract_status, approval_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='合同主表';

-- ----------------------------
-- 合同明细表
-- ----------------------------
CREATE TABLE IF NOT EXISTS ct_contract_item (
    id BIGINT NOT NULL COMMENT '合同明细ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    contract_id BIGINT NOT NULL COMMENT '合同ID',
    item_code VARCHAR(64) NULL COMMENT '明细编号',
    item_name VARCHAR(200) NOT NULL COMMENT '明细名称/清单项名称',
    item_spec VARCHAR(300) NULL COMMENT '规格型号',
    unit VARCHAR(50) NULL COMMENT '计量单位',
    quantity DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '数量',
    unit_price DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '单价',
    amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '金额（含税）',
    tax_rate DECIMAL(6,2) NOT NULL DEFAULT 0 COMMENT '税率',
    tax_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '税额',
    amount_without_tax DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '不含税金额',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    PRIMARY KEY (id),
    KEY idx_ct_contract_item_contract (contract_id, sort_order),
    KEY idx_ct_contract_item_code (item_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='合同明细表';

-- ----------------------------
-- 合同付款条款表
-- ----------------------------
CREATE TABLE IF NOT EXISTS ct_contract_payment_term (
    id BIGINT NOT NULL COMMENT '付款条款ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    contract_id BIGINT NOT NULL COMMENT '合同ID',
    term_name VARCHAR(200) NOT NULL COMMENT '条款名称/付款节点名称',
    payment_ratio DECIMAL(6,2) NOT NULL DEFAULT 0 COMMENT '付款比例(%)',
    payment_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '付款金额',
    payment_condition VARCHAR(500) NULL COMMENT '付款条件',
    planned_date DATE NULL COMMENT '计划付款日期',
    actual_date DATE NULL COMMENT '实际付款日期',
    term_status VARCHAR(50) NOT NULL DEFAULT 'PENDING' COMMENT '条款状态：PENDING待付，PARTIAL部分付款，PAID已付清',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    PRIMARY KEY (id),
    KEY idx_ct_payment_term_contract (contract_id, sort_order),
    KEY idx_ct_payment_term_status (term_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='合同付款条款表';

SET FOREIGN_KEY_CHECKS = 1;
