-- V78: Seed standardized cost/revenue/settlement subjects (H2 compatible)
ALTER TABLE cost_subject ADD COLUMN IF NOT EXISTS account_category VARCHAR(20) DEFAULT 'COST' NOT NULL;

INSERT INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, subject_type, level, sort_order, status, account_category, created_at, updated_at, deleted_flag)
SELECT 900001, 0, 0, '5401', '合同履约成本', 'ROOT', 1, 1, 'ENABLE', 'COST', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM cost_subject WHERE id = 900001);

INSERT INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, subject_type, level, sort_order, status, account_category, created_at, updated_at, deleted_flag)
SELECT 900010, 0, 900001, '5401.01', '招投标及前期费用', 'BID', 2, 1, 'ENABLE', 'COST', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM cost_subject WHERE id = 900010);

INSERT INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, subject_type, level, sort_order, status, account_category, created_at, updated_at, deleted_flag)
SELECT 900030, 0, 900001, '5401.02', '采购阶段成本', 'PURCHASE', 2, 2, 'ENABLE', 'COST', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM cost_subject WHERE id = 900030);

INSERT INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, subject_type, level, sort_order, status, account_category, created_at, updated_at, deleted_flag)
SELECT 900040, 0, 900001, '5401.03', '施工阶段成本', 'CONSTRUCTION', 2, 3, 'ENABLE', 'COST', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM cost_subject WHERE id = 900040);

INSERT INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, subject_type, level, sort_order, status, account_category, created_at, updated_at, deleted_flag)
SELECT 900080, 0, 900001, '5401.04', '项目间接费用', 'OVERHEAD', 2, 4, 'ENABLE', 'COST', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM cost_subject WHERE id = 900080);

INSERT INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, subject_type, level, sort_order, status, account_category, created_at, updated_at, deleted_flag)
SELECT 900200, 0, 0, '6001', '主营业务收入', 'REVENUE_MAIN', 1, 1, 'ENABLE', 'REVENUE', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM cost_subject WHERE id = 900200);

INSERT INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, subject_type, level, sort_order, status, account_category, created_at, updated_at, deleted_flag)
SELECT 900210, 0, 0, '6051', '其他业务收入', 'REVENUE_OTHER', 1, 2, 'ENABLE', 'REVENUE', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM cost_subject WHERE id = 900210);

INSERT INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, subject_type, level, sort_order, status, account_category, created_at, updated_at, deleted_flag)
SELECT 900220, 0, 0, '6301', '营业外收入', 'REVENUE_EXTRA', 1, 3, 'ENABLE', 'REVENUE', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM cost_subject WHERE id = 900220);

INSERT INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, subject_type, level, sort_order, status, account_category, created_at, updated_at, deleted_flag)
SELECT 900300, 0, 0, 'SETTLE', '合同结算', 'SETTLEMENT', 1, 1, 'ENABLE', 'SETTLEMENT', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM cost_subject WHERE id = 900300);
