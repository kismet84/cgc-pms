-- V97: Seed formal phase 2 dashboard business roles (MySQL)
-- Purpose: make PURCHASE_MANAGER / PRODUCTION_MANAGER see dashboard tabs
-- without relying on SUPER_ADMIN. Keep scope minimal: dashboard entry,
-- project query, and the role-specific dashboard permission only.

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

INSERT IGNORE INTO sys_role
    (id, tenant_id, role_code, role_name, role_type, status, data_scope, created_by, remark)
VALUES
    (7, 0, 'PURCHASE_MANAGER', '采购经理', 'BUSINESS', 'ENABLE', 'ALL', 1, '第二阶段：采购经理默认角色'),
    (8, 0, 'PRODUCTION_MANAGER', '生产经理', 'BUSINESS', 'ENABLE', 'ALL', 1, '第二阶段：生产经理默认角色');

-- Minimal bindings only:
-- 1   = 首页驾驶舱入口
-- 803 = project:query, required by dashboard project selector
-- 813 = dashboard:purchase-manager:view
-- 814 = dashboard:production-manager:view
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id)
VALUES
    (97001, 7, 1),
    (97002, 7, 803),
    (97003, 7, 813),
    (97011, 8, 1),
    (97012, 8, 803),
    (97013, 8, 814);

SET FOREIGN_KEY_CHECKS = 1;
