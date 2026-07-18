-- 第 51 条主线：成本科目 V2 目标环境只读采集脚本。
--
-- 用途：在经授权的生产只读副本或同版本脱敏快照执行，为 Phase 0 提供数据质量、引用范围、
-- 投标费用结转与历史映射证据。
--
-- 安全边界：本文件仅包含 SELECT；不得在生产主库执行，不得与迁移、修复或 UPDATE/DELETE 脚本混用。
-- 前置：连接数据库后先执行第 1 节；只有 Flyway 已成功达到 V212 或后续兼容版本，才执行第 2-7 节。
-- 输出归档：脱敏保存结果至第 51 条主线 Phase 0 正式审计报告，不提交原始业务明细或个人信息。

-- 1. 环境、版本与结构前置。
SELECT DATABASE() AS database_name, CURRENT_USER() AS connected_user, NOW() AS audited_at;

SELECT installed_rank, version, description, type, success, installed_on
FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 20;

SELECT table_name, column_name, column_type, is_nullable, column_key
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name IN (
      'cost_subject', 'cost_item', 'cost_target', 'cost_target_item',
      'cost_forecast_item', 'project_budget_line', 'pay_application',
      'expense_application', 'stl_settlement_item', 'accounting_entry_line',
      'bid_cost', 'overhead_allocation_rule', 'overhead_allocation_run'
  )
  AND (column_name = 'cost_subject_id' OR table_name IN ('cost_subject', 'bid_cost'))
ORDER BY table_name, ordinal_position;

-- 2. 成本科目基线与树完整性。
SELECT tenant_id, account_category, status, COUNT(*) AS subject_count
FROM cost_subject
WHERE deleted_flag = 0
GROUP BY tenant_id, account_category, status
ORDER BY tenant_id, account_category, status;

SELECT tenant_id, account_category, subject_code, COUNT(*) AS duplicate_count,
       GROUP_CONCAT(CONCAT(id, ':', subject_name, ':', status) ORDER BY id SEPARATOR ' | ') AS subjects
FROM cost_subject
WHERE deleted_flag = 0
GROUP BY tenant_id, account_category, subject_code
HAVING COUNT(*) > 1
ORDER BY tenant_id, account_category, subject_code;

SELECT c.tenant_id, c.id, c.subject_code, c.subject_name, c.subject_type, c.status,
       c.parent_id, p.status AS parent_status, p.deleted_flag AS parent_deleted_flag
FROM cost_subject c
LEFT JOIN cost_subject p
       ON p.id = c.parent_id AND p.tenant_id = c.tenant_id
WHERE c.deleted_flag = 0
  AND c.parent_id <> 0
  AND (p.id IS NULL OR p.deleted_flag <> 0 OR p.status <> 'ENABLE')
ORDER BY c.tenant_id, c.subject_code, c.id;

-- 3. 所有现行引用表的覆盖率与未归类量。
SELECT 'cost_item' AS source_table, tenant_id,
       COUNT(*) AS total_rows,
       SUM(cost_subject_id IS NULL) AS unclassified_rows,
       SUM(cost_subject_id IS NOT NULL) AS classified_rows,
       COALESCE(SUM(CASE WHEN cost_subject_id IS NULL THEN amount ELSE 0 END), 0) AS unclassified_amount
FROM cost_item
WHERE deleted_flag = 0
GROUP BY tenant_id
UNION ALL
SELECT 'cost_target_item', tenant_id,
       COUNT(*), SUM(cost_subject_id IS NULL), SUM(cost_subject_id IS NOT NULL),
       COALESCE(SUM(CASE WHEN cost_subject_id IS NULL THEN target_amount ELSE 0 END), 0)
FROM cost_target_item
WHERE deleted_flag = 0
GROUP BY tenant_id
UNION ALL
SELECT 'cost_forecast_item', tenant_id,
       COUNT(*), SUM(cost_subject_id IS NULL), SUM(cost_subject_id IS NOT NULL),
       COALESCE(SUM(CASE WHEN cost_subject_id IS NULL THEN forecast_at_completion_amount ELSE 0 END), 0)
FROM cost_forecast_item
GROUP BY tenant_id
UNION ALL
SELECT 'project_budget_line', tenant_id,
       COUNT(*), SUM(cost_subject_id IS NULL), SUM(cost_subject_id IS NOT NULL),
       COALESCE(SUM(CASE WHEN cost_subject_id IS NULL THEN budget_amount ELSE 0 END), 0)
FROM project_budget_line
WHERE deleted_flag = 0
GROUP BY tenant_id
UNION ALL
SELECT 'pay_application', tenant_id,
       COUNT(*), SUM(cost_subject_id IS NULL), SUM(cost_subject_id IS NOT NULL),
       COALESCE(SUM(CASE WHEN cost_subject_id IS NULL THEN approved_amount ELSE 0 END), 0)
FROM pay_application
WHERE deleted_flag = 0
GROUP BY tenant_id
UNION ALL
SELECT 'expense_application', tenant_id,
       COUNT(*), SUM(cost_subject_id IS NULL), SUM(cost_subject_id IS NOT NULL),
       COALESCE(SUM(CASE WHEN cost_subject_id IS NULL THEN amount ELSE 0 END), 0)
FROM expense_application
WHERE deleted_flag = 0
GROUP BY tenant_id
UNION ALL
SELECT 'stl_settlement_item', tenant_id,
       COUNT(*), SUM(cost_subject_id IS NULL), SUM(cost_subject_id IS NOT NULL),
       COALESCE(SUM(CASE WHEN cost_subject_id IS NULL THEN amount ELSE 0 END), 0)
FROM stl_settlement_item
WHERE deleted_flag = 0
GROUP BY tenant_id
UNION ALL
SELECT 'accounting_entry_line', tenant_id,
       COUNT(*), SUM(cost_subject_id IS NULL), SUM(cost_subject_id IS NOT NULL),
       COALESCE(SUM(CASE WHEN cost_subject_id IS NULL THEN amount ELSE 0 END), 0)
FROM accounting_entry_line
GROUP BY tenant_id
ORDER BY source_table, tenant_id;

-- 4. 引用到无效、已删除或不属于同租户科目的事实。结果必须为 0。
SELECT 'cost_item' AS source_table, c.tenant_id, COUNT(*) AS invalid_reference_count
FROM cost_item c
LEFT JOIN cost_subject s ON s.id = c.cost_subject_id AND s.tenant_id = c.tenant_id
WHERE c.deleted_flag = 0 AND c.cost_subject_id IS NOT NULL
  AND (s.id IS NULL OR s.deleted_flag <> 0)
GROUP BY c.tenant_id
UNION ALL
SELECT 'cost_target_item', i.tenant_id, COUNT(*)
FROM cost_target_item i
LEFT JOIN cost_subject s ON s.id = i.cost_subject_id AND s.tenant_id = i.tenant_id
WHERE i.deleted_flag = 0 AND i.cost_subject_id IS NOT NULL
  AND (s.id IS NULL OR s.deleted_flag <> 0)
GROUP BY i.tenant_id
UNION ALL
SELECT 'cost_forecast_item', i.tenant_id, COUNT(*)
FROM cost_forecast_item i
LEFT JOIN cost_subject s ON s.id = i.cost_subject_id AND s.tenant_id = i.tenant_id
WHERE i.cost_subject_id IS NOT NULL
  AND (s.id IS NULL OR s.deleted_flag <> 0)
GROUP BY i.tenant_id
UNION ALL
SELECT 'project_budget_line', i.tenant_id, COUNT(*)
FROM project_budget_line i
LEFT JOIN cost_subject s ON s.id = i.cost_subject_id AND s.tenant_id = i.tenant_id
WHERE i.deleted_flag = 0 AND i.cost_subject_id IS NOT NULL
  AND (s.id IS NULL OR s.deleted_flag <> 0)
GROUP BY i.tenant_id
UNION ALL
SELECT 'pay_application', p.tenant_id, COUNT(*)
FROM pay_application p
LEFT JOIN cost_subject s ON s.id = p.cost_subject_id AND s.tenant_id = p.tenant_id
WHERE p.deleted_flag = 0 AND p.cost_subject_id IS NOT NULL
  AND (s.id IS NULL OR s.deleted_flag <> 0)
GROUP BY p.tenant_id
UNION ALL
SELECT 'expense_application', e.tenant_id, COUNT(*)
FROM expense_application e
LEFT JOIN cost_subject s ON s.id = e.cost_subject_id AND s.tenant_id = e.tenant_id
WHERE e.deleted_flag = 0 AND e.cost_subject_id IS NOT NULL
  AND (s.id IS NULL OR s.deleted_flag <> 0)
GROUP BY e.tenant_id
UNION ALL
SELECT 'stl_settlement_item', i.tenant_id, COUNT(*)
FROM stl_settlement_item i
LEFT JOIN cost_subject s ON s.id = i.cost_subject_id AND s.tenant_id = i.tenant_id
WHERE i.deleted_flag = 0 AND i.cost_subject_id IS NOT NULL
  AND (s.id IS NULL OR s.deleted_flag <> 0)
GROUP BY i.tenant_id
UNION ALL
SELECT 'accounting_entry_line', l.tenant_id, COUNT(*)
FROM accounting_entry_line l
LEFT JOIN cost_subject s ON s.id = l.cost_subject_id AND s.tenant_id = l.tenant_id
WHERE l.cost_subject_id IS NOT NULL
  AND (s.id IS NULL OR s.deleted_flag <> 0)
GROUP BY l.tenant_id
ORDER BY source_table, tenant_id;

-- 5. 投标费用：确认中标结转是否完整。现行实现把原 cost_item 从 BID_COST 改为
-- BID_COST_TRANSFERRED 并关联项目；它不写入 cost_target_item。此查询只采集事实，不作业务裁决。
SELECT b.tenant_id, b.id AS bid_cost_id, b.bid_status, b.project_id,
       COUNT(c.id) AS cost_item_count,
       COALESCE(SUM(CASE WHEN c.source_type = 'BID_COST' THEN 1 ELSE 0 END), 0) AS untransferred_item_count,
       COALESCE(SUM(CASE WHEN c.source_type = 'BID_COST_TRANSFERRED' THEN 1 ELSE 0 END), 0) AS transferred_item_count,
       COALESCE(SUM(CASE WHEN c.source_type = 'BID_COST_TRANSFERRED' THEN c.amount ELSE 0 END), 0) AS transferred_amount,
       COALESCE(SUM(CASE WHEN c.source_type = 'BID_COST_TRANSFERRED' AND c.cost_subject_id IS NULL THEN 1 ELSE 0 END), 0) AS transferred_unclassified_count
FROM bid_cost b
LEFT JOIN cost_item c
       ON c.tenant_id = b.tenant_id AND c.source_id = b.id AND c.deleted_flag = 0
      AND c.source_type IN ('BID_COST', 'BID_COST_TRANSFERRED')
WHERE b.deleted_flag = 0
GROUP BY b.tenant_id, b.id, b.bid_status, b.project_id
ORDER BY b.tenant_id, b.id;

SELECT c.tenant_id, c.source_id AS bid_cost_id, c.project_id, c.cost_subject_id,
       c.cost_status, c.cost_date, c.amount, c.id AS cost_item_id
FROM cost_item c
WHERE c.deleted_flag = 0 AND c.source_type = 'BID_COST_TRANSFERRED'
ORDER BY c.tenant_id, c.source_id, c.id;

-- 6. 质量安全与项目财务费用的现行使用量。先确认候选科目，再确认各来源事实。
SELECT tenant_id, id, subject_code, subject_name, subject_type, level, status
FROM cost_subject
WHERE deleted_flag = 0
  AND (subject_code LIKE '5401.03.%' OR subject_code = '5401.04.19'
       OR subject_name LIKE '%质量%' OR subject_name LIKE '%安全%' OR subject_name LIKE '%财务费用%')
ORDER BY tenant_id, subject_code, id;

SELECT c.tenant_id, s.subject_code, s.subject_name, c.source_type, c.cost_status,
       COUNT(*) AS item_count, COALESCE(SUM(c.amount), 0) AS total_amount
FROM cost_item c
JOIN cost_subject s ON s.id = c.cost_subject_id AND s.tenant_id = c.tenant_id
WHERE c.deleted_flag = 0
  AND (s.subject_code LIKE '5401.03.%' OR s.subject_code = '5401.04.19'
       OR s.subject_name LIKE '%质量%' OR s.subject_name LIKE '%安全%' OR s.subject_name LIKE '%财务费用%')
GROUP BY c.tenant_id, s.subject_code, s.subject_name, c.source_type, c.cost_status
ORDER BY c.tenant_id, s.subject_code, c.source_type, c.cost_status;

-- 7. 分摊事实与重复运行风险。现有 overhead 模块仅适用于 subject_type=OVERHEAD 的 COST 科目；
-- 该输出用于判断历史间接费与未来财务费用分摊是否需要隔离迁移。
SELECT r.tenant_id, r.id AS rule_id, r.cost_subject_id, s.subject_code, s.subject_name,
       s.subject_type, r.allocation_basis, r.allocation_cycle, r.status
FROM overhead_allocation_rule r
LEFT JOIN cost_subject s ON s.id = r.cost_subject_id AND s.tenant_id = r.tenant_id
WHERE r.deleted_flag = 0
ORDER BY r.tenant_id, r.id;

SELECT tenant_id, rule_id, period, COUNT(*) AS run_count,
       GROUP_CONCAT(CONCAT(id, ':', run_status, ':', allocated_amount) ORDER BY id SEPARATOR ' | ') AS runs
FROM overhead_allocation_run
WHERE deleted_flag = 0
GROUP BY tenant_id, rule_id, period
HAVING COUNT(*) > 1
ORDER BY tenant_id, rule_id, period;
