-- V55: Seed partner_type and cost_subject_type dictionary entries (H2 version)
-- These support dynamic type dropdowns in partner and cost-subject pages.

-- Insert dict types (idempotent: skip if dict_code already exists for tenant 1)
INSERT INTO sys_dict_type (id, tenant_id, dict_code, dict_name, status, created_at, updated_at)
SELECT 9001, 1, 'partner_type', '合作方类型', 'ENABLED', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE tenant_id = 1 AND dict_code = 'partner_type');

INSERT INTO sys_dict_type (id, tenant_id, dict_code, dict_name, status, created_at, updated_at)
SELECT 9002, 1, 'cost_subject_type', '成本科目类型', 'ENABLED', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE tenant_id = 1 AND dict_code = 'cost_subject_type');

-- Insert dict data for partner_type (甲/乙/其他, 甲方置顶)
INSERT INTO sys_dict_data (id, tenant_id, dict_type_id, dict_label, dict_value, order_num, status, created_at, updated_at)
SELECT 9101, 1, t.id, '甲方', 'PARTY_A', 0, 'ENABLED', NOW(), NOW()
FROM sys_dict_type t WHERE t.dict_code = 'partner_type' AND t.tenant_id = 1
AND NOT EXISTS (SELECT 1 FROM sys_dict_data d WHERE d.dict_type_id = t.id AND d.dict_value = 'PARTY_A');

INSERT INTO sys_dict_data (id, tenant_id, dict_type_id, dict_label, dict_value, order_num, status, created_at, updated_at)
SELECT 9102, 1, t.id, '乙方', 'PARTY_B', 1, 'ENABLED', NOW(), NOW()
FROM sys_dict_type t WHERE t.dict_code = 'partner_type' AND t.tenant_id = 1
AND NOT EXISTS (SELECT 1 FROM sys_dict_data d WHERE d.dict_type_id = t.id AND d.dict_value = 'PARTY_B');

INSERT INTO sys_dict_data (id, tenant_id, dict_type_id, dict_label, dict_value, order_num, status, created_at, updated_at)
SELECT 9103, 1, t.id, '其他', 'OTHER', 2, 'ENABLED', NOW(), NOW()
FROM sys_dict_type t WHERE t.dict_code = 'partner_type' AND t.tenant_id = 1
AND NOT EXISTS (SELECT 1 FROM sys_dict_data d WHERE d.dict_type_id = t.id AND d.dict_value = 'OTHER');

-- Insert dict data for cost_subject_type
INSERT INTO sys_dict_data (id, tenant_id, dict_type_id, dict_label, dict_value, order_num, status, created_at, updated_at)
SELECT 9201, 1, t.id, '材料费', 'MATERIAL', 0, 'ENABLED', NOW(), NOW()
FROM sys_dict_type t WHERE t.dict_code = 'cost_subject_type' AND t.tenant_id = 1
AND NOT EXISTS (SELECT 1 FROM sys_dict_data d WHERE d.dict_type_id = t.id AND d.dict_value = 'MATERIAL');

INSERT INTO sys_dict_data (id, tenant_id, dict_type_id, dict_label, dict_value, order_num, status, created_at, updated_at)
SELECT 9202, 1, t.id, '人工费', 'LABOR', 1, 'ENABLED', NOW(), NOW()
FROM sys_dict_type t WHERE t.dict_code = 'cost_subject_type' AND t.tenant_id = 1
AND NOT EXISTS (SELECT 1 FROM sys_dict_data d WHERE d.dict_type_id = t.id AND d.dict_value = 'LABOR');

INSERT INTO sys_dict_data (id, tenant_id, dict_type_id, dict_label, dict_value, order_num, status, created_at, updated_at)
SELECT 9203, 1, t.id, '机械费', 'MACHINERY', 2, 'ENABLED', NOW(), NOW()
FROM sys_dict_type t WHERE t.dict_code = 'cost_subject_type' AND t.tenant_id = 1
AND NOT EXISTS (SELECT 1 FROM sys_dict_data d WHERE d.dict_type_id = t.id AND d.dict_value = 'MACHINERY');

INSERT INTO sys_dict_data (id, tenant_id, dict_type_id, dict_label, dict_value, order_num, status, created_at, updated_at)
SELECT 9204, 1, t.id, '分包费', 'SUBCONTRACT', 3, 'ENABLED', NOW(), NOW()
FROM sys_dict_type t WHERE t.dict_code = 'cost_subject_type' AND t.tenant_id = 1
AND NOT EXISTS (SELECT 1 FROM sys_dict_data d WHERE d.dict_type_id = t.id AND d.dict_value = 'SUBCONTRACT');

INSERT INTO sys_dict_data (id, tenant_id, dict_type_id, dict_label, dict_value, order_num, status, created_at, updated_at)
SELECT 9205, 1, t.id, '其他费用', 'OTHER', 4, 'ENABLED', NOW(), NOW()
FROM sys_dict_type t WHERE t.dict_code = 'cost_subject_type' AND t.tenant_id = 1
AND NOT EXISTS (SELECT 1 FROM sys_dict_data d WHERE d.dict_type_id = t.id AND d.dict_value = 'OTHER');
