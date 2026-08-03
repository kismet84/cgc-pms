-- Legacy H2 upgrade counterpart of V264__restructure_project_target_cost_subjects.sql.
ALTER TABLE cost_subject ADD COLUMN IF NOT EXISTS default_target_ratio DECIMAL(7,4) NULL;
ALTER TABLE cost_subject ADD CONSTRAINT IF NOT EXISTS ck_cost_subject_target_ratio
    CHECK (default_target_ratio IS NULL OR (default_target_ratio >= 0 AND default_target_ratio <= 100));

ALTER TABLE cost_target ADD COLUMN IF NOT EXISTS source_contract_amount DECIMAL(19,2) NULL;
ALTER TABLE cost_target ADD COLUMN IF NOT EXISTS target_cost_rate DECIMAL(8,6) NULL;
ALTER TABLE cost_target ADD CONSTRAINT IF NOT EXISTS ck_cost_target_contract_snapshot
    CHECK (
        (source_contract_amount IS NULL AND target_cost_rate IS NULL)
        OR (source_contract_amount > 0 AND target_cost_rate = 0.850000)
    );

DELETE FROM cost_subject
WHERE subject_code LIKE '5401.02.%'
   OR subject_code LIKE '5401.03.%';
DELETE FROM cost_subject WHERE subject_code = '5401.02';

UPDATE cost_subject
SET subject_name = '项目目标成本',
    subject_type = 'TARGET_COST',
    account_category = 'COST',
    level = 2,
    sort_order = 3,
    status = 'ENABLE',
    default_target_ratio = NULL
WHERE tenant_id = 0 AND subject_code = '5401.03' AND deleted_flag = 0;

INSERT INTO cost_subject
    (id, tenant_id, parent_id, subject_code, subject_name, subject_type, account_category,
     level, sort_order, status, default_target_ratio, created_at, updated_at, deleted_flag)
SELECT d.id, p.tenant_id, p.id, d.subject_code, d.subject_name, d.subject_type, 'COST',
       3, d.sort_order, 'ENABLE', d.default_target_ratio, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM cost_subject p
JOIN (
    SELECT 901001 AS id, '5401.03.01' AS subject_code, '人工成本' AS subject_name, 'LABOR' AS subject_type, 1 AS sort_order, CAST(25.0000 AS DECIMAL(7,4)) AS default_target_ratio
    UNION ALL SELECT 901002, '5401.03.02', '材料及工程设备成本', 'MATERIAL', 2, CAST(40.0000 AS DECIMAL(7,4))
    UNION ALL SELECT 901003, '5401.03.03', '施工机械成本', 'MACHINERY', 3, CAST(5.0000 AS DECIMAL(7,4))
    UNION ALL SELECT 901004, '5401.03.04', '分包成本', 'SUBCONTRACT', 4, CAST(5.0000 AS DECIMAL(7,4))
    UNION ALL SELECT 901005, '5401.03.05', '施工措施成本', 'MEASURES', 5, CAST(5.0000 AS DECIMAL(7,4))
    UNION ALL SELECT 901006, '5401.03.06', '项目现场管理成本', 'SITE_MANAGEMENT', 6, CAST(3.0000 AS DECIMAL(7,4))
    UNION ALL SELECT 901007, '5401.03.07', '公司管理费分摊', 'OVERHEAD', 7, CAST(5.0000 AS DECIMAL(7,4))
    UNION ALL SELECT 901008, '5401.03.08', '其他专项成本', 'SPECIAL', 8, CAST(1.0000 AS DECIMAL(7,4))
    UNION ALL SELECT 901009, '5401.03.09', '财务及税费成本', 'FINANCE_TAX', 9, CAST(8.0000 AS DECIMAL(7,4))
    UNION ALL SELECT 901010, '5401.03.10', '风险准备成本', 'RISK_RESERVE', 10, CAST(3.0000 AS DECIMAL(7,4))
) d ON 1 = 1
WHERE p.tenant_id = 0 AND p.subject_code = '5401.03' AND p.deleted_flag = 0;
