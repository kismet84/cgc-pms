-- V36__init_invoice_table.sql
-- 建筑工程总包项目全过程管理系统 - 发票管理表
-- 数据库：MySQL 8.0+
-- ID 策略：后端雪花 ID / ASSIGN_ID，数据库不使用 AUTO_INCREMENT
-- 说明：发票关联付款记录（pay_record），核验为状态字段切换，不走审批链

-- ----------------------------
-- 发票表
-- ----------------------------
CREATE TABLE IF NOT EXISTS pay_invoice (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    pay_application_id BIGINT NULL,
    pay_record_id BIGINT NULL,
    invoice_no VARCHAR(100) NOT NULL,
    invoice_type VARCHAR(50) NOT NULL DEFAULT 'VAT_SPECIAL',
    invoice_amount DECIMAL(18,2) NOT NULL,
    tax_rate DECIMAL(5,2) NULL,
    tax_amount DECIMAL(18,2) NULL,
    invoice_date DATE NULL,
    verify_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_by BIGINT NULL,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark TEXT NULL,
    PRIMARY KEY (id),
    KEY idx_pi_tenant (tenant_id),
    KEY idx_pi_pay_app (pay_application_id),
    KEY idx_pi_pay_record (pay_record_id),
    UNIQUE (tenant_id, invoice_no)
);
