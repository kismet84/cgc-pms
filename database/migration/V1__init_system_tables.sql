-- V1__init_system_tables.sql
-- 建筑工程总包项目全过程管理系统 - 系统/权限相关表
-- 数据库：MySQL 8.0+
-- ID 策略：后端雪花 ID / ASSIGN_ID，数据库不使用 AUTO_INCREMENT

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 用户表
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT NOT NULL COMMENT '用户ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    username VARCHAR(64) NOT NULL COMMENT '登录账号',
    password VARCHAR(200) NOT NULL COMMENT '登录密码（加密存储）',
    real_name VARCHAR(100) NULL COMMENT '真实姓名',
    phone VARCHAR(50) NULL COMMENT '手机号',
    email VARCHAR(128) NULL COMMENT '邮箱',
    avatar VARCHAR(500) NULL COMMENT '头像URL',
    status VARCHAR(50) NOT NULL DEFAULT 'ENABLE' COMMENT '状态：ENABLE启用，DISABLE禁用',
    is_admin TINYINT NOT NULL DEFAULT 0 COMMENT '是否超级管理员：0否，1是',
    created_by BIGINT NULL COMMENT '创建人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '更新人',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    remark VARCHAR(500) NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_username (tenant_id, username),
    KEY idx_sys_user_real_name (real_name),
    KEY idx_sys_user_phone (phone),
    KEY idx_sys_user_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统用户表';

-- ----------------------------
-- 角色表
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT NOT NULL COMMENT '角色ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    role_code VARCHAR(64) NOT NULL COMMENT '角色编码',
    role_name VARCHAR(100) NOT NULL COMMENT '角色名称',
    role_type VARCHAR(50) NOT NULL DEFAULT 'CUSTOM' COMMENT '角色类型：SYSTEM系统内置，CUSTOM自定义',
    status VARCHAR(50) NOT NULL DEFAULT 'ENABLE' COMMENT '状态：ENABLE启用，DISABLE禁用',
    data_scope VARCHAR(50) NOT NULL DEFAULT 'SELF' COMMENT '数据范围：ALL全部，DEPT本部门，DEPT_AND_CHILD本部门及以下，SELF仅本人，CUSTOM自定义',
    created_by BIGINT NULL COMMENT '创建人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '更新人',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    remark VARCHAR(500) NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_code (tenant_id, role_code),
    KEY idx_sys_role_name (role_name),
    KEY idx_sys_role_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统角色表';

-- ----------------------------
-- 菜单/权限表
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_menu (
    id BIGINT NOT NULL COMMENT '菜单ID，雪花ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '父菜单ID，0表示根节点',
    menu_name VARCHAR(100) NOT NULL COMMENT '菜单名称',
    menu_type VARCHAR(20) NOT NULL COMMENT '菜单类型：DIR目录，MENU菜单，BUTTON按钮',
    path VARCHAR(300) NULL COMMENT '路由地址',
    component VARCHAR(300) NULL COMMENT '组件路径',
    perms VARCHAR(200) NULL COMMENT '权限标识',
    icon VARCHAR(100) NULL COMMENT '菜单图标',
    order_num INT NOT NULL DEFAULT 0 COMMENT '显示顺序',
    status VARCHAR(50) NOT NULL DEFAULT 'ENABLE' COMMENT '状态：ENABLE启用，DISABLE禁用',
    visible TINYINT NOT NULL DEFAULT 1 COMMENT '是否可见：0隐藏，1显示',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
    PRIMARY KEY (id),
    KEY idx_sys_menu_parent (parent_id),
    KEY idx_sys_menu_type (menu_type),
    KEY idx_sys_menu_order (order_num)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统菜单权限表';

-- ----------------------------
-- 用户-角色关联表
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT NOT NULL COMMENT '主键ID，雪花ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_role (user_id, role_id),
    KEY idx_sys_user_role_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色关联表';

-- ----------------------------
-- 角色-菜单关联表
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_role_menu (
    id BIGINT NOT NULL COMMENT '主键ID，雪花ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    menu_id BIGINT NOT NULL COMMENT '菜单ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_menu (role_id, menu_id),
    KEY idx_sys_role_menu_menu (menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色菜单关联表';

SET FOREIGN_KEY_CHECKS = 1;
