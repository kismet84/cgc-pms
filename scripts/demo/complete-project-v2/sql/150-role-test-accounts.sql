-- CGC-COMPLETE-PROJECT v2 / DEV-ONLY CANONICAL ROLE TEST ACCOUNTS
SET @demo_admin := (SELECT id FROM sys_user WHERE tenant_id=0 AND username='admin' AND deleted_flag=0 LIMIT 1);
SET @demo_org := (SELECT org_id FROM sys_user WHERE id=@demo_admin LIMIT 1);
SET @demo_password_hash := (SELECT password FROM sys_user WHERE id=@demo_admin LIMIT 1);

-- V293/Mainline 89 owns the system-role catalog. Replays must not reactivate legacy roles.
UPDATE sys_role
SET status='DISABLE',updated_by=@demo_admin,updated_at=NOW()
WHERE tenant_id=0 AND role_code='COST_MANAGER' AND deleted_flag=0;

DELETE ur FROM sys_user_role ur
JOIN sys_user u ON u.tenant_id=ur.tenant_id AND u.id=ur.user_id
WHERE u.tenant_id=0
  AND u.username IN ('demo.business','demo.cost','demo.purchase','demo.production','demo.chief','demo.finance');

UPDATE sys_user
SET status='DISABLE',deleted_flag=1,updated_by=@demo_admin,updated_at=NOW(),
    remark='历史演示角色账号，已由 ui26.* 九角色账号替代'
WHERE tenant_id=0
  AND username IN ('demo.business','demo.cost','demo.purchase','demo.production','demo.chief','demo.finance');

DELETE pm FROM pm_project_member pm
JOIN sys_user u ON u.tenant_id=pm.tenant_id AND u.id=pm.user_id
WHERE u.tenant_id=0
  AND u.username IN ('demo.business','demo.cost','demo.purchase','demo.production','demo.chief','demo.finance');
DELETE FROM pm_project_member
WHERE tenant_id=0 AND id BETWEEN 520000000000008650 AND 520000000000008656;

-- AppShell dev-only role switcher accounts. Keep all visible options executable on a clean demo load.
INSERT INTO sys_user
  (id,tenant_id,username,password,real_name,phone,email,org_id,avatar,status,is_admin,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000008701,0,'ui26.pm01',@demo_password_hash,'周明远',NULL,'ui26.pm01@example.invalid',@demo_org,NULL,'ENABLE',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'本地演示角色切换账号'),
  (520000000000008702,0,'ui26.bm01',@demo_password_hash,'陈思远',NULL,'ui26.bm01@example.invalid',@demo_org,NULL,'ENABLE',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'本地演示角色切换账号'),
  (520000000000008703,0,'ui26.cost01',@demo_password_hash,'许承泽',NULL,'ui26.cost01@example.invalid',@demo_org,NULL,'ENABLE',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'本地演示角色切换账号'),
  (520000000000008704,0,'ui26.pur01',@demo_password_hash,'何俊峰',NULL,'ui26.pur01@example.invalid',@demo_org,NULL,'ENABLE',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'本地演示角色切换账号'),
  (520000000000008705,0,'ui26.prod01',@demo_password_hash,'郑宏达',NULL,'ui26.prod01@example.invalid',@demo_org,NULL,'ENABLE',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'本地演示角色切换账号'),
  (520000000000008706,0,'ui26.chief01',@demo_password_hash,'徐正凯',NULL,'ui26.chief01@example.invalid',@demo_org,NULL,'ENABLE',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'本地演示角色切换账号'),
  (520000000000008707,0,'ui26.fin01',@demo_password_hash,'沈佳宁',NULL,'ui26.fin01@example.invalid',@demo_org,NULL,'ENABLE',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'本地演示角色切换账号'),
  (520000000000008709,0,'ui26.staff01',@demo_password_hash,'陈安琪',NULL,'ui26.staff01@example.invalid',@demo_org,NULL,'ENABLE',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'本地演示角色切换账号'),
  (520000000000008710,0,'ui26.gm01',@demo_password_hash,'顾景航',NULL,'ui26.gm01@example.invalid',@demo_org,NULL,'ENABLE',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'本地演示角色切换账号')
ON DUPLICATE KEY UPDATE
  real_name=VALUES(real_name),password=VALUES(password),email=VALUES(email),org_id=VALUES(org_id),status='ENABLE',
  updated_by=VALUES(updated_by),updated_at=NOW(),deleted_flag=0,remark=VALUES(remark);

-- Remove legacy role bindings and task-owned over-grants from earlier package versions.
DELETE FROM sys_role_menu
WHERE tenant_id=0 AND id BETWEEN 520000000000008620 AND 520000000000008709;

UPDATE sys_user
SET status='DISABLE',updated_by=@demo_admin,updated_at=NOW(),
    remark='历史角色切换辅助账号，不再作为九角色入口'
WHERE tenant_id=0 AND username IN ('ui26.mgmt01','ui26.mat01') AND deleted_flag=0;

DELETE ur FROM sys_user_role ur
JOIN sys_user u ON u.tenant_id=ur.tenant_id AND u.id=ur.user_id
WHERE u.tenant_id=0
  AND u.username IN ('ui26.gm01','ui26.fin01','ui26.pm01','ui26.cost01','ui26.chief01','ui26.bm01','ui26.prod01','ui26.pur01','ui26.staff01','ui26.mgmt01','ui26.mat01');

INSERT INTO sys_user_role (id,tenant_id,user_id,role_id)
VALUES
  (520000000000008727,0,(SELECT id FROM sys_user WHERE tenant_id=0 AND username='ui26.fin01' AND deleted_flag=0),(SELECT id FROM sys_role WHERE tenant_id=0 AND role_code='COMPANY_FINANCE' AND status='ENABLE' AND deleted_flag=0)),
  (520000000000008733,0,(SELECT id FROM sys_user WHERE tenant_id=0 AND username='ui26.fin01' AND deleted_flag=0),(SELECT id FROM sys_role WHERE tenant_id=0 AND role_code='SUPER_ADMIN' AND status='ENABLE' AND deleted_flag=0)),
  (520000000000008720,0,(SELECT id FROM sys_user WHERE tenant_id=0 AND username='ui26.pm01' AND deleted_flag=0),(SELECT id FROM sys_role WHERE tenant_id=0 AND role_code='PROJECT_MANAGER' AND status='ENABLE' AND deleted_flag=0)),
  (520000000000008721,0,(SELECT id FROM sys_user WHERE tenant_id=0 AND username='ui26.bm01' AND deleted_flag=0),(SELECT id FROM sys_role WHERE tenant_id=0 AND role_code='SAFETY_LEAD' AND status='ENABLE' AND deleted_flag=0)),
  (520000000000008723,0,(SELECT id FROM sys_user WHERE tenant_id=0 AND username='ui26.cost01' AND deleted_flag=0),(SELECT id FROM sys_role WHERE tenant_id=0 AND role_code='PROJECT_ACCOUNTANT' AND status='ENABLE' AND deleted_flag=0)),
  (520000000000008724,0,(SELECT id FROM sys_user WHERE tenant_id=0 AND username='ui26.pur01' AND deleted_flag=0),(SELECT id FROM sys_role WHERE tenant_id=0 AND role_code='PROCUREMENT_LEAD' AND status='ENABLE' AND deleted_flag=0)),
  (520000000000008725,0,(SELECT id FROM sys_user WHERE tenant_id=0 AND username='ui26.prod01' AND deleted_flag=0),(SELECT id FROM sys_role WHERE tenant_id=0 AND role_code='CONSTRUCTION_LEAD' AND status='ENABLE' AND deleted_flag=0)),
  (520000000000008726,0,(SELECT id FROM sys_user WHERE tenant_id=0 AND username='ui26.chief01' AND deleted_flag=0),(SELECT id FROM sys_role WHERE tenant_id=0 AND role_code='TECHNICAL_LEAD' AND status='ENABLE' AND deleted_flag=0)),
  (520000000000008730,0,(SELECT id FROM sys_user WHERE tenant_id=0 AND username='ui26.staff01' AND deleted_flag=0),(SELECT id FROM sys_role WHERE tenant_id=0 AND role_code='EMPLOYEE' AND status='ENABLE' AND deleted_flag=0)),
  (520000000000008731,0,(SELECT id FROM sys_user WHERE tenant_id=0 AND username='ui26.gm01' AND deleted_flag=0),(SELECT id FROM sys_role WHERE tenant_id=0 AND role_code='COMPANY_OWNER' AND status='ENABLE' AND deleted_flag=0))
ON DUPLICATE KEY UPDATE user_id=VALUES(user_id),role_id=VALUES(role_id);

INSERT INTO pm_project_member
  (id,tenant_id,project_id,user_id,role_code,position_name,start_date,end_date,status,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
SELECT members.* FROM (
SELECT
  520000000000008650 AS id,0 AS tenant_id,520000000000009002 AS project_id,
  (SELECT id FROM sys_user WHERE tenant_id=0 AND username='ui26.bm01' AND deleted_flag=0 LIMIT 1) AS user_id,
  'SAFETY_LEAD' AS role_code,'安全负责人' AS position_name,CURDATE() AS start_date,NULL AS end_date,
  'ACTIVE' AS status,@demo_admin AS created_by,NOW() AS created_at,@demo_admin AS updated_by,NOW() AS updated_at,
  0 AS deleted_flag,'M52 九角色驾驶舱项目范围' AS remark
UNION ALL SELECT 520000000000008651,0,520000000000009002,(SELECT id FROM sys_user WHERE tenant_id=0 AND username='ui26.cost01' AND deleted_flag=0 LIMIT 1),'PROJECT_ACCOUNTANT','项目会计',CURDATE(),NULL,'ACTIVE',@demo_admin,NOW(),@demo_admin,NOW(),0,'M52 九角色驾驶舱项目范围'
UNION ALL SELECT 520000000000008652,0,520000000000009002,(SELECT id FROM sys_user WHERE tenant_id=0 AND username='ui26.pur01' AND deleted_flag=0 LIMIT 1),'PROCUREMENT_LEAD','采购负责人',CURDATE(),NULL,'ACTIVE',@demo_admin,NOW(),@demo_admin,NOW(),0,'M52 九角色驾驶舱项目范围'
UNION ALL SELECT 520000000000008653,0,520000000000009002,(SELECT id FROM sys_user WHERE tenant_id=0 AND username='ui26.prod01' AND deleted_flag=0 LIMIT 1),'CONSTRUCTION_LEAD','施工负责人',CURDATE(),NULL,'ACTIVE',@demo_admin,NOW(),@demo_admin,NOW(),0,'M52 九角色驾驶舱项目范围'
UNION ALL SELECT 520000000000008654,0,520000000000009002,(SELECT id FROM sys_user WHERE tenant_id=0 AND username='ui26.chief01' AND deleted_flag=0 LIMIT 1),'TECHNICAL_LEAD','技术负责人',CURDATE(),NULL,'ACTIVE',@demo_admin,NOW(),@demo_admin,NOW(),0,'M52 九角色驾驶舱项目范围'
UNION ALL SELECT 520000000000008655,0,520000000000009002,(SELECT id FROM sys_user WHERE tenant_id=0 AND username='ui26.staff01' AND status='ENABLE' AND deleted_flag=0 LIMIT 1),'EMPLOYEE','员工',CURDATE(),NULL,'ACTIVE',@demo_admin,NOW(),@demo_admin,NOW(),0,'M52 九角色驾驶舱项目范围'
UNION ALL SELECT 520000000000008656,0,520000000000009002,(SELECT id FROM sys_user WHERE tenant_id=0 AND username='ui26.pm01' AND status='ENABLE' AND deleted_flag=0 LIMIT 1),'PROJECT_MANAGER','项目经理',CURDATE(),NULL,'ACTIVE',@demo_admin,NOW(),@demo_admin,NOW(),0,'M52 九角色驾驶舱项目范围'
) AS members
WHERE EXISTS (SELECT 1 FROM pm_project WHERE tenant_id=0 AND id=520000000000009002 AND deleted_flag=0)
ON DUPLICATE KEY UPDATE
  role_code=VALUES(role_code),position_name=VALUES(position_name),status='ACTIVE',end_date=NULL,
  updated_by=VALUES(updated_by),updated_at=NOW(),deleted_flag=0,remark=VALUES(remark);
