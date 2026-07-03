-- V120: seed alert-center real demo accounts and grant alert menu to business roles (H2)

INSERT INTO sys_role
    (id, tenant_id, role_code, role_name, role_type, status, data_scope, created_by, remark)
SELECT 4, 0, 'COMMERCIAL_MANAGER', '商务经理', 'BUSINESS', 'ENABLE', 'ALL', 1, '第15条主线：商务经理正式角色'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role
    WHERE id = 4 OR (tenant_id = 0 AND role_code = 'COMMERCIAL_MANAGER')
);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 120001, 4, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 4 AND menu_id = 1);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 120002, 4, 803
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 4 AND menu_id = 803);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 120003, 4, 808
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 4 AND menu_id = 808);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 120004, 4, 765
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 4 AND menu_id = 765);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 120005, 4, 766
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 4 AND menu_id = 766);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 120011, 7, 765
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 7 AND menu_id = 765);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 120012, 7, 766
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 7 AND menu_id = 766);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 120021, 8, 765
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 8 AND menu_id = 765);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 120022, 8, 766
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 8 AND menu_id = 766);

INSERT INTO sys_user (
    id, tenant_id, username, password, real_name, phone, email,
    status, is_admin, created_by, updated_by, created_at, updated_at,
    deleted_flag, remark
)
SELECT 980000000000000021, 0, 'demo_alert_purchase',
       '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2',
       '预警采购经理演示账号', '13800010021', 'demo_alert_purchase@cgc-pms.com',
       'ENABLE', 0, 1, 1, NOW(), NOW(),
       0, 'V120预警中心真实角色验收账号'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_user
    WHERE tenant_id = 0 AND username = 'demo_alert_purchase' AND deleted_flag = 0
);

INSERT INTO sys_user (
    id, tenant_id, username, password, real_name, phone, email,
    status, is_admin, created_by, updated_by, created_at, updated_at,
    deleted_flag, remark
)
SELECT 980000000000000022, 0, 'demo_alert_production',
       '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2',
       '预警生产经理演示账号', '13800010022', 'demo_alert_production@cgc-pms.com',
       'ENABLE', 0, 1, 1, NOW(), NOW(),
       0, 'V120预警中心真实角色验收账号'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_user
    WHERE tenant_id = 0 AND username = 'demo_alert_production' AND deleted_flag = 0
);

INSERT INTO sys_user (
    id, tenant_id, username, password, real_name, phone, email,
    status, is_admin, created_by, updated_by, created_at, updated_at,
    deleted_flag, remark
)
SELECT 980000000000000023, 0, 'demo_alert_commercial',
       '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2',
       '预警商务经理演示账号', '13800010023', 'demo_alert_commercial@cgc-pms.com',
       'ENABLE', 0, 1, 1, NOW(), NOW(),
       0, 'V120预警中心真实角色验收账号'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_user
    WHERE tenant_id = 0 AND username = 'demo_alert_commercial' AND deleted_flag = 0
);

INSERT INTO sys_user (
    id, tenant_id, username, password, real_name, phone, email,
    status, is_admin, created_by, updated_by, created_at, updated_at,
    deleted_flag, remark
)
SELECT 980000000000000024, 0, 'demo_alert_purchase_production',
       '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2',
       '预警采购生产双角色账号', '13800010024', 'demo_alert_purchase_production@cgc-pms.com',
       'ENABLE', 0, 1, 1, NOW(), NOW(),
       0, 'V120预警中心真实多角色验收账号'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_user
    WHERE tenant_id = 0 AND username = 'demo_alert_purchase_production' AND deleted_flag = 0
);

INSERT INTO sys_user (
    id, tenant_id, username, password, real_name, phone, email,
    status, is_admin, created_by, updated_by, created_at, updated_at,
    deleted_flag, remark
)
SELECT 980000000000000025, 0, 'demo_alert_purchase_commercial',
       '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2',
       '预警采购商务双角色账号', '13800010025', 'demo_alert_purchase_commercial@cgc-pms.com',
       'ENABLE', 0, 1, 1, NOW(), NOW(),
       0, 'V120预警中心真实多角色验收账号'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_user
    WHERE tenant_id = 0 AND username = 'demo_alert_purchase_commercial' AND deleted_flag = 0
);

INSERT INTO sys_user_role (id, user_id, role_id)
SELECT 120101, 980000000000000021, 7
WHERE NOT EXISTS (SELECT 1 FROM sys_user_role WHERE user_id = 980000000000000021 AND role_id = 7);

INSERT INTO sys_user_role (id, user_id, role_id)
SELECT 120102, 980000000000000022, 8
WHERE NOT EXISTS (SELECT 1 FROM sys_user_role WHERE user_id = 980000000000000022 AND role_id = 8);

INSERT INTO sys_user_role (id, user_id, role_id)
SELECT 120103, 980000000000000023, 4
WHERE NOT EXISTS (SELECT 1 FROM sys_user_role WHERE user_id = 980000000000000023 AND role_id = 4);

INSERT INTO sys_user_role (id, user_id, role_id)
SELECT 120104, 980000000000000024, 7
WHERE NOT EXISTS (SELECT 1 FROM sys_user_role WHERE user_id = 980000000000000024 AND role_id = 7);

INSERT INTO sys_user_role (id, user_id, role_id)
SELECT 120105, 980000000000000024, 8
WHERE NOT EXISTS (SELECT 1 FROM sys_user_role WHERE user_id = 980000000000000024 AND role_id = 8);

INSERT INTO sys_user_role (id, user_id, role_id)
SELECT 120106, 980000000000000025, 7
WHERE NOT EXISTS (SELECT 1 FROM sys_user_role WHERE user_id = 980000000000000025 AND role_id = 7);

INSERT INTO sys_user_role (id, user_id, role_id)
SELECT 120107, 980000000000000025, 4
WHERE NOT EXISTS (SELECT 1 FROM sys_user_role WHERE user_id = 980000000000000025 AND role_id = 4);
