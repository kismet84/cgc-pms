-- V55: Seed partner_type and cost_subject_type dictionary entries
-- These support dynamic type dropdowns in partner and cost-subject pages.

-- Insert dict types (idempotent via IGNORE-like pattern)
INSERT IGNORE INTO sys_dict_type (id, tenant_id, dict_code, dict_name, status, created_at, updated_at)
VALUES (FLOOR(RAND() * 9000000000000000000) + 1000000000000000000, 1, 'partner_type', '合作方类型', 'ENABLED', NOW(), NOW());

INSERT IGNORE INTO sys_dict_type (id, tenant_id, dict_code, dict_name, status, created_at, updated_at)
VALUES (FLOOR(RAND() * 9000000000000000000) + 1000000000000000000, 1, 'cost_subject_type', '成本科目类型', 'ENABLED', NOW(), NOW());

-- Insert dict data for partner_type (甲/乙/其他, 甲方置顶)
INSERT IGNORE INTO sys_dict_data (id, tenant_id, dict_type_id, dict_label, dict_value, order_num, status, created_at, updated_at)
SELECT FLOOR(RAND() * 9000000000000000000) + 1000000000000000000, 1, t.id, '甲方', 'PARTY_A', 0, 'ENABLED', NOW(), NOW()
FROM sys_dict_type t WHERE t.dict_code = 'partner_type' AND t.tenant_id = 1;

INSERT IGNORE INTO sys_dict_data (id, tenant_id, dict_type_id, dict_label, dict_value, order_num, status, created_at, updated_at)
SELECT FLOOR(RAND() * 9000000000000000000) + 1000000000000000000, 1, t.id, '乙方', 'PARTY_B', 1, 'ENABLED', NOW(), NOW()
FROM sys_dict_type t WHERE t.dict_code = 'partner_type' AND t.tenant_id = 1;

INSERT IGNORE INTO sys_dict_data (id, tenant_id, dict_type_id, dict_label, dict_value, order_num, status, created_at, updated_at)
SELECT FLOOR(RAND() * 9000000000000000000) + 1000000000000000000, 1, t.id, '其他', 'OTHER', 2, 'ENABLED', NOW(), NOW()
FROM sys_dict_type t WHERE t.dict_code = 'partner_type' AND t.tenant_id = 1;

-- Insert dict data for cost_subject_type
INSERT IGNORE INTO sys_dict_data (id, tenant_id, dict_type_id, dict_label, dict_value, order_num, status, created_at, updated_at)
SELECT FLOOR(RAND() * 9000000000000000000) + 1000000000000000000, 1, t.id, '材料费', 'MATERIAL', 0, 'ENABLED', NOW(), NOW()
FROM sys_dict_type t WHERE t.dict_code = 'cost_subject_type' AND t.tenant_id = 1;

INSERT IGNORE INTO sys_dict_data (id, tenant_id, dict_type_id, dict_label, dict_value, order_num, status, created_at, updated_at)
SELECT FLOOR(RAND() * 9000000000000000000) + 1000000000000000000, 1, t.id, '人工费', 'LABOR', 1, 'ENABLED', NOW(), NOW()
FROM sys_dict_type t WHERE t.dict_code = 'cost_subject_type' AND t.tenant_id = 1;

INSERT IGNORE INTO sys_dict_data (id, tenant_id, dict_type_id, dict_label, dict_value, order_num, status, created_at, updated_at)
SELECT FLOOR(RAND() * 9000000000000000000) + 1000000000000000000, 1, t.id, '机械费', 'MACHINERY', 2, 'ENABLED', NOW(), NOW()
FROM sys_dict_type t WHERE t.dict_code = 'cost_subject_type' AND t.tenant_id = 1;

INSERT IGNORE INTO sys_dict_data (id, tenant_id, dict_type_id, dict_label, dict_value, order_num, status, created_at, updated_at)
SELECT FLOOR(RAND() * 9000000000000000000) + 1000000000000000000, 1, t.id, '分包费', 'SUBCONTRACT', 3, 'ENABLED', NOW(), NOW()
FROM sys_dict_type t WHERE t.dict_code = 'cost_subject_type' AND t.tenant_id = 1;

INSERT IGNORE INTO sys_dict_data (id, tenant_id, dict_type_id, dict_label, dict_value, order_num, status, created_at, updated_at)
SELECT FLOOR(RAND() * 9000000000000000000) + 1000000000000000000, 1, t.id, '其他费用', 'OTHER', 4, 'ENABLED', NOW(), NOW()
FROM sys_dict_type t WHERE t.dict_code = 'cost_subject_type' AND t.tenant_id = 1;
