-- H2 counterpart of V300__unify_accounting_subject_catalog.sql.
INSERT INTO cost_subject
    (id, tenant_id, parent_id, subject_code, subject_name, subject_type, account_category,
     level, sort_order, status, remark, created_at, updated_at, deleted_flag)
SELECT 300000000000000 + ROW_NUMBER() OVER (ORDER BY tenants.tenant_id, catalog.sort_order),
       tenants.tenant_id, 0, catalog.subject_code, catalog.subject_name, 'GENERAL_LEDGER',
       catalog.account_category, 1, catalog.sort_order, 'ENABLE', '系统记账科目',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM (SELECT DISTINCT tenant_id FROM cost_subject WHERE deleted_flag = 0) tenants
JOIN (
    SELECT '1002-BANK' AS subject_code, '银行存款' AS subject_name, 'ASSET' AS account_category, 10 AS sort_order
    UNION ALL SELECT '1122-AR', '应收账款', 'ASSET', 20
    UNION ALL SELECT '1123-PREPAY', '预付账款', 'ASSET', 30
    UNION ALL SELECT '2202-AP', '应付账款', 'LIABILITY', 40
    UNION ALL SELECT '2203-ADVANCE', '预收账款', 'LIABILITY', 50
) catalog ON 1 = 1
WHERE NOT EXISTS (
    SELECT 1 FROM cost_subject existing
    WHERE existing.tenant_id = tenants.tenant_id
      AND existing.subject_code = catalog.subject_code
      AND existing.deleted_flag = 0
);

UPDATE sys_menu
SET menu_name = '会计科目', updated_at = CURRENT_TIMESTAMP
WHERE path = '/cost/subject' AND deleted_flag = 0;
