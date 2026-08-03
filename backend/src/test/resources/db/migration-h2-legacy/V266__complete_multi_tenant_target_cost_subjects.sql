-- Legacy H2 upgrade counterpart of V266__complete_multi_tenant_target_cost_subjects.sql.
UPDATE cost_subject
SET subject_name = '项目目标成本', subject_type = 'TARGET_COST', account_category = 'COST',
    level = 2, sort_order = 3, status = 'ENABLE', default_target_ratio = NULL
WHERE subject_code = '5401.03' AND deleted_flag = 0;

INSERT INTO cost_subject
    (id, tenant_id, parent_id, subject_code, subject_name, subject_type, account_category,
     level, sort_order, status, default_target_ratio, created_at, updated_at, deleted_flag)
SELECT 266000100000000 + ROW_NUMBER() OVER (ORDER BY p.tenant_id, d.sort_order),
       p.tenant_id, p.id, d.subject_code, d.subject_name, d.subject_type, 'COST',
       3, d.sort_order, 'ENABLE', d.default_target_ratio, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM cost_subject p
JOIN (
    SELECT '5401.03.01' AS subject_code, '人工成本' AS subject_name, 'LABOR' AS subject_type, 1 AS sort_order, CAST(25.0000 AS DECIMAL(7,4)) AS default_target_ratio
    UNION ALL SELECT '5401.03.02', '材料及工程设备成本', 'MATERIAL', 2, CAST(40.0000 AS DECIMAL(7,4))
    UNION ALL SELECT '5401.03.03', '施工机械成本', 'MACHINERY', 3, CAST(5.0000 AS DECIMAL(7,4))
    UNION ALL SELECT '5401.03.04', '分包成本', 'SUBCONTRACT', 4, CAST(5.0000 AS DECIMAL(7,4))
    UNION ALL SELECT '5401.03.05', '施工措施成本', 'MEASURES', 5, CAST(5.0000 AS DECIMAL(7,4))
    UNION ALL SELECT '5401.03.06', '项目现场管理成本', 'SITE_MANAGEMENT', 6, CAST(3.0000 AS DECIMAL(7,4))
    UNION ALL SELECT '5401.03.07', '公司管理费分摊', 'OVERHEAD', 7, CAST(5.0000 AS DECIMAL(7,4))
    UNION ALL SELECT '5401.03.08', '其他专项成本', 'SPECIAL', 8, CAST(1.0000 AS DECIMAL(7,4))
    UNION ALL SELECT '5401.03.09', '财务及税费成本', 'FINANCE_TAX', 9, CAST(8.0000 AS DECIMAL(7,4))
    UNION ALL SELECT '5401.03.10', '风险准备成本', 'RISK_RESERVE', 10, CAST(3.0000 AS DECIMAL(7,4))
) d ON 1 = 1
WHERE p.subject_code = '5401.03' AND p.deleted_flag = 0
  AND NOT EXISTS (
      SELECT 1 FROM cost_subject existing
      WHERE existing.tenant_id = p.tenant_id AND existing.subject_code = d.subject_code AND existing.deleted_flag = 0
  );

INSERT INTO cost_subject
    (id, tenant_id, parent_id, subject_code, subject_name, subject_type, account_category,
     level, sort_order, status, default_target_ratio, created_at, updated_at, deleted_flag)
SELECT 266000200000000 + ROW_NUMBER() OVER (ORDER BY p.tenant_id, d.sort_order),
       p.tenant_id, p.id, d.subject_code, d.subject_name, 'OVERHEAD', 'COST',
       3, d.sort_order, 'ENABLE', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM cost_subject p
JOIN (
    SELECT '5401.04.06' AS subject_code, '低值易耗品摊销' AS subject_name, 6 AS sort_order
    UNION ALL SELECT '5401.04.10', '排污费', 10
    UNION ALL SELECT '5401.04.11', '劳动保护费', 11
    UNION ALL SELECT '5401.04.12', '取暖费', 12
    UNION ALL SELECT '5401.04.13', '材料整理及零星运费', 13
    UNION ALL SELECT '5401.04.15', '外单位管理费', 15
    UNION ALL SELECT '5401.04.16', '职工教育经费', 16
    UNION ALL SELECT '5401.04.17', '工会经费', 17
    UNION ALL SELECT '5401.04.18', '劳动保险费', 18
) d ON 1 = 1
WHERE p.subject_code = '5401.04' AND p.deleted_flag = 0
  AND NOT EXISTS (
      SELECT 1 FROM cost_subject existing
      WHERE existing.tenant_id = p.tenant_id AND existing.subject_code = d.subject_code AND existing.deleted_flag = 0
  );
