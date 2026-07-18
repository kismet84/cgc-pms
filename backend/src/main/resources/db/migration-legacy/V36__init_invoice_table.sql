-- V36__init_invoice_table.sql
-- 建筑工程总包项目全过程管理系统 - 发票管理表
-- 数据库：MySQL 8.0+
-- ID 策略：后端雪花 ID / ASSIGN_ID，数据库不使用 AUTO_INCREMENT
-- 说明：发票关联付款记录（pay_record），核验为状态字段切换，不走审批链

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 发票表
-- ----------------------------
CREATE TABLE IF NOT EXISTS pay_invoice (
    id BIGINT NOT NULL COMMENT '发票ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    pay_application_id BIGINT NULL COMMENT '付款申请ID，关联pay_application.id',
    pay_record_id BIGINT NULL COMMENT '付款记录ID，关联pay_record.id',
    invoice_no VARCHAR(100) NOT NULL COMMENT '发票号码',
    invoice_type VARCHAR(50) NOT NULL DEFAULT 'VAT_SPECIAL' COMMENT '发票类型：VAT_SPECIAL增值税专票，VAT_NORMAL增值税普票，OTHER其他',
    invoice_amount DECIMAL(18,2) NOT NULL COMMENT '发票金额',
    tax_rate DECIMAL(5,2) NULL COMMENT '税率（百分比，如13.00）',
    tax_amount DECIMAL(18,2) NULL COMMENT '税额',
    invoice_date DATE NULL COMMENT '开票日期',
    verify_status VARCHAR(50) NOT NULL DEFAULT 'PENDING' COMMENT '核验状态：PENDING待核验，VERIFIED已认证，ABNORMAL异常',
    created_by BIGINT NULL COMMENT '创建人',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '更新人',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    remark TEXT NULL COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_pi_tenant (tenant_id),
    KEY idx_pi_pay_app (pay_application_id),
    KEY idx_pi_pay_record (pay_record_id),
    UNIQUE KEY uk_pi_tenant_invoice_no (tenant_id, invoice_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='发票表';

SET FOREIGN_KEY_CHECKS = 1;
