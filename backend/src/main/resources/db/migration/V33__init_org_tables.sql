-- V33__init_org_tables.sql
-- 建筑工程总包项目全过程管理系统 - 组织架构表
-- 数据库：MySQL 8.0+
-- ID 策略：后端雪花 ID / ASSIGN_ID，数据库不使用 AUTO_INCREMENT
-- 审计列约定：统一使用 created_time/updated_time（对齐 V22+ 与 MyMetaObjectHandler）

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 公司表
-- ----------------------------
CREATE TABLE IF NOT EXISTS org_company (
    id BIGINT NOT NULL COMMENT '公司ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    company_code VARCHAR(50) NOT NULL COMMENT '公司编码',
    company_name VARCHAR(200) NOT NULL COMMENT '公司名称',
    status VARCHAR(50) NOT NULL DEFAULT 'ENABLE' COMMENT '状态：ENABLE启用，DISABLE禁用',
    created_by BIGINT NULL COMMENT '创建人',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '更新人',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    remark TEXT NULL COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_oc_tenant (tenant_id),
    UNIQUE KEY uk_oc_tenant_code (tenant_id, company_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公司表';

-- ----------------------------
-- 部门表（自引用树）
-- ----------------------------
CREATE TABLE IF NOT EXISTS org_department (
    id BIGINT NOT NULL COMMENT '部门ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    company_id BIGINT NOT NULL COMMENT '所属公司ID',
    parent_id BIGINT NULL DEFAULT NULL COMMENT '上级部门ID，NULL为根部门',
    dept_code VARCHAR(50) NOT NULL COMMENT '部门编码',
    dept_name VARCHAR(200) NOT NULL COMMENT '部门名称',
    order_num INT NOT NULL DEFAULT 0 COMMENT '排序号',
    status VARCHAR(50) NOT NULL DEFAULT 'ENABLE' COMMENT '状态：ENABLE启用，DISABLE禁用',
    created_by BIGINT NULL COMMENT '创建人',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '更新人',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    remark TEXT NULL COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_od_tenant (tenant_id),
    KEY idx_od_company (company_id),
    KEY idx_od_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='部门表（自引用树形结构）';

-- ----------------------------
-- 岗位表（HR 头衔，不含权限语义）
-- ----------------------------
CREATE TABLE IF NOT EXISTS org_position (
    id BIGINT NOT NULL COMMENT '岗位ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    position_code VARCHAR(50) NOT NULL COMMENT '岗位编码',
    position_name VARCHAR(200) NOT NULL COMMENT '岗位名称',
    status VARCHAR(50) NOT NULL DEFAULT 'ENABLE' COMMENT '状态：ENABLE启用，DISABLE禁用',
    created_by BIGINT NULL COMMENT '创建人',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '更新人',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    remark TEXT NULL COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_op_tenant (tenant_id),
    UNIQUE KEY uk_op_tenant_code (tenant_id, position_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='岗位表';

SET FOREIGN_KEY_CHECKS = 1;
