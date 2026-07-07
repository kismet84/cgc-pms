-- V78__seed_standard_cost_revenue_subjects.sql - 修复版
-- 仅用 INSERT IGNORE 不会报错，但子节点依赖硬编码 parent_id
-- 修复：子节点通过业务键查询父节点 ID，不再硬编码
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ═══════════════════════════════════════════════════════════
-- Phase 1: 插入根节点（用业务键判断是否已存在）
-- ═══════════════════════════════════════════════════════════

-- 确保根级别科目存在
INSERT IGNORE INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, level, account_category, subject_type, status, sort_order, deleted_flag)
VALUES
(900000, 0, 0, 'COST_ROOT', '成本科目根', 0, 'ROOT', NULL, 'ENABLE', 0, 0);

INSERT IGNORE INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, level, account_category, subject_type, status, sort_order, deleted_flag)
VALUES
(900001, 0, 900000, '5001', '合同成本', 1, 'COST', NULL, 'ENABLE', 10, 0),
(900002, 0, 900000, '6001', '合同收入', 1, 'REVENUE', NULL, 'ENABLE', 20, 0);

-- ═══════════════════════════════════════════════════════════
-- Phase 2: 子节点通过业务键查询父节点（不再硬编码）
-- ═══════════════════════════════════════════════════════════

-- 插入成本子科目 - 使用子查询获取父节点 ID
INSERT IGNORE INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, level, account_category, subject_type, status, sort_order, deleted_flag)
SELECT 900003, 0, COALESCE((SELECT id FROM cost_subject WHERE tenant_id=0 AND subject_code='5001' AND account_category='COST' AND deleted_flag=0 LIMIT 1), 900001),
       '5001.01', '分包成本-劳务', 2, 'COST', 'DIRECT', 'ENABLE', 10, 0
FROM DUAL;

INSERT IGNORE INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, level, account_category, subject_type, status, sort_order, deleted_flag)
SELECT 900004, 0, COALESCE((SELECT id FROM cost_subject WHERE tenant_id=0 AND subject_code='5001' AND account_category='COST' AND deleted_flag=0 LIMIT 1), 900001),
       '5001.02', '分包成本-机械', 2, 'COST', 'DIRECT', 'ENABLE', 20, 0
FROM DUAL;

INSERT IGNORE INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, level, account_category, subject_type, status, sort_order, deleted_flag)
SELECT 900005, 0, COALESCE((SELECT id FROM cost_subject WHERE tenant_id=0 AND subject_code='5001' AND account_category='COST' AND deleted_flag=0 LIMIT 1), 900001),
       '5001.03', '材料成本', 2, 'COST', 'DIRECT', 'ENABLE', 30, 0
FROM DUAL;

INSERT IGNORE INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, level, account_category, subject_type, status, sort_order, deleted_flag)
SELECT 900006, 0, COALESCE((SELECT id FROM cost_subject WHERE tenant_id=0 AND subject_code='5001' AND account_category='COST' AND deleted_flag=0 LIMIT 1), 900001),
       '5001.04', '间接费用', 2, 'COST', 'INDIRECT', 'ENABLE', 40, 0
FROM DUAL;

-- 收入子科目
INSERT IGNORE INTO cost_subject (id, tenant_id, parent_id, subject_code, subject_name, level, account_category, subject_type, status, sort_order, deleted_flag)
SELECT 900007, 0, COALESCE((SELECT id FROM cost_subject WHERE tenant_id=0 AND subject_code='6001' AND account_category='REVENUE' AND deleted_flag=0 LIMIT 1), 900002),
       '6001.01', '合同建造收入', 2, 'REVENUE', NULL, 'ENABLE', 10, 0
FROM DUAL;

SET FOREIGN_KEY_CHECKS = 1;
