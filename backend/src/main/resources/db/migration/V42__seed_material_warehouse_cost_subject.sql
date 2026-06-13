-- V42: Seed material, warehouse, cost subject reference data + business roles
-- Fixes P2-05 (missing material/warehouse seed), P2-06 (missing cost subject seed), P3-04 (missing roles)
-- All INSERT IGNORE for idempotency

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 成本科目种子数据 (P2-06)
-- ----------------------------
INSERT IGNORE INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, subject_type, level, sort_order, status, created_at, updated_at, deleted_flag)
VALUES
-- Level 1: 根科目
(1001, 0, 0, 'COST_ROOT', '工程成本', NULL, 1, 1, 'ENABLE', NOW(), NOW(), 0),
-- Level 2: 五大成本类型
(1002, 0, 1001, 'COST_MATERIAL', '材料成本', '材料', 2, 1, 'ENABLE', NOW(), NOW(), 0),
(1003, 0, 1001, 'COST_SUBCONTRACT', '分包成本', '分包', 2, 2, 'ENABLE', NOW(), NOW(), 0),
(1004, 0, 1001, 'COST_LABOR', '人工成本', '人工', 2, 3, 'ENABLE', NOW(), NOW(), 0),
(1005, 0, 1001, 'COST_MACHINERY', '机械成本', '机械', 2, 4, 'ENABLE', NOW(), NOW(), 0),
(1006, 0, 1001, 'COST_OTHER', '其他成本', '其他', 2, 5, 'ENABLE', NOW(), NOW(), 0);

-- ----------------------------
-- 物料字典种子数据 (P2-05)
-- ----------------------------
INSERT IGNORE INTO md_material (id, tenant_id, material_code, material_name, spec, unit, unit_price, status, created_at, updated_at, deleted_flag)
VALUES
(1, 0, 'MAT-001', '螺纹钢 HRB400 Φ12', 'Φ12mm×9m', '吨', 4200.00, 'ENABLE', NOW(), NOW(), 0),
(2, 0, 'MAT-002', '商品混凝土 C30', 'C30', '立方米', 380.00, 'ENABLE', NOW(), NOW(), 0),
(3, 0, 'MAT-003', '水泥 P.O 42.5', 'P.O 42.5', '吨', 350.00, 'ENABLE', NOW(), NOW(), 0),
(4, 0, 'MAT-004', '砂子 中砂', '中砂', '吨', 80.00, 'ENABLE', NOW(), NOW(), 0),
(5, 0, 'MAT-005', '碎石 5-31.5mm', '5-31.5mm', '吨', 90.00, 'ENABLE', NOW(), NOW(), 0);

-- ----------------------------
-- 仓库种子数据 (P2-05)
-- ----------------------------
INSERT IGNORE INTO mat_warehouse (id, tenant_id, project_id, warehouse_code, warehouse_name, status, created_time, updated_time, deleted_flag)
VALUES
(1, 0, 10001, 'WH-001', '主仓库', 'ENABLE', NOW(), NOW(), 0),
(2, 0, 10001, 'WH-002', '材料堆场', 'ENABLE', NOW(), NOW(), 0);

-- ----------------------------
-- 业务角色种子数据 (P3-04)
-- ----------------------------
INSERT IGNORE INTO sys_role (id, tenant_id, role_code, role_name, role_type, status, data_scope, created_by, remark) VALUES
(5, 0, 'MATERIAL_CLERK', '材料员', 'BUSINESS', 'ENABLE', 3, 1, '负责材料验收、出入库管理'),
(6, 0, 'FINANCE', '财务人员', 'BUSINESS', 'ENABLE', 3, 1, '负责付款、发票、结算相关操作');

SET FOREIGN_KEY_CHECKS = 1;
