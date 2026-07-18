-- V97: Seed formal phase 2 dashboard business roles (H2)

INSERT INTO sys_role
    (id, tenant_id, role_code, role_name, role_type, status, data_scope, created_by, remark)
SELECT 7, 0, 'PURCHASE_MANAGER', '采购经理', 'BUSINESS', 'ENABLE', 'ALL', 1, '第二阶段：采购经理默认角色'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 7 OR (tenant_id = 0 AND role_code = 'PURCHASE_MANAGER'));

INSERT INTO sys_role
    (id, tenant_id, role_code, role_name, role_type, status, data_scope, created_by, remark)
SELECT 8, 0, 'PRODUCTION_MANAGER', '生产经理', 'BUSINESS', 'ENABLE', 'ALL', 1, '第二阶段：生产经理默认角色'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 8 OR (tenant_id = 0 AND role_code = 'PRODUCTION_MANAGER'));

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 97001, 7, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 7 AND menu_id = 1);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 97002, 7, 803
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 7 AND menu_id = 803);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 97003, 7, 813
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 7 AND menu_id = 813);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 97011, 8, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 8 AND menu_id = 1);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 97012, 8, 803
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 8 AND menu_id = 803);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 97013, 8, 814
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 8 AND menu_id = 814);
