-- TECHNICAL_LEAD is the project member role used by the V2 demo/persona.
-- Keep technical write authority on that role instead of requiring legacy
-- CHIEF_ENGINEER or cross-domain administrative accounts.
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT bounds.max_id + ROW_NUMBER() OVER (ORDER BY role_row.tenant_id, role_row.id, menu_row.id),
       role_row.tenant_id, role_row.id, menu_row.id
FROM sys_role role_row
JOIN sys_menu menu_row ON menu_row.tenant_id = role_row.tenant_id
    AND menu_row.perms IN (
        'technical:drawing:receive',
        'technical:drawing:review',
        'technical:rfi:raise',
        'technical:rfi:respond',
        'technical:rfi:accept',
        'technical:disclosure:maintain',
        'technical:archive:confirm'
    )
    AND menu_row.status = 'ENABLE'
    AND menu_row.deleted_flag = 0
CROSS JOIN (SELECT COALESCE(MAX(id), 0) AS max_id FROM sys_role_menu) bounds
WHERE role_row.role_code = 'TECHNICAL_LEAD'
  AND role_row.status = 'ENABLE'
  AND role_row.deleted_flag = 0
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_menu existing
      WHERE existing.tenant_id = role_row.tenant_id
        AND existing.role_id = role_row.id
        AND existing.menu_id = menu_row.id
  );
