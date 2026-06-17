-- Reorganize navigation by business domain without changing existing page URLs.

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
VALUES
    (900, 0, 0, '工作台',       'DIR', '/workbench',             NULL, NULL, 'home',          1, 'ENABLE', 1),
    (901, 0, 0, '项目与主数据', 'DIR', '/master-data',           NULL, NULL, 'project',       2, 'ENABLE', 1),
    (902, 0, 0, '合同管理',     'DIR', '/contract-domain',       NULL, NULL, 'contract',      3, 'ENABLE', 1),
    (903, 0, 0, '成本管理',     'DIR', '/cost-domain',           NULL, NULL, 'dollar',        4, 'ENABLE', 1),
    (904, 0, 0, '采购与库存',   'DIR', '/procurement-inventory', NULL, NULL, 'shopping-cart', 5, 'ENABLE', 1),
    (905, 0, 0, '分包管理',     'DIR', '/subcontract-domain',    NULL, NULL, 'branches',      6, 'ENABLE', 1),
    (906, 0, 0, '付款与发票',   'DIR', '/payment-invoice',       NULL, NULL, 'account-book',  7, 'ENABLE', 1),
    (907, 0, 0, '结算管理',     'DIR', '/settlement-domain',     NULL, NULL, 'account-book',  8, 'ENABLE', 1),
    (908, 0, 0, '审批中心',     'DIR', '/approval-center',       NULL, NULL, 'audit',         9, 'ENABLE', 1),
    (909, 0, 0, '系统管理',     'DIR', '/system-management',     NULL, NULL, 'setting',      10, 'ENABLE', 1);

UPDATE sys_menu SET parent_id = 900, menu_name = '首页驾驶舱', path = '/dashboard',
    component = 'dashboard/index', order_num = 1, visible = 1 WHERE id = 1;
UPDATE sys_menu SET parent_id = 900, menu_name = '预警中心', path = '/alert',
    component = 'alert/index', order_num = 2, visible = 1 WHERE id = 766;

UPDATE sys_menu SET parent_id = 901, menu_name = '项目列表', path = '/project/list',
    component = 'project/index', order_num = 1, visible = 1 WHERE id = 201;
UPDATE sys_menu SET parent_id = 901, menu_name = '合作方管理', path = '/partner',
    component = 'partner/index', order_num = 2, visible = 1 WHERE id = 401;
UPDATE sys_menu SET parent_id = 901, menu_name = '组织架构', path = '/org',
    component = 'org/index', order_num = 3, visible = 1 WHERE id = 701;

UPDATE sys_menu SET parent_id = 902, menu_name = '合同台账', path = '/contract/ledger',
    component = 'contract/ContractLedgerPage', order_num = 1, visible = 1 WHERE id = 301;

UPDATE sys_menu SET parent_id = 904, path = '/inventory/purchase-request', order_num = 1, visible = 1 WHERE id = 734;
UPDATE sys_menu SET parent_id = 904, path = '/inventory/warehouse', order_num = 4, visible = 1 WHERE id = 731;
UPDATE sys_menu SET parent_id = 904, path = '/inventory/stock', order_num = 5, visible = 1 WHERE id = 732;
UPDATE sys_menu SET parent_id = 904, path = '/inventory/transaction', order_num = 6, visible = 1 WHERE id = 733;

UPDATE sys_menu SET parent_id = 906, menu_name = '发票管理', path = '/invoice',
    component = 'invoice/index', order_num = 2, visible = 1 WHERE id = 751;

UPDATE sys_menu SET parent_id = 909, path = '/system/users',
    component = 'system/users/index', order_num = 1, visible = 1 WHERE id = 501;
UPDATE sys_menu SET parent_id = 909, path = '/system/roles',
    component = 'system/roles/index', order_num = 2, visible = 1 WHERE id = 502;
UPDATE sys_menu SET parent_id = 909, path = '/system/dict',
    component = 'system/dict/index', order_num = 3, visible = 1 WHERE id = 504;
UPDATE sys_menu SET visible = 0 WHERE id = 503;

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status, visible)
VALUES
    (920, 0, 901, '材料字典',     'MENU', '/material/dictionary', 'material/dictionary',           'material:dict:list',          'database',      4, 'ENABLE', 1),
    (921, 0, 902, '变更签证',     'MENU', '/variation/order',     'variation/order',               'variation:order:query',       'swap',          2, 'ENABLE', 1),
    (930, 0, 903, '成本科目',     'MENU', '/cost/subject',        'cost-subject/index',            'cost:query',                  'profile',       1, 'ENABLE', 1),
    (931, 0, 903, '成本台账',     'MENU', '/cost/ledger',         'cost/ledger',                   'cost:ledger:query',           'account-book',  2, 'ENABLE', 1),
    (932, 0, 903, '动态成本汇总', 'MENU', '/cost/summary',        'cost/summary',                  'cost:summary:view',           'fund',          3, 'ENABLE', 1),
    (933, 0, 903, '目标成本',     'MENU', '/cost-target/index',   'cost-target/index',             'cost:target:query',           'aim',           4, 'ENABLE', 1),
    (940, 0, 904, '采购订单',     'MENU', '/purchase/order',      'purchase/order',                'purchase:order:query',        'shopping-cart', 2, 'ENABLE', 1),
    (941, 0, 904, '材料验收',     'MENU', '/purchase/receipt',    'receipt/index',                 'receipt:query',               'check-square',  3, 'ENABLE', 1),
    (942, 0, 905, '分包任务',     'MENU', '/subcontract/task',    'subcontract/task',              'subtask:query',               'branches',      1, 'ENABLE', 1),
    (943, 0, 905, '分包计量',     'MENU', '/subcontract/measure', 'subcontract/measure',           'subcontract:measure:query',   'calculator',    2, 'ENABLE', 1),
    (944, 0, 906, '付款申请',     'MENU', '/payment/application', 'payment/index',                 'payment:app:query',           'dollar',        1, 'ENABLE', 1),
    (945, 0, 907, '结算列表',     'MENU', '/settlement/list',     'settlement/index',              'settlement:query',            'account-book',  1, 'ENABLE', 1),
    (946, 0, 908, '我的待办',     'MENU', '/approval/todo',       'approval/todo',                 NULL,                          'clock-circle',  1, 'ENABLE', 1),
    (947, 0, 908, '我的已办',     'MENU', '/approval/done',       'approval/todo',                 NULL,                          'check-circle',  2, 'ENABLE', 1),
    (948, 0, 908, '抄送我的',     'MENU', '/approval/cc',         'approval/todo',                 NULL,                          'mail',          3, 'ENABLE', 1),
    (949, 0, 908, '审批流程管理', 'MENU', '/approval/process',    'approval/process',              NULL,                          'deployment',    4, 'ENABLE', 1),
    (950, 0, 909, '数据管理',     'MENU', '/system/data',         'system/data/index',             NULL,                          'database',      4, 'ENABLE', 1);

UPDATE sys_menu
SET visible = 0
WHERE id IN (2, 3, 4, 5, 700, 710, 720, 730, 765);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 600000 + id, 1, id
FROM sys_menu
WHERE id BETWEEN 900 AND 950 AND deleted_flag = 0;

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 700000000 + rm.role_id * 1000 + child.parent_id, rm.role_id, child.parent_id
FROM sys_role_menu rm
JOIN sys_menu child ON child.id = rm.menu_id
WHERE child.parent_id BETWEEN 900 AND 909
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_menu existing
      WHERE existing.role_id = rm.role_id
        AND existing.menu_id = child.parent_id
  )
GROUP BY rm.role_id, child.parent_id;
