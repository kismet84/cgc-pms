-- CGC-PMS V210+ 迁移后只读检查。
-- BLOCK 必须为 0；REVIEW 需要人工确认但不得伪装成已自动处置。

SELECT 'BLOCK' severity, 'missing type registry seeds' check_name,
       GREATEST(0,34-(SELECT COUNT(*) FROM sys_type_registry WHERE status='ACTIVE')) issue_count
UNION ALL
SELECT 'BLOCK', 'expense approval status null', COUNT(*) FROM expense_application WHERE deleted_flag=0 AND approval_status IS NULL
UNION ALL
SELECT 'BLOCK', 'settlement approval status null', COUNT(*) FROM stl_settlement WHERE deleted_flag=0 AND approval_status IS NULL
UNION ALL
SELECT 'BLOCK', 'material category cross-tenant/orphan', COUNT(*)
FROM md_material m LEFT JOIN md_material_category c ON c.id=m.category_id AND c.tenant_id=m.tenant_id
WHERE m.deleted_flag=0 AND m.category_id IS NOT NULL AND c.id IS NULL
UNION ALL
SELECT 'BLOCK', 'rbac user-role orphan', COUNT(*)
FROM sys_user_role ur LEFT JOIN sys_user u ON u.id=ur.user_id AND u.tenant_id=ur.tenant_id
LEFT JOIN sys_role r ON r.id=ur.role_id AND r.tenant_id=ur.tenant_id
WHERE u.id IS NULL OR r.id IS NULL
UNION ALL
SELECT 'BLOCK', 'rbac role-menu orphan', COUNT(*)
FROM sys_role_menu rm LEFT JOIN sys_role r ON r.id=rm.role_id AND r.tenant_id=rm.tenant_id
LEFT JOIN sys_menu m ON m.id=rm.menu_id
WHERE r.id IS NULL OR m.id IS NULL
UNION ALL
SELECT 'REVIEW', 'legacy overhead_allocation_record rows', COUNT(*) FROM overhead_allocation_record
UNION ALL
SELECT 'REVIEW', 'historical finance_alert without alert_log authority', COUNT(*) FROM finance_alert WHERE alert_log_id IS NULL
UNION ALL
SELECT 'REVIEW', 'materials awaiting category assignment', COUNT(*) FROM md_material WHERE deleted_flag=0 AND category_id IS NULL
UNION ALL
SELECT 'BLOCK', 'obsolete deleted_token columns remain', COUNT(*)
FROM information_schema.columns
WHERE table_schema = DATABASE() AND column_name = 'deleted_token';
