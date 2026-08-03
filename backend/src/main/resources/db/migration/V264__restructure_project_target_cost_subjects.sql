-- Restructure project target cost subjects. Foreign keys stay enabled so unknown
-- references fail closed; local cleanup must complete before this migration runs.
ALTER TABLE cost_subject
    ADD COLUMN default_target_ratio DECIMAL(7,4) NULL COMMENT '占目标成本比例，按百分数存储';

ALTER TABLE cost_subject
    ADD CONSTRAINT ck_cost_subject_target_ratio
        CHECK (default_target_ratio IS NULL OR (default_target_ratio >= 0 AND default_target_ratio <= 100));

ALTER TABLE cost_target
    ADD COLUMN source_contract_amount DECIMAL(19,2) NULL COMMENT '新建目标成本时项目合同金额快照',
    ADD COLUMN target_cost_rate DECIMAL(8,6) NULL COMMENT '新建目标成本率快照';

ALTER TABLE cost_target
    ADD CONSTRAINT ck_cost_target_contract_snapshot
        CHECK (
            (source_contract_amount IS NULL AND target_cost_rate IS NULL)
            OR (source_contract_amount > 0 AND target_cost_rate = 0.850000)
        );

-- V263 retired these standard 5401.04 children. This target-cost restructure
-- explicitly preserves the original 5401.04 tree, so restore their baseline rows.
INSERT INTO cost_subject
    (id, tenant_id, parent_id, subject_code, subject_name, subject_type, account_category,
     level, sort_order, status, default_target_ratio, created_by, updated_by, remark,
     created_at, updated_at, deleted_flag)
SELECT d.id, p.tenant_id, p.id, d.subject_code, d.subject_name, 'OVERHEAD', 'COST',
       3, d.sort_order, 'ENABLE', NULL, NULL, NULL, NULL,
       '2026-07-18 16:48:52', '2026-07-18 16:48:52', 0
FROM cost_subject p
JOIN (
    SELECT 900086 AS id, '5401.04.06' AS subject_code, '低值易耗品摊销' AS subject_name, 6 AS sort_order
    UNION ALL SELECT 900090, '5401.04.10', '排污费', 10
    UNION ALL SELECT 900091, '5401.04.11', '劳动保护费', 11
    UNION ALL SELECT 900092, '5401.04.12', '取暖费', 12
    UNION ALL SELECT 900093, '5401.04.13', '材料整理及零星运费', 13
    UNION ALL SELECT 900095, '5401.04.15', '外单位管理费', 15
    UNION ALL SELECT 900096, '5401.04.16', '职工教育经费', 16
    UNION ALL SELECT 900097, '5401.04.17', '工会经费', 17
    UNION ALL SELECT 900098, '5401.04.18', '劳动保险费', 18
) d ON 1 = 1
WHERE p.tenant_id = 0
  AND p.subject_code = '5401.04'
  AND p.deleted_flag = 0
  AND NOT EXISTS (
      SELECT 1 FROM cost_subject existing
      WHERE existing.tenant_id = p.tenant_id
        AND existing.subject_code = d.subject_code
  );

DELETE FROM cost_subject
WHERE subject_code LIKE '5401.02.%'
   OR subject_code LIKE '5401.03.%';

DELETE FROM cost_subject
WHERE subject_code = '5401.02';

UPDATE cost_subject
SET subject_name = '项目目标成本',
    subject_type = 'TARGET_COST',
    account_category = 'COST',
    level = 2,
    sort_order = 3,
    status = 'ENABLE',
    default_target_ratio = NULL
WHERE tenant_id = 0
  AND subject_code = '5401.03'
  AND deleted_flag = 0;

INSERT INTO cost_subject
    (id, tenant_id, parent_id, subject_code, subject_name, subject_type, account_category,
     level, sort_order, status, default_target_ratio, created_at, updated_at, deleted_flag)
SELECT d.id, p.tenant_id, p.id, d.subject_code, d.subject_name, d.subject_type, 'COST',
       3, d.sort_order, 'ENABLE', d.default_target_ratio, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM cost_subject p
JOIN (
    SELECT 901001 AS id, '5401.03.01' AS subject_code, '人工成本' AS subject_name, 'LABOR' AS subject_type, 1 AS sort_order, 25.0000 AS default_target_ratio
    UNION ALL SELECT 901002, '5401.03.02', '材料及工程设备成本', 'MATERIAL', 2, 40.0000
    UNION ALL SELECT 901003, '5401.03.03', '施工机械成本', 'MACHINERY', 3, 5.0000
    UNION ALL SELECT 901004, '5401.03.04', '分包成本', 'SUBCONTRACT', 4, 5.0000
    UNION ALL SELECT 901005, '5401.03.05', '施工措施成本', 'MEASURES', 5, 5.0000
    UNION ALL SELECT 901006, '5401.03.06', '项目现场管理成本', 'SITE_MANAGEMENT', 6, 3.0000
    UNION ALL SELECT 901007, '5401.03.07', '公司管理费分摊', 'OVERHEAD', 7, 5.0000
    UNION ALL SELECT 901008, '5401.03.08', '其他专项成本', 'SPECIAL', 8, 1.0000
    UNION ALL SELECT 901009, '5401.03.09', '财务及税费成本', 'FINANCE_TAX', 9, 8.0000
    UNION ALL SELECT 901010, '5401.03.10', '风险准备成本', 'RISK_RESERVE', 10, 3.0000
) d
WHERE p.tenant_id = 0
  AND p.subject_code = '5401.03'
  AND p.deleted_flag = 0;
