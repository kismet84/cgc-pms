-- V24__enhance_settlement_alert_summary.sql
-- 建筑工程总包项目全过程管理系统 - 结算字段增强 + 预警记录表 + 成本汇总索引与关联
-- 数据库：MySQL 8.0+
-- ID 策略：后端雪花 ID / ASSIGN_ID，数据库不使用 AUTO_INCREMENT

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- A. 结算表字段增强（ALTER TABLE，不重建）
--    基于 V12 已创建的 stl_settlement / stl_settlement_item 表
-- ============================================================

-- 结算主表：新增未付金额、质保金金额、结算状态、定案日期
ALTER TABLE stl_settlement
    ADD COLUMN unpaid_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '未付金额',
    ADD COLUMN warranty_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '质保金金额',
    ADD COLUMN settlement_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT' COMMENT '结算状态：DRAFT草稿，FINALIZED已定案，CANCELLED已作废',
    ADD COLUMN finalized_at DATETIME NULL COMMENT '定案时间';

-- 结算明细表：新增来源类型和来源ID，支持结算下钻反查（刚需）
ALTER TABLE stl_settlement_item
    ADD COLUMN source_type VARCHAR(50) NULL COMMENT '来源类型：MAT_RECEIPT材料验收，SUB_MEASURE分包计量，VAR_ORDER变更签证，CT_CONTRACT合同',
    ADD COLUMN source_id BIGINT NULL COMMENT '来源单据ID';

-- ============================================================
-- B. 预警记录表（CREATE TABLE IF NOT EXISTS）
-- ============================================================

CREATE TABLE IF NOT EXISTS alert_log (
    id BIGINT NOT NULL COMMENT '预警ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    rule_type VARCHAR(100) NOT NULL COMMENT '预警规则类型',
    severity VARCHAR(20) NOT NULL DEFAULT 'MEDIUM' COMMENT '严重程度：HIGH高，MEDIUM中，LOW低',
    message TEXT NULL COMMENT '预警消息内容',
    triggered_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '触发时间',
    is_read TINYINT NOT NULL DEFAULT 0 COMMENT '是否已读：0未读，1已读',
    created_by BIGINT NULL COMMENT '创建人',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '更新人',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    remark VARCHAR(500) NULL COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_alert_project (project_id),
    KEY idx_alert_tenant (tenant_id),
    KEY idx_alert_read (is_read),
    KEY idx_alert_triggered (triggered_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预警记录表';

-- ============================================================
-- C. 成本汇总表增强：新增目标成本版本关联 + 驾驶舱索引
--    基于 V12 已创建的 cost_summary 表
-- ============================================================

-- 新增 cost_target_id 关联生效的目标成本版本
ALTER TABLE cost_summary
    ADD COLUMN cost_target_id BIGINT NULL COMMENT '关联的目标成本版本ID，关联cost_target.id' AFTER cost_subject_id;

-- 安全创建索引（MySQL 8.0 不支持 CREATE INDEX IF NOT EXISTS，通过 INFORMATION_SCHEMA 条件判断实现幂等）
SELECT COUNT(*) INTO @idx_exists FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'cost_summary' AND index_name = 'idx_summary_tenant_project';

SET @sql = IF(@idx_exists = 0,
    'CREATE INDEX idx_summary_tenant_project ON cost_summary(tenant_id, project_id)',
    'SELECT "Index idx_summary_tenant_project already exists" AS msg'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @idx_exists FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'cost_summary' AND index_name = 'idx_summary_subject';

SET @sql = IF(@idx_exists = 0,
    'CREATE INDEX idx_summary_subject ON cost_summary(project_id, cost_subject_id)',
    'SELECT "Index idx_summary_subject already exists" AS msg'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET FOREIGN_KEY_CHECKS = 1;
