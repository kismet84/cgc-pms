-- V1__init_system_tables.sql
-- 建筑工程总包项目全过程管理系统 - 系统/权限相关表
-- 数据库：MySQL 8.0+
-- ID 策略：后端雪花 ID / ASSIGN_ID，数据库不使用 AUTO_INCREMENT

-- ----------------------------
-- 用户表
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    username VARCHAR(64) NOT NULL,
    password VARCHAR(200) NOT NULL,
    real_name VARCHAR(100) NULL,
    phone VARCHAR(50) NULL,
    email VARCHAR(128) NULL,
    avatar VARCHAR(500) NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ENABLE',
    is_admin SMALLINT NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE (tenant_id, username),
    KEY idx_sys_user_real_name (real_name),
    KEY idx_sys_user_phone (phone),
    KEY idx_sys_user_status (status)
);

-- ----------------------------
-- 角色表
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    role_code VARCHAR(64) NOT NULL,
    role_name VARCHAR(100) NOT NULL,
    role_type VARCHAR(50) NOT NULL DEFAULT 'CUSTOM',
    status VARCHAR(50) NOT NULL DEFAULT 'ENABLE',
    data_scope VARCHAR(50) NOT NULL DEFAULT 'SELF',
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE (tenant_id, role_code),
    KEY idx_sys_role_name (role_name),
    KEY idx_sys_role_status (status)
);

-- ----------------------------
-- 菜单/权限表
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_menu (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    parent_id BIGINT NOT NULL DEFAULT 0,
    menu_name VARCHAR(100) NOT NULL,
    menu_type VARCHAR(20) NOT NULL,
    path VARCHAR(300) NULL,
    component VARCHAR(300) NULL,
    perms VARCHAR(200) NULL,
    icon VARCHAR(100) NULL,
    order_num INT NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'ENABLE',
    visible SMALLINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_flag SMALLINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_sys_menu_parent (parent_id),
    KEY idx_sys_menu_type (menu_type),
    KEY idx_sys_menu_order (order_num)
);

-- ----------------------------
-- 用户-角色关联表
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (user_id, role_id),
    KEY idx_sys_user_role_role (role_id)
);

-- ----------------------------
-- 角色-菜单关联表
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_role_menu (
    id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (role_id, menu_id),
    KEY idx_sys_role_menu_menu (menu_id)
);
