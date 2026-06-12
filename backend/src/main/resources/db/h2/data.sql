-- H2 Seed data for local development

-- Admin user (password: admin123, BCrypt)
MERGE INTO sys_user (id, tenant_id, username, password, real_name, phone, email, status, is_admin) KEY(id)
VALUES (1, 0, 'admin', '$2b$10$AhU0o3.v7v5GsriXoJ7S2uSGP0o8nHeoIbgoDvjIS2LZR1gRpHsO6', '系统管理员', '13800000000', 'admin@cgc.com', 'ENABLE', 1);

-- Roles
MERGE INTO sys_role (id, tenant_id, role_code, role_name, role_type, status) KEY(id)
VALUES (1, 0, 'SUPER_ADMIN', '超级管理员', 'SYSTEM', 'ENABLE');
MERGE INTO sys_role (id, tenant_id, role_code, role_name, role_type, status) KEY(id)
VALUES (2, 0, 'PROJECT_MANAGER', '项目经理', 'BUSINESS', 'ENABLE');
MERGE INTO sys_role (id, tenant_id, role_code, role_name, role_type, status) KEY(id)
VALUES (3, 0, 'USER', '普通用户', 'BUSINESS', 'ENABLE');

-- Menus
MERGE INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, icon, order_num, status, visible) KEY(id)
VALUES (1, 0, 0, '首页', 'MENU', '/dashboard', 'dashboard/index', 'HomeOutlined', 1, 'ENABLE', 1);
MERGE INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, icon, order_num, status, visible) KEY(id)
VALUES (2, 0, 0, '项目管理', 'MENU', '/project', 'project/index', 'ProjectOutlined', 2, 'ENABLE', 1);
MERGE INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, icon, order_num, status, visible) KEY(id)
VALUES (3, 0, 0, '合同管理', 'DIR', '/contract', NULL, 'FileTextOutlined', 3, 'ENABLE', 1);
MERGE INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, icon, order_num, status, visible) KEY(id)
VALUES (31, 0, 3, '合同台账', 'MENU', '/contract/ledger', 'contract/ContractLedgerPage', NULL, 1, 'ENABLE', 1);
MERGE INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, icon, order_num, status, visible) KEY(id)
VALUES (4, 0, 0, '系统设置', 'MENU', '/system', 'system/index', 'SettingOutlined', 9, 'ENABLE', 1);

-- User-Role: admin -> super admin
MERGE INTO sys_user_role (id, user_id, role_id) KEY(id) VALUES (1, 1, 1);

-- Role-Menu: super admin gets all menus
MERGE INTO sys_role_menu (id, role_id, menu_id) KEY(id) VALUES (1, 1, 1);
MERGE INTO sys_role_menu (id, role_id, menu_id) KEY(id) VALUES (2, 1, 2);
MERGE INTO sys_role_menu (id, role_id, menu_id) KEY(id) VALUES (3, 1, 3);
MERGE INTO sys_role_menu (id, role_id, menu_id) KEY(id) VALUES (4, 1, 31);
MERGE INTO sys_role_menu (id, role_id, menu_id) KEY(id) VALUES (5, 1, 4);

-- Test project (Phase 1)
MERGE INTO pm_project (id, tenant_id, project_code, project_name, project_type, status, approval_status) KEY(id)
VALUES (100, 0, 'PRJ-TEST-001', '测试项目', '施工总承包', '进行中', '已批准');

-- Test partner (Phase 1)
MERGE INTO md_partner (id, tenant_id, partner_code, partner_name, partner_type, status) KEY(id)
VALUES (100, 0, 'PTN-TEST-001', '测试供应商', '供应商', 'ENABLE');

-- Test contract (Phase 1)
MERGE INTO ct_contract (id, tenant_id, project_id, partner_id, contract_code, contract_name, contract_type, contract_amount, contract_status, approval_status) KEY(id)
VALUES (100, 0, 100, 100, 'CT-TEST-001', '测试合同', 'MAIN', 1000000.00, 'DRAFT', 'DRAFT');

-- ====== V6 Demo Data (Phase 2+3 test IDs) ======

-- Demo projects (PRJ-2026-001 ONGOING, PRJ-2026-002 DRAFT)
MERGE INTO pm_project (id, tenant_id, project_code, project_name, project_type, contract_amount, target_cost, planned_start_date, planned_end_date, status, approval_status) KEY(id)
VALUES (10001, 0, 'PRJ-2026-001', '城市中心商业综合体总承包工程', '房建工程', 580000000.00, 520000000.00, '2026-03-01', '2028-06-30', 'ONGOING', 'APPROVED');

MERGE INTO pm_project (id, tenant_id, project_code, project_name, project_type, contract_amount, target_cost, planned_start_date, planned_end_date, status, approval_status) KEY(id)
VALUES (10002, 0, 'PRJ-2026-002', '滨江路市政道路改造工程', '市政工程', 128000000.00, 115000000.00, '2026-05-01', '2027-04-30', 'DRAFT', 'DRAFT');

-- Demo partners
MERGE INTO md_partner (id, tenant_id, partner_code, partner_name, partner_type, qualification_level, blacklist_flag, risk_level, status) KEY(id)
VALUES (20001, 0, 'PTN-S-001', '中建商砼材料供应有限公司', 'SUPPLIER', '一级', 0, 'LOW', 'ENABLE');

MERGE INTO md_partner (id, tenant_id, partner_code, partner_name, partner_type, qualification_level, blacklist_flag, risk_level, status) KEY(id)
VALUES (20002, 0, 'PTN-C-001', '宏远建筑劳务分包有限公司', 'SUBCONTRACTOR', '专业承包二级', 0, 'MEDIUM', 'ENABLE');

MERGE INTO md_partner (id, tenant_id, partner_code, partner_name, partner_type, qualification_level, blacklist_flag, risk_level, status) KEY(id)
VALUES (20003, 0, 'PTN-V-001', '智联工程咨询服务有限公司', 'SERVICE_PROVIDER', '甲级', 0, 'LOW', 'ENABLE');

-- Demo contracts (PERFORMING+APPROVED for testing)
MERGE INTO ct_contract (id, tenant_id, project_id, partner_id, contract_code, contract_name, contract_type, contract_amount, current_amount, tax_rate, tax_amount, amount_without_tax, signed_date, start_date, end_date, payment_method, settlement_method, warranty_rate, warranty_amount, contract_status, approval_status) KEY(id)
VALUES (30001, 0, 10001, 20001, 'CT-2026-001', '商砼及钢材采购合同', 'PURCHASE', 45000000.00, 45000000.00, 13.00, 5176991.15, 39823008.85, '2026-03-10', '2026-03-15', '2027-12-31', '按月结算', '验收结算', 3.00, 1350000.00, 'PERFORMING', 'APPROVED');

MERGE INTO ct_contract (id, tenant_id, project_id, partner_id, contract_code, contract_name, contract_type, contract_amount, current_amount, tax_rate, tax_amount, amount_without_tax, signed_date, start_date, end_date, payment_method, settlement_method, warranty_rate, warranty_amount, contract_status, approval_status) KEY(id)
VALUES (30002, 0, 10001, 20002, 'CT-2026-002', '主体结构劳务分包合同', 'SUB', 86000000.00, 86000000.00, 9.00, 7100917.43, 78899082.57, '2026-03-20', '2026-04-01', '2028-03-31', '按进度付款', '竣工结算', 5.00, 4300000.00, 'PERFORMING', 'APPROVED');

MERGE INTO ct_contract (id, tenant_id, project_id, partner_id, contract_code, contract_name, contract_type, contract_amount, current_amount, tax_rate, tax_amount, amount_without_tax, signed_date, start_date, end_date, payment_method, settlement_method, warranty_rate, warranty_amount, contract_status, approval_status) KEY(id)
VALUES (30003, 0, 10001, 20003, 'CT-2026-003', '工程造价咨询服务合同', 'SERVICE', 1200000.00, 1200000.00, 6.00, 67924.53, 1132075.47, '2026-03-25', '2026-04-01', '2028-06-30', '分阶段付款', '一次性结算', 0.00, 0.00, 'DRAFT', 'DRAFT');

-- ====== Workflow Test Data ======

-- Approval template for contract approval
MERGE INTO wf_template (id, tenant_id, template_code, template_name, business_type, enabled) KEY(id)
VALUES (200, 0, 'TPL-CONTRACT-001', '合同审批流程', 'CONTRACT_APPROVAL', 1);

MERGE INTO wf_template (id, tenant_id, template_code, template_name, business_type, enabled, amount_min, amount_max, created_by, remark) KEY(id)
VALUES (50003, 0, 'TPL-MATERIAL-RECEIPT-001', '材料验收审批流程', 'MATERIAL_RECEIPT', 1, 0.00, 999999999.99, 1, '材料验收审批标准流程');

-- Template nodes: 发起人提交 → 项目经理审批(顺序) → 商务+成本会签 → 总经理审批
MERGE INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config) KEY(id)
VALUES (201, 0, 200, 'NODE_MANAGER', '项目经理审批', 1, 'APPROVAL', 'SEQUENTIAL', '{"role":"PROJECT_MANAGER"}');
MERGE INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config) KEY(id)
VALUES (202, 0, 200, 'NODE_COUNTERSIGN', '商务成本会签', 2, 'APPROVAL', 'COUNTERSIGN', '{"role":"BUSINESS_MANAGER"}');
MERGE INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config) KEY(id)
VALUES (203, 0, 200, 'NODE_GM', '总经理审批', 3, 'APPROVAL', 'SEQUENTIAL', '{"role":"GM"}');

MERGE INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, allow_transfer, allow_add_sign, timeout_hours) KEY(id)
VALUES (50301, 0, 50003, 'N1', '项目经理审批', 1, 'APPROVAL', 'SEQUENTIAL', '{"type":"USER","userId":1}', 1, 1, 48);
MERGE INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, allow_transfer, allow_add_sign, timeout_hours) KEY(id)
VALUES (50302, 0, 50003, 'N2', '部门经理审批', 2, 'APPROVAL', 'SEQUENTIAL', '{"type":"USER","userId":1}', 1, 1, 48);
MERGE INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, allow_transfer, allow_add_sign, timeout_hours) KEY(id)
VALUES (50303, 0, 50003, 'N3', '总经理审批', 3, 'APPROVAL', 'SEQUENTIAL', '{"type":"USER","userId":1}', 1, 1, 72);
