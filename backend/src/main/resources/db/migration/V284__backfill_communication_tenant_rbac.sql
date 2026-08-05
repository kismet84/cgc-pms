-- Backfill communication RBAC for every tenant that owns an enabled role.
-- V283 was already applied locally; keep it immutable and correct the tenant scope forward.

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon,
     order_num, status, visible, created_by, updated_by, remark, created_at, updated_at, deleted_flag)
SELECT bounds.max_id + ROW_NUMBER() OVER (ORDER BY tenants.tenant_id),
       tenants.tenant_id, 0, '站内通讯', 'MENU', '/communication', 'communication/index',
       'communication:view', 'message-square', 12, 'ENABLE', 1, 1, 1,
       'Internal communication entry', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM (
    SELECT DISTINCT tenant_id FROM sys_role WHERE status='ENABLE' AND deleted_flag=0
) tenants
CROSS JOIN (SELECT COALESCE(MAX(id),0) AS max_id FROM sys_menu) bounds
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu existing
    WHERE existing.tenant_id=tenants.tenant_id
      AND existing.perms='communication:view' AND existing.deleted_flag=0
);

INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon,
     order_num, status, visible, created_by, updated_by, remark, created_at, updated_at, deleted_flag)
SELECT bounds.max_id + ROW_NUMBER() OVER (ORDER BY parent.tenant_id),
       parent.tenant_id, parent.id, '发送消息', 'BUTTON', NULL, NULL,
       'communication:send', NULL, 1, 'ENABLE', 0, 1, 1,
       'Internal communication send authority', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM sys_menu parent
CROSS JOIN (SELECT COALESCE(MAX(id),0) AS max_id FROM sys_menu) bounds
WHERE parent.perms='communication:view' AND parent.deleted_flag=0
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu existing
      WHERE existing.tenant_id=parent.tenant_id
        AND existing.perms='communication:send' AND existing.deleted_flag=0
  );
INSERT INTO sys_menu
    (id, tenant_id, parent_id, menu_name, menu_type, path, component, perms, icon,
     order_num, status, visible, created_by, updated_by, remark, created_at, updated_at, deleted_flag)
SELECT bounds.max_id + ROW_NUMBER() OVER (ORDER BY parent.tenant_id),
       parent.tenant_id, parent.id, '管理群聊', 'BUTTON', NULL, NULL,
       'communication:group:manage', NULL, 2, 'ENABLE', 0, 1, 1,
       'Internal communication group authority', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM sys_menu parent
CROSS JOIN (SELECT COALESCE(MAX(id),0) AS max_id FROM sys_menu) bounds
WHERE parent.perms='communication:view' AND parent.deleted_flag=0
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu existing
      WHERE existing.tenant_id=parent.tenant_id
        AND existing.perms='communication:group:manage' AND existing.deleted_flag=0
  );

INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT bounds.max_id + ROW_NUMBER() OVER (ORDER BY role_row.tenant_id,role_row.id,menu_row.id),
       role_row.tenant_id, role_row.id, menu_row.id
FROM sys_role role_row
JOIN sys_menu menu_row ON menu_row.tenant_id=role_row.tenant_id
    AND menu_row.perms IN ('communication:view','communication:send')
    AND menu_row.deleted_flag=0
CROSS JOIN (SELECT COALESCE(MAX(id),0) AS max_id FROM sys_role_menu) bounds
WHERE role_row.status='ENABLE' AND role_row.deleted_flag=0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu existing
      WHERE existing.tenant_id=role_row.tenant_id
        AND existing.role_id=role_row.id AND existing.menu_id=menu_row.id
  );

INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT bounds.max_id + ROW_NUMBER() OVER (ORDER BY role_row.tenant_id,role_row.id,menu_row.id),
       role_row.tenant_id, role_row.id, menu_row.id
FROM sys_role role_row
JOIN sys_menu menu_row ON menu_row.tenant_id=role_row.tenant_id
    AND menu_row.perms='communication:group:manage' AND menu_row.deleted_flag=0
CROSS JOIN (SELECT COALESCE(MAX(id),0) AS max_id FROM sys_role_menu) bounds
WHERE UPPER(role_row.role_code) IN ('PROJECT_MANAGER','DEPARTMENT_MANAGER','GENERAL_MANAGER')
  AND role_row.status='ENABLE' AND role_row.deleted_flag=0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu existing
      WHERE existing.tenant_id=role_row.tenant_id
        AND existing.role_id=role_row.id AND existing.menu_id=menu_row.id
  );
