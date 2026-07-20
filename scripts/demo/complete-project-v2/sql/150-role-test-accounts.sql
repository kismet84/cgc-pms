-- CGC-COMPLETE-PROJECT v2 / DEV-ONLY ROLE TEST ACCOUNTS
SET @demo_admin := (SELECT id FROM sys_user WHERE tenant_id=0 AND username='admin' AND deleted_flag=0 LIMIT 1);
SET @demo_org := (SELECT org_id FROM sys_user WHERE id=@demo_admin LIMIT 1);
SET @demo_password_hash := (SELECT password FROM sys_user WHERE id=@demo_admin LIMIT 1);

INSERT INTO sys_role
  (id,tenant_id,role_code,role_name,role_type,status,data_scope,created_by,created_at,updated_by,updated_at,deleted_flag,remark,role_level)
VALUES
  (520000000000008601,0,'COST_MANAGER','成本经理','BUSINESS','ENABLE','ALL',@demo_admin,NOW(),@demo_admin,NOW(),0,'M52 驾驶舱角色测试账号专用角色',2)
ON DUPLICATE KEY UPDATE
  role_name=VALUES(role_name),status='ENABLE',data_scope='ALL',updated_by=VALUES(updated_by),updated_at=NOW(),deleted_flag=0;

INSERT INTO sys_user
  (id,tenant_id,username,password,real_name,phone,email,org_id,avatar,status,is_admin,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000008602,0,'demo.business',@demo_password_hash,'演示商务经理',NULL,'demo.business@example.invalid',@demo_org,NULL,'ENABLE',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M52 驾驶舱角色测试账号'),
  (520000000000008603,0,'demo.cost',@demo_password_hash,'演示成本经理',NULL,'demo.cost@example.invalid',@demo_org,NULL,'ENABLE',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M52 驾驶舱角色测试账号'),
  (520000000000008604,0,'demo.purchase',@demo_password_hash,'演示采购经理',NULL,'demo.purchase@example.invalid',@demo_org,NULL,'ENABLE',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M52 驾驶舱角色测试账号'),
  (520000000000008605,0,'demo.production',@demo_password_hash,'演示生产经理',NULL,'demo.production@example.invalid',@demo_org,NULL,'ENABLE',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M52 驾驶舱角色测试账号'),
  (520000000000008606,0,'demo.chief',@demo_password_hash,'演示总工程师',NULL,'demo.chief@example.invalid',@demo_org,NULL,'ENABLE',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M52 驾驶舱角色测试账号'),
  (520000000000008607,0,'demo.finance',@demo_password_hash,'演示财务经理',NULL,'demo.finance@example.invalid',@demo_org,NULL,'ENABLE',0,@demo_admin,NOW(),@demo_admin,NOW(),0,'M52 驾驶舱角色测试账号')
ON DUPLICATE KEY UPDATE
  real_name=VALUES(real_name),email=VALUES(email),org_id=VALUES(org_id),status='ENABLE',updated_by=VALUES(updated_by),updated_at=NOW(),deleted_flag=0;

INSERT INTO sys_user_role (id,tenant_id,user_id,role_id)
VALUES
  (520000000000008612,0,520000000000008602,4),
  (520000000000008613,0,520000000000008603,520000000000008601),
  (520000000000008614,0,520000000000008604,7),
  (520000000000008615,0,520000000000008605,8),
  (520000000000008616,0,520000000000008606,9),
  (520000000000008617,0,520000000000008607,6)
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);

INSERT IGNORE INTO sys_role_menu (id,tenant_id,role_id,menu_id)
VALUES
  (520000000000008620,0,2,1),
  (520000000000008621,0,2,803),
  (520000000000008622,0,2,807),
  (520000000000008623,0,4,1),
  (520000000000008624,0,4,803),
  (520000000000008625,0,4,808),
  (520000000000008626,0,520000000000008601,1),
  (520000000000008627,0,520000000000008601,803),
  (520000000000008628,0,520000000000008601,809),
  (520000000000008629,0,520000000000008601,812),
  (520000000000008630,0,7,1),
  (520000000000008631,0,7,803),
  (520000000000008632,0,7,813),
  (520000000000008633,0,8,1),
  (520000000000008634,0,8,803),
  (520000000000008635,0,8,814),
  (520000000000008636,0,9,1),
  (520000000000008637,0,9,803),
  (520000000000008638,0,9,815),
  (520000000000008639,0,6,1),
  (520000000000008640,0,6,803),
  (520000000000008641,0,6,810),
  (520000000000008642,0,2,766),
  (520000000000008643,0,4,766),
  (520000000000008644,0,520000000000008601,766),
  (520000000000008645,0,7,766),
  (520000000000008646,0,8,766),
  (520000000000008647,0,9,766),
  (520000000000008648,0,6,766),
  (520000000000008658,0,2,767),
  (520000000000008659,0,4,767),
  (520000000000008660,0,520000000000008601,767),
  (520000000000008661,0,7,767),
  (520000000000008662,0,8,767),
  (520000000000008663,0,9,767),
  (520000000000008664,0,6,767);

INSERT INTO pm_project_member
  (id,tenant_id,project_id,user_id,role_code,position_name,start_date,end_date,status,created_by,created_at,updated_by,updated_at,deleted_flag,remark)
VALUES
  (520000000000008650,0,520000000000009002,520000000000008602,'COMMERCIAL_MANAGER','商务经理',CURDATE(),NULL,'ACTIVE',@demo_admin,NOW(),@demo_admin,NOW(),0,'M52 驾驶舱预警项目范围'),
  (520000000000008651,0,520000000000009002,520000000000008603,'COST_MANAGER','成本经理',CURDATE(),NULL,'ACTIVE',@demo_admin,NOW(),@demo_admin,NOW(),0,'M52 驾驶舱预警项目范围'),
  (520000000000008652,0,520000000000009002,520000000000008604,'PURCHASE_MANAGER','采购经理',CURDATE(),NULL,'ACTIVE',@demo_admin,NOW(),@demo_admin,NOW(),0,'M52 驾驶舱预警项目范围'),
  (520000000000008653,0,520000000000009002,520000000000008605,'PRODUCTION_MANAGER','生产经理',CURDATE(),NULL,'ACTIVE',@demo_admin,NOW(),@demo_admin,NOW(),0,'M52 驾驶舱预警项目范围'),
  (520000000000008654,0,520000000000009002,520000000000008606,'CHIEF_ENGINEER','总工程师',CURDATE(),NULL,'ACTIVE',@demo_admin,NOW(),@demo_admin,NOW(),0,'M52 驾驶舱预警项目范围'),
  (520000000000008655,0,520000000000009002,520000000000008607,'FINANCE','财务经理',CURDATE(),NULL,'ACTIVE',@demo_admin,NOW(),@demo_admin,NOW(),0,'M52 驾驶舱预警项目范围'),
  (520000000000008656,0,520000000000009002,(SELECT id FROM sys_user WHERE tenant_id=0 AND username='demo.manager' AND deleted_flag=0 LIMIT 1),'PROJECT_MANAGER','项目经理',CURDATE(),NULL,'ACTIVE',@demo_admin,NOW(),@demo_admin,NOW(),0,'M52 驾驶舱预警项目范围')
ON DUPLICATE KEY UPDATE
  role_code=VALUES(role_code),position_name=VALUES(position_name),status='ACTIVE',end_date=NULL,
  updated_by=VALUES(updated_by),updated_at=NOW(),deleted_flag=0,remark=VALUES(remark);
