-- H2 Seed data for local development

-- Admin user (password: admin123, BCrypt)
INSERT INTO sys_user (id, tenant_id, username, password, real_name, phone, email, status, is_admin)
VALUES (1, 0, 'admin', '$2b$10$AhU0o3.v7v5GsriXoJ7S2uSGP0o8nHeoIbgoDvjIS2LZR1gRpHsO6', '系统管理员', '13800000000', 'admin@cgc.com', 'ENABLE', 1);

-- Roles
INSERT INTO sys_role (id, tenant_id, role_code, role_name, role_type, status)
VALUES (1, 0, 'SUPER_ADMIN', '超级管理员', 'SYSTEM', 'ENABLE');
INSERT INTO sys_role (id, tenant_id, role_code, role_name, role_type, status)
VALUES (2, 0, 'PROJECT_MANAGER', '项目经理', 'BUSINESS', 'ENABLE');
INSERT INTO sys_role (id, tenant_id, role_code, role_name, role_type, status)
VALUES (3, 0, 'USER', '普通用户', 'BUSINESS', 'ENABLE');

-- Menus
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, icon, order_num, status, visible)
VALUES (1, 0, 0, '首页', 'MENU', '/dashboard', 'dashboard/index', 'HomeOutlined', 1, 'ENABLE', 1);
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, icon, order_num, status, visible)
VALUES (2, 0, 0, '项目管理', 'MENU', '/project', 'project/index', 'ProjectOutlined', 2, 'ENABLE', 1);
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, icon, order_num, status, visible)
VALUES (3, 0, 0, '合同管理', 'DIR', '/contract', NULL, 'FileTextOutlined', 3, 'ENABLE', 1);
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, icon, order_num, status, visible)
VALUES (31, 0, 3, '合同台账', 'MENU', '/contract/ledger', 'contract/ContractLedgerPage', NULL, 1, 'ENABLE', 1);
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, icon, order_num, status, visible)
VALUES (4, 0, 0, '系统设置', 'MENU', '/system', 'system/index', 'SettingOutlined', 9, 'ENABLE', 1);

-- User-Role: admin -> super admin
INSERT INTO sys_user_role (id, user_id, role_id) VALUES (1, 1, 1);

-- Role-Menu: super admin gets all menus
INSERT INTO sys_role_menu (id, role_id, menu_id) VALUES (1, 1, 1);
INSERT INTO sys_role_menu (id, role_id, menu_id) VALUES (2, 1, 2);
INSERT INTO sys_role_menu (id, role_id, menu_id) VALUES (3, 1, 3);
INSERT INTO sys_role_menu (id, role_id, menu_id) VALUES (4, 1, 31);
INSERT INTO sys_role_menu (id, role_id, menu_id) VALUES (5, 1, 4);
