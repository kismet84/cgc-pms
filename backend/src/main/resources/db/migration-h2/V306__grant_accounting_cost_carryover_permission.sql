CREATE LOCAL TEMPORARY TABLE m306_tenant(tenant_id BIGINT PRIMARY KEY);
INSERT INTO m306_tenant VALUES(0);
INSERT INTO m306_tenant SELECT DISTINCT tenant_id FROM sys_role WHERE tenant_id<>0;

INSERT INTO sys_menu(id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible,
 created_by,updated_by,remark,created_at,updated_at,deleted_flag)
SELECT 306000000000000000+ROW_NUMBER() OVER(ORDER BY t.tenant_id),t.tenant_id,
 COALESCE((SELECT MIN(m.id) FROM sys_menu m WHERE m.tenant_id=t.tenant_id AND m.deleted_flag=0
  AND m.path='/accounting-entry'),0),'执行项目成本结转','BUTTON',NULL,NULL,
 'accounting:cost-carryover',NULL,95,'ENABLE',0,NULL,NULL,'MAINLINE-97-COST-CARRYOVER',
 CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM m306_tenant t
WHERE NOT EXISTS(SELECT 1 FROM sys_menu m
 WHERE m.tenant_id=t.tenant_id AND m.perms='accounting:cost-carryover' AND m.deleted_flag=0);

INSERT INTO sys_role_menu(id,tenant_id,role_id,menu_id)
SELECT 306100000000000000+ROW_NUMBER() OVER(ORDER BY r.tenant_id,r.id,m.id),r.tenant_id,r.id,m.id
FROM sys_role r JOIN sys_menu m ON m.tenant_id=r.tenant_id
WHERE r.role_code='COMPANY_FINANCE' AND r.deleted_flag=0 AND r.status='ENABLE'
 AND m.deleted_flag=0 AND m.perms='accounting:cost-carryover'
 AND NOT EXISTS(SELECT 1 FROM sys_role_menu x
  WHERE x.tenant_id=r.tenant_id AND x.role_id=r.id AND x.menu_id=m.id);

DROP TABLE m306_tenant;
