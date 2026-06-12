-- V39__init_phase4_menu_perms.sql
-- 建筑工程总包项目全过程管理系统 - 第4阶段菜单、权限、审批模板种子
-- 数据库：MySQL 8.0+
-- ID 策略：菜单 ID 区间 700-799，审批模板 template_id 50010+（避开已用 50007-50009）
-- 说明：全部 INSERT IGNORE 确保幂等

-- ============================================================
-- 1. 一级目录：组织架构 / 库存管理 / 发票管理 / 消息中心
-- ============================================================
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible) VALUES
(700, 0, 0, '组织架构',   'DIR', '/org',        NULL, NULL, 'apartment',  6, 'ENABLE', 1),
(710, 0, 0, '库存管理',   'DIR', '/inventory',  NULL, NULL, 'warehouse',  7, 'ENABLE', 1),
(720, 0, 0, '发票管理',   'DIR', '/invoice',    NULL, NULL, 'file-text',  8, 'ENABLE', 1),
(730, 0, 0, '消息中心',   'DIR', '/notification', NULL, NULL, 'bell',    9, 'ENABLE', 1);

-- ============================================================
-- 2. 组织架构子菜单（公司/部门/岗位）
-- ============================================================
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible) VALUES
(701, 0, 700, '组织管理', 'MENU', 'index',   'org/index',         'org:list',   'apartment', 1, 'ENABLE', 1),
(702, 0, 701, '新增组织', 'BUTTON', NULL,     NULL,                 'org:add',    NULL, 1, 'ENABLE', 1),
(703, 0, 701, '编辑组织', 'BUTTON', NULL,     NULL,                 'org:edit',   NULL, 2, 'ENABLE', 1),
(704, 0, 701, '删除组织', 'BUTTON', NULL,     NULL,                 'org:delete', NULL, 3, 'ENABLE', 1);

-- ============================================================
-- 3. 项目成员管理子菜单（挂靠项目管理目录 2）
-- ============================================================
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible) VALUES
(711, 0, 2, '项目成员',   'BUTTON', NULL,     NULL,                 'project:member:list',   NULL, 4, 'ENABLE', 1),
(712, 0, 2, '新增成员',   'BUTTON', NULL,     NULL,                 'project:member:add',    NULL, 5, 'ENABLE', 1),
(713, 0, 2, '编辑成员',   'BUTTON', NULL,     NULL,                 'project:member:edit',   NULL, 6, 'ENABLE', 1),
(714, 0, 2, '删除成员',   'BUTTON', NULL,     NULL,                 'project:member:delete', NULL, 7, 'ENABLE', 1);

-- ============================================================
-- 4. 字典管理（菜单 504 已在 V6 存在 system:dict:list，补充操作权限）
-- ============================================================
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible) VALUES
(721, 0, 504, '新增字典', 'BUTTON', NULL,     NULL,                 'system:dict:add',    NULL, 1, 'ENABLE', 1),
(722, 0, 504, '编辑字典', 'BUTTON', NULL,     NULL,                 'system:dict:edit',   NULL, 2, 'ENABLE', 1),
(723, 0, 504, '删除字典', 'BUTTON', NULL,     NULL,                 'system:dict:delete', NULL, 3, 'ENABLE', 1);

-- ============================================================
-- 5. 库存管理子菜单（仓库/台账/出入库/采购申请）
-- ============================================================
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible) VALUES
(731, 0, 710, '仓库管理',   'MENU', 'warehouse',        'inventory/warehouse',        'inventory:warehouse:list',   'home',    1, 'ENABLE', 1),
(732, 0, 710, '库存台账',   'MENU', 'stock',            'inventory/stock',            'inventory:stock:list',      'bar-chart', 2, 'ENABLE', 1),
(733, 0, 710, '出入库管理', 'MENU', 'transaction',      'inventory/transaction',      'inventory:transaction:list','swap',  3, 'ENABLE', 1),
(734, 0, 710, '采购申请',   'MENU', 'purchase-request', 'inventory/purchase-request', 'purchase:request:list',     'shopping-cart', 4, 'ENABLE', 1);

-- 库存操作权限
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible) VALUES
(735, 0, 731, '新增仓库',   'BUTTON', NULL, NULL, 'inventory:warehouse:add',    NULL, 1, 'ENABLE', 1),
(736, 0, 731, '编辑仓库',   'BUTTON', NULL, NULL, 'inventory:warehouse:edit',   NULL, 2, 'ENABLE', 1),
(737, 0, 731, '删除仓库',   'BUTTON', NULL, NULL, 'inventory:warehouse:delete', NULL, 3, 'ENABLE', 1),
(738, 0, 733, '新增入库',   'BUTTON', NULL, NULL, 'inventory:transaction:add',  NULL, 1, 'ENABLE', 1),
(739, 0, 734, '新增采购申请', 'BUTTON', NULL, NULL, 'purchase:request:add',    NULL, 1, 'ENABLE', 1),
(740, 0, 734, '提交审批',   'BUTTON', NULL, NULL, 'purchase:request:submit',   NULL, 2, 'ENABLE', 1);

-- ============================================================
-- 6. 发票管理子菜单
-- ============================================================
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible) VALUES
(751, 0, 720, '发票列表',   'MENU', 'index',   'invoice/index',     'invoice:list',   'file-text', 1, 'ENABLE', 1),
(752, 0, 751, '新增发票',   'BUTTON', NULL,     NULL,                 'invoice:add',    NULL, 1, 'ENABLE', 1),
(753, 0, 751, '编辑发票',   'BUTTON', NULL,     NULL,                 'invoice:edit',   NULL, 2, 'ENABLE', 1),
(754, 0, 751, '删除发票',   'BUTTON', NULL,     NULL,                 'invoice:delete', NULL, 3, 'ENABLE', 1),
(755, 0, 751, '核验发票',   'BUTTON', NULL,     NULL,                 'invoice:verify', NULL, 4, 'ENABLE', 1);

-- ============================================================
-- 7. 消息中心子菜单
-- ============================================================
INSERT INTO sys_menu (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible) VALUES
(761, 0, 730, '消息列表',   'MENU', 'index',   'notification/index', 'notification:list', 'bell', 1, 'ENABLE', 1);

-- ============================================================
-- 8. 审批模板：采购申请审批（3 节点顺序审批）
--    参照 V9 合同审批模板的字段顺序
--    template_id=50010，避开已用 50001/50007/50008/50009
--    enabled=1 (SMALLINT)，非 VARCHAR status
-- ============================================================
INSERT INTO wf_template (id, tenant_id, template_code, template_name, business_type, enabled, amount_min, amount_max, condition_rule, form_schema, created_by, remark) VALUES
(50010, 0, 'TPL-PURCHASE-REQUEST-001', '采购申请审批', 'PURCHASE_REQUEST', 1, 0.00, 999999999.99, NULL, NULL, 1, '第4阶段：采购申请三级顺序审批');

INSERT INTO wf_template_node (id, tenant_id, template_id, node_code, node_name, node_order, node_type, approve_mode, approver_config, condition_rule, allow_transfer, allow_add_sign, timeout_hours) VALUES
(50011, 0, 50010, 'NODE_PR_001', '项目经理', 1, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type', 'ROLE', 'roleCode', 'PROJECT_MANAGER'), NULL, 1, 1, 48),
(50012, 0, 50010, 'NODE_PR_002', '商务经理', 2, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type', 'ROLE', 'roleCode', 'COMMERCIAL_MANAGER'), NULL, 1, 1, 48),
(50013, 0, 50010, 'NODE_PR_003', '成本/采购', 3, 'APPROVAL', 'SEQUENTIAL', JSON_OBJECT('type', 'ROLE', 'roleCode', 'COST_MANAGER'), NULL, 1, 1, 48);

-- ============================================================
-- 9. 超级管理员拥有全部第4阶段菜单权限
-- ============================================================
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 10000 + id, 1, id FROM sys_menu WHERE id BETWEEN 700 AND 799 AND deleted_flag = 0;
