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

-- Test project
INSERT INTO pm_project (id, tenant_id, project_code, project_name, project_type, status, approval_status)
VALUES (100, 0, 'PRJ-TEST-001', '测试项目', '施工总承包', '进行中', '已批准');

-- Test partner
INSERT INTO md_partner (id, tenant_id, partner_code, partner_name, partner_type, status)
VALUES (100, 0, 'PTN-TEST-001', '测试供应商', '供应商', 'ENABLE');

-- Test contract
INSERT INTO ct_contract (id, tenant_id, project_id, partner_id, contract_code, contract_name, contract_type, contract_amount, contract_status, approval_status)
VALUES (100, 0, 100, 100, 'CT-TEST-001', '测试合同', 'MAIN', 1000000.00, 'DRAFT', 'DRAFT');

-- ====== Workflow Test Data ======

-- Approval template for contract approval
INSERT INTO wf_template (id, tenant_id, template_code, template_name, business_type, enabled)
VALUES (200, 0, 'TPL-CONTRACT-001', '合同审批流程', 'CONTRACT_APPROVAL', 1);

-- Template nodes: 发起人提交 → 项目经理审批(顺序) → 商务+成本会签 → 总经理审批
INSERT INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config)
VALUES (201, 0, 200, 'NODE_MANAGER', '项目经理审批', 1, 'APPROVAL', 'SEQUENTIAL', '{"role":"PROJECT_MANAGER"}');
INSERT INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config)
VALUES (202, 0, 200, 'NODE_COUNTERSIGN', '商务成本会签', 2, 'APPROVAL', 'COUNTERSIGN', '{"role":"BUSINESS_MANAGER"}');
INSERT INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config)
VALUES (203, 0, 200, 'NODE_GM', '总经理审批', 3, 'APPROVAL', 'SEQUENTIAL', '{"role":"GM"}');
