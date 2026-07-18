-- V33__init_org_tables.sql
-- 建筑工程总包项目全过程管理系统 - 组织架构表
-- 数据库：MySQL 8.0+
-- ID 策略：后端雪花 ID / ASSIGN_ID，数据库不使用 AUTO_INCREMENT
-- 审计列约定：统一使用 created_time/updated_time（对齐 V22+ 与 MyMetaObjectHandler）

-- ----------------------------
-- 公司表
-- ----------------------------
CREATE TABLE IF NOT EXISTS org_company (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    company_code VARCHAR(50) NOT NULL,
    company_name VARCHAR(200) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ENABLE',
    created_by BIGINT NULL,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark TEXT NULL,
    PRIMARY KEY (id),
    KEY idx_oc_tenant (tenant_id),
    UNIQUE (tenant_id, company_code)
);

-- ----------------------------
-- 部门表（自引用树）
-- ----------------------------
CREATE TABLE IF NOT EXISTS org_department (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    company_id BIGINT NOT NULL,
    parent_id BIGINT NULL DEFAULT NULL,
    dept_code VARCHAR(50) NOT NULL,
    dept_name VARCHAR(200) NOT NULL,
    order_num INT NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'ENABLE',
    created_by BIGINT NULL,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark TEXT NULL,
    PRIMARY KEY (id),
    KEY idx_od_tenant (tenant_id),
    KEY idx_od_company (company_id),
    KEY idx_od_parent (parent_id)
);

-- ----------------------------
-- 岗位表（HR 头衔，不含权限语义）
-- ----------------------------
CREATE TABLE IF NOT EXISTS org_position (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    position_code VARCHAR(50) NOT NULL,
    position_name VARCHAR(200) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ENABLE',
    created_by BIGINT NULL,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark TEXT NULL,
    PRIMARY KEY (id),
    KEY idx_op_tenant (tenant_id),
    UNIQUE (tenant_id, position_code)
);
