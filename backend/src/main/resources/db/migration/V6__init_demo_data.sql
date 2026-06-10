-- V6__init_demo_data.sql
-- 建筑工程总包项目全过程管理系统 - 演示/测试数据
-- 数据库：MySQL 8.0+
-- ID 策略：后端雪花 ID / ASSIGN_ID，数据库不使用 AUTO_INCREMENT
-- 说明：演示数据使用固定 ID（预留低位区段），避免与雪花 ID 冲突
--      管理员账号 admin，初始密码 admin123（BCrypt 加密存储，登录后请尽快修改）

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 1. 管理员用户
--    密码明文 admin123，下方为 BCrypt($2a$10$) 哈希
-- ============================================================
INSERT IGNORE INTO sys_user (id, tenant_id, username, password, real_name, phone, email, status, is_admin, created_by, remark) VALUES
(1, 0, 'admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '系统管理员', '13800000000', 'admin@cgc-pms.com', 'ENABLE', 1, 1, '系统内置超级管理员，初始密码 admin123');

-- ============================================================
-- 2. 角色：超级管理员 / 项目经理 / 普通用户
-- ============================================================
INSERT IGNORE INTO sys_role (id, tenant_id, role_code, role_name, role_type, status, data_scope, created_by, remark) VALUES
(1, 0, 'SUPER_ADMIN',     '超级管理员', 'SYSTEM', 'ENABLE', 'ALL',  1, '系统内置，拥有全部权限'),
(2, 0, 'PROJECT_MANAGER', '项目经理',   'CUSTOM', 'ENABLE', 'DEPT_AND_CHILD', 1, '项目经理角色'),
(3, 0, 'COMMON_USER',     '普通用户',   'CUSTOM', 'ENABLE', 'SELF', 1, '普通业务用户');

-- ============================================================
-- 3. 菜单：仪表盘 / 项目管理 / 合同管理 / 合作方管理 / 系统设置（含子菜单）
-- ============================================================
-- 一级目录
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible) VALUES
(1, 0, 0, '仪表盘',     'MENU', '/dashboard',  'dashboard/index', 'dashboard:view', 'dashboard', 1, 'ENABLE', 1),
(2, 0, 0, '项目管理',   'DIR',  '/project',    NULL,              NULL,             'project',   2, 'ENABLE', 1),
(3, 0, 0, '合同管理',   'DIR',  '/contract',   NULL,              NULL,             'contract',  3, 'ENABLE', 1),
(4, 0, 0, '合作方管理', 'DIR',  '/partner',    NULL,              NULL,             'partner',   4, 'ENABLE', 1),
(5, 0, 0, '系统设置',   'DIR',  '/system',     NULL,              NULL,             'setting',   5, 'ENABLE', 1);

-- 项目管理子菜单
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible) VALUES
(201, 0, 2, '项目列表', 'MENU',   'list',   'project/list/index', 'project:list',   'list', 1, 'ENABLE', 1),
(202, 0, 201, '新增项目', 'BUTTON', NULL,    NULL,                 'project:add',    NULL,   1, 'ENABLE', 1),
(203, 0, 201, '编辑项目', 'BUTTON', NULL,    NULL,                 'project:edit',   NULL,   2, 'ENABLE', 1),
(204, 0, 201, '删除项目', 'BUTTON', NULL,    NULL,                 'project:delete', NULL,   3, 'ENABLE', 1);

-- 合同管理子菜单
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible) VALUES
(301, 0, 3, '合同列表', 'MENU',   'list',   'contract/list/index', 'contract:list',   'list', 1, 'ENABLE', 1),
(302, 0, 301, '新增合同', 'BUTTON', NULL,    NULL,                  'contract:add',    NULL,   1, 'ENABLE', 1),
(303, 0, 301, '编辑合同', 'BUTTON', NULL,    NULL,                  'contract:edit',   NULL,   2, 'ENABLE', 1),
(304, 0, 301, '删除合同', 'BUTTON', NULL,    NULL,                  'contract:delete', NULL,   3, 'ENABLE', 1),
(305, 0, 301, '提交审批', 'BUTTON', NULL,    NULL,                  'contract:submit', NULL,   4, 'ENABLE', 1);

-- 合作方管理子菜单
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible) VALUES
(401, 0, 4, '合作方列表', 'MENU',   'list', 'partner/list/index', 'partner:list',   'list', 1, 'ENABLE', 1),
(402, 0, 401, '新增合作方', 'BUTTON', NULL,  NULL,                 'partner:add',    NULL,   1, 'ENABLE', 1),
(403, 0, 401, '编辑合作方', 'BUTTON', NULL,  NULL,                 'partner:edit',   NULL,   2, 'ENABLE', 1),
(404, 0, 401, '删除合作方', 'BUTTON', NULL,  NULL,                 'partner:delete', NULL,   3, 'ENABLE', 1);

-- 系统设置子菜单
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible) VALUES
(501, 0, 5, '用户管理', 'MENU', 'user', 'system/user/index', 'system:user:list', 'user', 1, 'ENABLE', 1),
(502, 0, 5, '角色管理', 'MENU', 'role', 'system/role/index', 'system:role:list', 'role', 2, 'ENABLE', 1),
(503, 0, 5, '菜单管理', 'MENU', 'menu', 'system/menu/index', 'system:menu:list', 'menu', 3, 'ENABLE', 1),
(504, 0, 5, '字典管理', 'MENU', 'dict', 'system/dict/index', 'system:dict:list', 'dict', 4, 'ENABLE', 1);

-- ============================================================
-- 4. 用户-角色：admin -> 超级管理员
-- ============================================================
INSERT IGNORE INTO sys_user_role (id, user_id, role_id) VALUES
(1, 1, 1);

-- ============================================================
-- 5. 角色-菜单：超级管理员拥有全部菜单
-- ============================================================
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id)
SELECT id, 1, id FROM sys_menu WHERE deleted_flag = 0;

-- ============================================================
-- 6. 演示项目（2个）
-- ============================================================
INSERT IGNORE INTO pm_project (id, tenant_id, project_code, project_name, project_type, project_address, owner_unit, supervisor_unit, design_unit, contract_amount, target_cost, planned_start_date, planned_end_date, project_manager_id, status, approval_status, created_by) VALUES
(10001, 0, 'PRJ-2026-001', '城市中心商业综合体总承包工程', '房建工程', '某市高新区科技大道88号', '某市城投置业有限公司', '某建设监理咨询有限公司', '某建筑设计研究院', 580000000.00, 520000000.00, '2026-03-01', '2028-06-30', 1, 'ONGOING', 'APPROVED', 1),
(10002, 0, 'PRJ-2026-002', '滨江路市政道路改造工程',     '市政工程', '某市滨江路全段',         '某市市政建设管理局',     '某市政工程监理公司',     '某市政设计院',     128000000.00, 115000000.00, '2026-05-01', '2027-04-30', 1, 'DRAFT',   'DRAFT',    1);

-- ============================================================
-- 7. 演示合作方（3个：供应商 / 分包商 / 服务商）
-- ============================================================
INSERT IGNORE INTO md_partner (id, tenant_id, partner_code, partner_name, partner_type, credit_code, legal_person, contact_name, contact_phone, bank_name, bank_account, qualification_level, blacklist_flag, risk_level, status, created_by) VALUES
(20001, 0, 'PTN-S-001', '中建商砼材料供应有限公司', 'SUPPLIER',      '91110000MA001A0001', '张建国', '李采购', '13900000001', '中国建设银行某市分行', '6217000000000000001', '一级', 0, 'LOW',    'ENABLE', 1),
(20002, 0, 'PTN-C-001', '宏远建筑劳务分包有限公司', 'SUBCONTRACTOR', '91110000MA001B0002', '王宏远', '赵分包', '13900000002', '中国工商银行某市分行', '6222000000000000002', '专业承包二级', 0, 'MEDIUM', 'ENABLE', 1),
(20003, 0, 'PTN-V-001', '智联工程咨询服务有限公司', 'SERVICE_PROVIDER','91110000MA001C0003', '陈智联', '孙服务', '13900000003', '中国银行某市分行',     '6217000000000000003', '甲级', 0, 'LOW',    'ENABLE', 1);

-- ============================================================
-- 8. 演示合同（3个，关联演示项目与合作方）
-- ============================================================
INSERT IGNORE INTO ct_contract (id, tenant_id, project_id, partner_id, contract_code, contract_name, contract_type, party_a, party_b, contract_amount, current_amount, tax_rate, tax_amount, amount_without_tax, signed_date, start_date, end_date, payment_method, settlement_method, warranty_rate, warranty_amount, contract_status, approval_status, created_by) VALUES
(30001, 0, 10001, 20001, 'CT-2026-001', '商砼及钢材采购合同',     'PURCHASE', '某建工集团有限公司', '中建商砼材料供应有限公司', 45000000.00, 45000000.00, 13.00, 5176991.15, 39823008.85, '2026-03-10', '2026-03-15', '2027-12-31', '按月结算', '验收结算', 3.00, 1350000.00, 'PERFORMING', 'APPROVED', 1),
(30002, 0, 10001, 20002, 'CT-2026-002', '主体结构劳务分包合同',   'SUB',      '某建工集团有限公司', '宏远建筑劳务分包有限公司', 86000000.00, 86000000.00, 9.00,  7100917.43, 78899082.57, '2026-03-20', '2026-04-01', '2028-03-31', '按进度付款', '竣工结算', 5.00, 4300000.00, 'PERFORMING', 'APPROVED', 1),
(30003, 0, 10001, 20003, 'CT-2026-003', '工程造价咨询服务合同',   'SERVICE',  '某建工集团有限公司', '智联工程咨询服务有限公司', 1200000.00,  1200000.00,  6.00,  67924.53,   1132075.47,  '2026-03-25', '2026-04-01', '2028-06-30', '分阶段付款', '一次性结算', 0.00, 0.00, 'DRAFT', 'DRAFT', 1);

-- ============================================================
-- 9. 演示审批模板：合同审批流程（业务类型 PAY_REQUEST_TEST），含 5 个节点
-- ============================================================
INSERT IGNORE INTO wf_template (id, tenant_id, template_code, template_name, business_type, enabled, amount_min, amount_max, condition_rule, form_schema, created_by, remark) VALUES
(40001, 0, 'TPL-PAY-TEST-001', '付款申请测试审批流程', 'PAY_REQUEST_TEST', 1, 0.00, 999999999.99, NULL, NULL, 1, '演示用5节点审批流程');

INSERT IGNORE INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours) VALUES
(40101, 0, 40001, 'NODE_APPLY',    '发起申请',     1, 'START',    'SEQUENTIAL', JSON_OBJECT('type', 'INITIATOR'),                            NULL, 0, 0, NULL),
(40102, 0, 40001, 'NODE_DEPT',     '部门负责人审批', 2, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type', 'ROLE', 'roleCode', 'PROJECT_MANAGER'), NULL, 1, 1, 48),
(40103, 0, 40001, 'NODE_COST',     '成本部审核',   3, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type', 'ROLE', 'roleCode', 'COST_AUDIT'),     NULL, 1, 1, 48),
(40104, 0, 40001, 'NODE_FINANCE',  '财务审批',     4, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type', 'ROLE', 'roleCode', 'FINANCE'),        NULL, 1, 1, 48),
(40105, 0, 40001, 'NODE_GM',       '总经理审批',   5, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type', 'ROLE', 'roleCode', 'GENERAL_MANAGER'), NULL, 0, 1, 72);

SET FOREIGN_KEY_CHECKS = 1;
