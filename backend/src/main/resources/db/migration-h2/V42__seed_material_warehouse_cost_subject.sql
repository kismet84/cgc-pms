-- V42: Seed material, warehouse, cost subject reference data + business roles (H2)
-- Fixes P2-05 (missing material/warehouse seed), P2-06 (missing cost subject seed), P3-04 (missing roles)

-- ----------------------------
-- 成本科目种子数据 (P2-06)
-- ----------------------------
INSERT INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, subject_type, level, sort_order, status, created_at, updated_at, deleted_flag)
SELECT 1001, 0, 0, 'COST_ROOT', '工程成本', NULL, 1, 1, 'ENABLE', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM cost_subject WHERE id = 1001);

INSERT INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, subject_type, level, sort_order, status, created_at, updated_at, deleted_flag)
SELECT 1002, 0, 1001, 'COST_MATERIAL', '材料成本', '材料', 2, 1, 'ENABLE', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM cost_subject WHERE id = 1002);

INSERT INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, subject_type, level, sort_order, status, created_at, updated_at, deleted_flag)
SELECT 1003, 0, 1001, 'COST_SUBCONTRACT', '分包成本', '分包', 2, 2, 'ENABLE', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM cost_subject WHERE id = 1003);

INSERT INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, subject_type, level, sort_order, status, created_at, updated_at, deleted_flag)
SELECT 1004, 0, 1001, 'COST_LABOR', '人工成本', '人工', 2, 3, 'ENABLE', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM cost_subject WHERE id = 1004);

INSERT INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, subject_type, level, sort_order, status, created_at, updated_at, deleted_flag)
SELECT 1005, 0, 1001, 'COST_MACHINERY', '机械成本', '机械', 2, 4, 'ENABLE', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM cost_subject WHERE id = 1005);

INSERT INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, subject_type, level, sort_order, status, created_at, updated_at, deleted_flag)
SELECT 1006, 0, 1001, 'COST_OTHER', '其他成本', '其他', 2, 5, 'ENABLE', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM cost_subject WHERE id = 1006);

-- ----------------------------
-- 物料字典种子数据 (P2-05)
-- ----------------------------
INSERT INTO md_material (id, tenant_id, material_code, material_name, specification, unit, status, created_at, updated_at, deleted_flag)
SELECT 1, 0, 'MAT-001', '螺纹钢 HRB400 Φ12', 'Φ12mm×9m', '吨', 'ENABLE', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM md_material WHERE id = 1);

INSERT INTO md_material (id, tenant_id, material_code, material_name, specification, unit, status, created_at, updated_at, deleted_flag)
SELECT 2, 0, 'MAT-002', '商品混凝土 C30', 'C30', '立方米', 'ENABLE', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM md_material WHERE id = 2);

INSERT INTO md_material (id, tenant_id, material_code, material_name, specification, unit, status, created_at, updated_at, deleted_flag)
SELECT 3, 0, 'MAT-003', '水泥 P.O 42.5', 'P.O 42.5', '吨', 'ENABLE', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM md_material WHERE id = 3);

INSERT INTO md_material (id, tenant_id, material_code, material_name, specification, unit, status, created_at, updated_at, deleted_flag)
SELECT 4, 0, 'MAT-004', '砂子 中砂', '中砂', '吨', 'ENABLE', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM md_material WHERE id = 4);

INSERT INTO md_material (id, tenant_id, material_code, material_name, specification, unit, status, created_at, updated_at, deleted_flag)
SELECT 5, 0, 'MAT-005', '碎石 5-31.5mm', '5-31.5mm', '吨', 'ENABLE', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM md_material WHERE id = 5);

-- ----------------------------
-- 仓库种子数据 (P2-05)
-- ----------------------------
INSERT INTO mat_warehouse (id, tenant_id, project_id, warehouse_code, warehouse_name, status, created_time, updated_time, deleted_flag)
SELECT 1, 0, 10001, 'WH-001', '主仓库', 'ENABLE', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM mat_warehouse WHERE id = 1);

INSERT INTO mat_warehouse (id, tenant_id, project_id, warehouse_code, warehouse_name, status, created_time, updated_time, deleted_flag)
SELECT 2, 0, 10001, 'WH-002', '材料堆场', 'ENABLE', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM mat_warehouse WHERE id = 2);

-- ----------------------------
-- 业务角色种子数据 (P3-04)
-- ----------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, role_type, status, data_scope, created_by, remark)
SELECT 5, 0, 'MATERIAL_CLERK', '材料员', 'BUSINESS', 'ENABLE', 3, 1, '负责材料验收、出入库管理'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 5);

INSERT INTO sys_role (id, tenant_id, role_code, role_name, role_type, status, data_scope, created_by, remark)
SELECT 6, 0, 'FINANCE', '财务人员', 'BUSINESS', 'ENABLE', 3, 1, '负责付款、发票、结算相关操作'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 6);
