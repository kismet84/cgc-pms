-- V23__init_contract_change_table.sql
-- 建筑工程总包项目全过程管理系统 - 合同变更表
-- 数据库：MySQL 8.0+
-- ID 策略：后端雪花 ID / ASSIGN_ID，数据库不使用 AUTO_INCREMENT
--
-- CT_CHANGE 是正式合同变更（调整合同金额/工期/条款），不同于 VAR_ORDER（现场签证）。

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 合同变更表
-- ----------------------------
CREATE TABLE IF NOT EXISTS ct_contract_change (
    id BIGINT NOT NULL COMMENT '合同变更ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    contract_id BIGINT NOT NULL COMMENT '合同ID',
    change_code VARCHAR(64) NOT NULL COMMENT '变更编号，自动生成',
    change_name VARCHAR(200) NOT NULL COMMENT '变更名称',
    change_type VARCHAR(50) NOT NULL COMMENT '变更类型：AMOUNT金额变更，DURATION工期变更，CLAUSE条款变更',
    before_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '变更前合同金额',
    change_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '变更金额（正数为增，负数为减）',
    after_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '变更后合同金额',
    reason TEXT NULL COMMENT '变更原因',
    approval_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT' COMMENT '审批状态：DRAFT草稿，APPROVING审批中，APPROVED已通过，REJECTED已驳回',
    effective_flag TINYINT NOT NULL DEFAULT 0 COMMENT '生效标识：0未生效，1已生效',
    cost_generated_flag TINYINT NOT NULL DEFAULT 0 COMMENT '成本生成标识：0未生成，1已生成',
    created_by BIGINT NULL COMMENT '创建人',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '更新人',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    remark VARCHAR(500) NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ct_change_code (tenant_id, change_code),
    KEY idx_change_contract (contract_id),
    KEY idx_change_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='合同变更表';

SET FOREIGN_KEY_CHECKS = 1;
