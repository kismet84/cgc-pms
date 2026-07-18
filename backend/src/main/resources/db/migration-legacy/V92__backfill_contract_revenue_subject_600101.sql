-- V92__backfill_contract_revenue_subject_600101.sql
-- 补齐 V78 缺失的主营业务收入二级科目，确保收入确认精确命中 6001.01

INSERT IGNORE INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, subject_type, level, sort_order, status, account_category, created_at, updated_at, deleted_flag)
VALUES
    (900201, 0, 900200, '6001.01', '合同建造收入', 'REVENUE_MAIN', 2, 1, 'ENABLE', 'REVENUE', NOW(), NOW(), 0),
    (900202, 0, 900200, '6001.02', '变更签证收入', 'REVENUE_MAIN', 2, 2, 'ENABLE', 'REVENUE', NOW(), NOW(), 0),
    (900203, 0, 900200, '6001.03', '索赔收入', 'REVENUE_MAIN', 2, 3, 'ENABLE', 'REVENUE', NOW(), NOW(), 0),
    (900204, 0, 900200, '6001.04', '奖励收入', 'REVENUE_MAIN', 2, 4, 'ENABLE', 'REVENUE', NOW(), NOW(), 0);
