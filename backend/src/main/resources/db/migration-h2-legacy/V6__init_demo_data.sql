-- V6__init_demo_data.sql
-- 建筑工程总包项目全过程管理系统 - 演示/测试数据
-- 数据库：MySQL 8.0+
-- ID 策略：后端雪花 ID / ASSIGN_ID，数据库不使用 AUTO_INCREMENT
-- 说明：演示数据使用固定 ID（预留低位区段），避免与雪花 ID 冲突
--      管理员账号 admin，初始密码 admin123（BCrypt 加密存储，登录后请尽快修改）

-- ============================================================
-- 1. 管理员用户
--    密码明文 admin123，下方为 BCrypt($2a$10$) 哈希
-- ============================================================
INSERT INTO sys_user (id, tenant_id, username, password, real_name, phone, email, status, is_admin, created_by, remark) VALUES
(1, 0, 'admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '系统管理员', '13800000000', 'admin@cgc-pms.com', 'ENABLE', 1, 1, '系统内置超级管理员，初始密码 admin123');

-- ============================================================
-- 2. 角色：超级管理员 / 项目经理 / 普通用户
-- ============================================================
INSERT INTO sys_role (id, tenant_id, role_code, role_name, role_type, status, data_scope, created_by, remark) VALUES
(1, 0, 'SUPER_ADMIN',     '超级管理员', 'SYSTEM', 'ENABLE', 'ALL',  1, '系统内置，拥有全部权限'),
(2, 0, 'PROJECT_MANAGER', '项目经理',   'CUSTOM', 'ENABLE', 'DEPT_AND_CHILD', 1, '项目经理角色'),
(3, 0, 'COMMON_USER',     '普通用户',   'CUSTOM', 'ENABLE', 'SELF', 1, '普通业务用户');

-- ============================================================
-- 3. 菜单：仪表盘 / 项目管理 / 合同管理 / 合作方管理 / 系统设置（含子菜单）
-- ============================================================
-- 一级目录
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible) VALUES
(1, 0, 0, '仪表盘',     'MENU', '/dashboard',  'dashboard/index', 'dashboard:view', 'dashboard', 1, 'ENABLE', 1),
(2, 0, 0, '项目管理',   'DIR',  '/project',    NULL,              NULL,             'project',   2, 'ENABLE', 1),
(3, 0, 0, '合同管理',   'DIR',  '/contract',   NULL,              NULL,             'contract',  3, 'ENABLE', 1),
(4, 0, 0, '合作方管理', 'DIR',  '/partner',    NULL,              NULL,             'partner',   4, 'ENABLE', 1),
(5, 0, 0, '系统设置',   'DIR',  '/system',     NULL,              NULL,             'setting',   5, 'ENABLE', 1);

-- 项目管理子菜单
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible) VALUES
(201, 0, 2, '项目列表', 'MENU',   'list',   'project/list/index', 'project:list',   'list', 1, 'ENABLE', 1),
(202, 0, 201, '新增项目', 'BUTTON', NULL,    NULL,                 'project:add',    NULL,   1, 'ENABLE', 1),
(203, 0, 201, '编辑项目', 'BUTTON', NULL,    NULL,                 'project:edit',   NULL,   2, 'ENABLE', 1),
(204, 0, 201, '删除项目', 'BUTTON', NULL,    NULL,                 'project:delete', NULL,   3, 'ENABLE', 1);

-- 合同管理子菜单
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible) VALUES
(301, 0, 3, '合同列表', 'MENU',   'list',   'contract/list/index', 'contract:list',   'list', 1, 'ENABLE', 1),
(302, 0, 301, '新增合同', 'BUTTON', NULL,    NULL,                  'contract:add',    NULL,   1, 'ENABLE', 1),
(303, 0, 301, '编辑合同', 'BUTTON', NULL,    NULL,                  'contract:edit',   NULL,   2, 'ENABLE', 1),
(304, 0, 301, '删除合同', 'BUTTON', NULL,    NULL,                  'contract:delete', NULL,   3, 'ENABLE', 1),
(305, 0, 301, '提交审批', 'BUTTON', NULL,    NULL,                  'contract:submit', NULL,   4, 'ENABLE', 1);

-- 合作方管理子菜单
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible) VALUES
(401, 0, 4, '合作方列表', 'MENU',   'list', 'partner/list/index', 'partner:list',   'list', 1, 'ENABLE', 1),
(402, 0, 401, '新增合作方', 'BUTTON', NULL,  NULL,                 'partner:add',    NULL,   1, 'ENABLE', 1),
(403, 0, 401, '编辑合作方', 'BUTTON', NULL,  NULL,                 'partner:edit',   NULL,   2, 'ENABLE', 1),
(404, 0, 401, '删除合作方', 'BUTTON', NULL,  NULL,                 'partner:delete', NULL,   3, 'ENABLE', 1);

-- 系统设置子菜单
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible) VALUES
(501, 0, 5, '用户管理', 'MENU', 'user', 'system/user/index', 'system:user:list', 'user', 1, 'ENABLE', 1),
(502, 0, 5, '角色管理', 'MENU', 'role', 'system/role/index', 'system:role:list', 'role', 2, 'ENABLE', 1),
(503, 0, 5, '菜单管理', 'MENU', 'menu', 'system/menu/index', 'system:menu:list', 'menu', 3, 'ENABLE', 1),
(504, 0, 5, '字典管理', 'MENU', 'dict', 'system/dict/index', 'system:dict:list', 'dict', 4, 'ENABLE', 1);

-- ============================================================
-- 4. 用户-角色：admin -> 超级管理员
-- ============================================================
INSERT INTO sys_user_role (id, user_id, role_id) VALUES
(1, 1, 1);

-- ============================================================
-- 5. 角色-菜单：超级管理员拥有全部菜单
-- ============================================================
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT id, 1, id FROM sys_menu WHERE deleted_flag = 0;

-- 业务演示数据（项目/合作方/合同/审批模板）已移除
-- 如需恢复，请从 git history 中还原 V6__init_demo_data.sql
