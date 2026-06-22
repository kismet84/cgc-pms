-- V90__h2_integration_test_seed_data.sql
-- 为集成测试提供硬编码 ID 的种子数据
-- H2 不支持 SELECT * FROM (VALUES ...) 无名列，使用 VALUES + 列别名
-- 所有列名与实际 H2 表结构（含所有 ALTER）对齐

INSERT INTO pm_project (id, tenant_id, project_code, project_name, project_type, status, approval_status, contract_amount, target_cost, created_by, remark, planned_start_date, planned_end_date)
SELECT v.id, v.tid, v.code, v.name, v.type, v.st, v.ast, v.camt, v.tcst, v.cby, v.rmk, v.psd, v.ped
FROM (VALUES (10001, 0, 'PRJ-2026-001', '麓谷科技产业园一期', '房建工程', 'ACTIVE', 'APPROVED', CAST(50000000.00 AS DECIMAL(18,2)), CAST(42000000.00 AS DECIMAL(18,2)), 1, 'H2集成测试种子项目', DATE '2026-01-01', DATE '2026-12-31'))
  AS v(id, tid, code, name, type, st, ast, camt, tcst, cby, rmk, psd, ped)
WHERE NOT EXISTS (SELECT 1 FROM pm_project WHERE id = 10001);

INSERT INTO md_partner (id, tenant_id, partner_code, partner_name, partner_type, blacklist_flag, status, remark)
SELECT v.id, v.tid, v.code, v.name, v.type, v.bl, v.st, v.rmk
FROM (VALUES (20001, 0, 'PT-A-001', '恒大建设集团', 'PARTY_A', 0, 'ENABLE', '甲方-集成测试'))
  AS v(id, tid, code, name, type, bl, st, rmk)
WHERE NOT EXISTS (SELECT 1 FROM md_partner WHERE id = 20001);

INSERT INTO md_partner (id, tenant_id, partner_code, partner_name, partner_type, blacklist_flag, status, remark)
SELECT v.id, v.tid, v.code, v.name, v.type, v.bl, v.st, v.rmk
FROM (VALUES (20002, 0, 'PT-B-001', '中建三局', 'PARTY_B', 0, 'ENABLE', '乙方-集成测试'))
  AS v(id, tid, code, name, type, bl, st, rmk)
WHERE NOT EXISTS (SELECT 1 FROM md_partner WHERE id = 20002);

INSERT INTO ct_contract (id, tenant_id, project_id, party_a_id, party_b_id, contract_code, contract_name, contract_type, contract_amount, current_amount, contract_status, approval_status, start_date, end_date, cost_generated_flag, created_by, remark)
SELECT v.id, v.tid, v.pid, v.pa, v.pb, v.code, v.name, v.type, v.camt, v.curamt, v.cst, v.apst, v.sd, v.ed, v.cg, v.cby, v.rmk
FROM (VALUES (30001, 0, 10001, 20001, 20002, 'CT-2026-001', '主体结构施工合同', 'SUB', CAST(50000000.00 AS DECIMAL(18,2)), CAST(50000000.00 AS DECIMAL(18,2)), 'PERFORMING', 'APPROVED', DATE '2026-01-01', DATE '2027-06-30', 0, 1, '集成测试合同'))
  AS v(id, tid, pid, pa, pb, code, name, type, camt, curamt, cst, apst, sd, ed, cg, cby, rmk)
WHERE NOT EXISTS (SELECT 1 FROM ct_contract WHERE id = 30001);

INSERT INTO pm_project_member (id, tenant_id, project_id, user_id, role_code)
SELECT v.id, v.tid, v.pid, v.uid, v.role
FROM (VALUES (40001, 0, 10001, 1, 'PROJECT_MANAGER'))
  AS v(id, tid, pid, uid, role)
WHERE NOT EXISTS (SELECT 1 FROM pm_project_member WHERE id = 40001);

INSERT INTO org_company (id, tenant_id, company_code, company_name, status, created_by, remark)
SELECT v.id, v.tid, v.code, v.name, v.st, v.cby, v.rmk
FROM (VALUES (90001, 0, 'COMP-TEST', '测试公司', 'ENABLE', 1, '集成测试公司'))
  AS v(id, tid, code, name, st, cby, rmk)
WHERE NOT EXISTS (SELECT 1 FROM org_company WHERE id = 90001);

INSERT INTO org_department (id, tenant_id, dept_code, dept_name, company_id, status, created_by, remark)
SELECT v.id, v.tid, v.code, v.name, v.cid, v.st, v.cby, v.rmk
FROM (VALUES (90002, 0, 'DEPT-TEST', '测试部门', 90001, 'ENABLE', 1, '集成测试部门'))
  AS v(id, tid, code, name, cid, st, cby, rmk)
WHERE NOT EXISTS (SELECT 1 FROM org_department WHERE id = 90002);
