CREATE TEMPORARY TABLE m302_tenant (tenant_id BIGINT PRIMARY KEY);
INSERT IGNORE INTO m302_tenant VALUES (0);
INSERT IGNORE INTO m302_tenant SELECT DISTINCT tenant_id FROM sys_role;

CREATE TEMPORARY TABLE m302_permission (
    permission_code VARCHAR(200) PRIMARY KEY,
    menu_name VARCHAR(200) NOT NULL
);
INSERT INTO m302_permission VALUES
 ('overhead:query','查看间接费分摊规则'),
 ('overhead:add','创建间接费分摊规则'),
 ('overhead:edit','启停间接费分摊规则'),
 ('overhead:execute','执行间接费分摊');

SET @m302_menu_base=(SELECT GREATEST(COALESCE(MAX(id),0),302000000000000000) FROM sys_menu);
INSERT INTO sys_menu
 (id,tenant_id,parent_id,menu_name,menu_type,path,component,perms,icon,order_num,status,visible,
  created_by,updated_by,remark,created_at,updated_at,deleted_flag)
SELECT @m302_menu_base+ROW_NUMBER() OVER (ORDER BY t.tenant_id,p.permission_code),t.tenant_id,
       COALESCE((SELECT MIN(m.id) FROM sys_menu m
                 WHERE m.tenant_id=t.tenant_id AND m.deleted_flag=0
                   AND m.path='/cost/subject/rules'),0),
       p.menu_name,'BUTTON',NULL,NULL,p.permission_code,NULL,92,'ENABLE',0,
       NULL,NULL,'MAINLINE-96-OVERHEAD',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM m302_tenant t CROSS JOIN m302_permission p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m
                  WHERE m.tenant_id=t.tenant_id AND m.perms=p.permission_code AND m.deleted_flag=0);

SET @m302_role_menu_base=(SELECT GREATEST(COALESCE(MAX(id),0),302100000000000000) FROM sys_role_menu);
INSERT INTO sys_role_menu (id,tenant_id,role_id,menu_id)
SELECT @m302_role_menu_base+ROW_NUMBER() OVER (ORDER BY r.tenant_id,r.id,m.id),r.tenant_id,r.id,m.id
FROM sys_role r JOIN sys_menu m ON m.tenant_id=r.tenant_id
WHERE r.role_code='COMPANY_FINANCE' AND r.deleted_flag=0 AND r.status='ENABLE'
  AND m.deleted_flag=0 AND m.perms IN (SELECT permission_code FROM m302_permission)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x
                  WHERE x.tenant_id=r.tenant_id AND x.role_id=r.id AND x.menu_id=m.id);

DROP TEMPORARY TABLE m302_permission;
DROP TEMPORARY TABLE m302_tenant;
