-- 第 51 条主线：成本科目 V2 上线前后只读对账脚本。
--
-- 安全边界：仅包含 SELECT；用于经授权的测试库、生产只读副本或脱敏快照。
-- 判定原则：标记为“必须为 0”的查询只要返回非零即停止上线；不得在本脚本中修数。

-- 1. 迁移与映射版本基线。V213 必须成功，且每租户最多一个 ACTIVE 版本。
SELECT installed_rank, version, description, success, installed_on
FROM flyway_schema_history
WHERE CAST(version AS UNSIGNED) >= 213
ORDER BY installed_rank;

SELECT tenant_id, status, COUNT(*) AS version_count
FROM cost_subject_mapping_version
GROUP BY tenant_id, status
ORDER BY tenant_id, status;

SELECT tenant_id, COUNT(*) AS active_version_count
FROM cost_subject_mapping_version
WHERE status = 'ACTIVE'
GROUP BY tenant_id
HAVING COUNT(*) > 1;

-- 1.1 V214旧科目清理。以下三个查询必须为空。
SELECT id, tenant_id, subject_code, subject_name
FROM cost_subject
WHERE id IN (1001,1002,1003,1004,1005,1006,900003,900004,900005,900006);

SELECT COUNT(*) AS cleanup_audit_count
FROM cost_subject_legacy_cleanup_audit
HAVING COUNT(*) <> 10;

SELECT reference_name, old_reference_count
FROM (
    SELECT 'cost_item' reference_name, COUNT(*) old_reference_count FROM cost_item WHERE cost_subject_id IN (1001,1002,1003,1004,1005,1006,900003,900004,900005,900006)
    UNION ALL SELECT 'cost_target_item',COUNT(*) FROM cost_target_item WHERE cost_subject_id IN (1001,1002,1003,1004,1005,1006,900003,900004,900005,900006)
    UNION ALL SELECT 'cost_forecast_item',COUNT(*) FROM cost_forecast_item WHERE cost_subject_id IN (1001,1002,1003,1004,1005,1006,900003,900004,900005,900006)
    UNION ALL SELECT 'project_budget_line',COUNT(*) FROM project_budget_line WHERE cost_subject_id IN (1001,1002,1003,1004,1005,1006,900003,900004,900005,900006)
    UNION ALL SELECT 'pay_application',COUNT(*) FROM pay_application WHERE cost_subject_id IN (1001,1002,1003,1004,1005,1006,900003,900004,900005,900006)
    UNION ALL SELECT 'expense_application',COUNT(*) FROM expense_application WHERE cost_subject_id IN (1001,1002,1003,1004,1005,1006,900003,900004,900005,900006)
    UNION ALL SELECT 'stl_settlement_item',COUNT(*) FROM stl_settlement_item WHERE cost_subject_id IN (1001,1002,1003,1004,1005,1006,900003,900004,900005,900006)
    UNION ALL SELECT 'accounting_entry_line',COUNT(*) FROM accounting_entry_line WHERE cost_subject_id IN (1001,1002,1003,1004,1005,1006,900003,900004,900005,900006)
    UNION ALL SELECT 'assignment_rule',COUNT(*) FROM cost_subject_assignment_rule WHERE cost_subject_id IN (1001,1002,1003,1004,1005,1006,900003,900004,900005,900006)
    UNION ALL SELECT 'project_scope',COUNT(*) FROM project_cost_subject_scope WHERE cost_subject_id IN (1001,1002,1003,1004,1005,1006,900003,900004,900005,900006)
    UNION ALL SELECT 'qs_consequence',COUNT(*) FROM qs_consequence WHERE cost_subject_id IN (1001,1002,1003,1004,1005,1006,900003,900004,900005,900006)
    UNION ALL SELECT 'finance_allocation',COUNT(*) FROM finance_cost_allocation_batch WHERE cost_subject_id IN (1001,1002,1003,1004,1005,1006,900003,900004,900005,900006)
    UNION ALL SELECT 'bid_transfer_source',COUNT(*) FROM bid_cost_target_transfer_line WHERE source_subject_id IN (1001,1002,1003,1004,1005,1006,900003,900004,900005,900006)
    UNION ALL SELECT 'bid_transfer_target',COUNT(*) FROM bid_cost_target_transfer_line WHERE target_subject_id IN (1001,1002,1003,1004,1005,1006,900003,900004,900005,900006)
    UNION ALL SELECT 'mapping_source',COUNT(*) FROM cost_subject_mapping_item WHERE source_subject_id IN (1001,1002,1003,1004,1005,1006,900003,900004,900005,900006)
    UNION ALL SELECT 'mapping_target',COUNT(*) FROM cost_subject_mapping_item WHERE target_subject_id IN (1001,1002,1003,1004,1005,1006,900003,900004,900005,900006)
    UNION ALL SELECT 'cost_summary',COUNT(*) FROM cost_summary WHERE cost_subject_id IN (1001,1002,1003,1004,1005,1006,900003,900004,900005,900006)
    UNION ALL SELECT 'overhead_rule',COUNT(*) FROM overhead_allocation_rule WHERE cost_subject_id IN (1001,1002,1003,1004,1005,1006,900003,900004,900005,900006)
    UNION ALL SELECT 'overhead_record',COUNT(*) FROM overhead_allocation_record WHERE cost_subject_id IN (1001,1002,1003,1004,1005,1006,900003,900004,900005,900006)
    UNION ALL SELECT 'var_order_item',COUNT(*) FROM var_order_item WHERE cost_subject_id IN (1001,1002,1003,1004,1005,1006,900003,900004,900005,900006)
) legacy_references
WHERE old_reference_count <> 0;

-- 2. 映射完整性。以下结果必须为 0：失效来源、失效目标、非末级目标。
SELECT i.tenant_id, i.mapping_version_id,
       SUM(s.id IS NULL OR s.deleted_flag <> 0) AS invalid_source_count,
       SUM(i.target_subject_id IS NOT NULL AND (t.id IS NULL OR t.deleted_flag <> 0 OR t.status <> 'ENABLE')) AS invalid_target_count,
       SUM(i.target_subject_id IS NOT NULL AND EXISTS (
           SELECT 1 FROM cost_subject child
           WHERE child.tenant_id = i.tenant_id
             AND child.parent_id = i.target_subject_id
             AND child.deleted_flag = 0
       )) AS non_leaf_target_count
FROM cost_subject_mapping_item i
LEFT JOIN cost_subject s ON s.id = i.source_subject_id AND s.tenant_id = i.tenant_id
LEFT JOIN cost_subject t ON t.id = i.target_subject_id AND t.tenant_id = i.tenant_id
GROUP BY i.tenant_id, i.mapping_version_id
HAVING invalid_source_count <> 0 OR invalid_target_count <> 0 OR non_leaf_target_count <> 0;

-- 3. 启用规则有效性和同等级冲突。以下结果必须为空。
SELECT r.tenant_id, r.id, r.rule_code, r.source_type, r.business_category,
       r.project_id, r.cost_subject_id, r.priority
FROM cost_subject_assignment_rule r
LEFT JOIN cost_subject_mapping_version v
       ON v.id = r.mapping_version_id AND v.tenant_id = r.tenant_id
LEFT JOIN cost_subject s
       ON s.id = r.cost_subject_id AND s.tenant_id = r.tenant_id
WHERE r.status = 'ACTIVE'
  AND (v.status <> 'ACTIVE' OR s.id IS NULL OR s.deleted_flag <> 0 OR s.status <> 'ENABLE'
       OR EXISTS (
           SELECT 1 FROM cost_subject child
           WHERE child.tenant_id = r.tenant_id
             AND child.parent_id = r.cost_subject_id
             AND child.deleted_flag = 0
       ));

SELECT tenant_id, source_type, business_category, COALESCE(project_id, 0) AS project_scope,
       priority, COUNT(*) AS conflicting_rule_count,
       GROUP_CONCAT(rule_code ORDER BY rule_code SEPARATOR ',') AS rule_codes
FROM cost_subject_assignment_rule
WHERE status = 'ACTIVE'
  AND effective_from <= CURRENT_DATE
  AND (effective_to IS NULL OR effective_to >= CURRENT_DATE)
GROUP BY tenant_id, source_type, business_category, COALESCE(project_id, 0), priority
HAVING COUNT(*) > 1;

-- 4. 项目适用范围。无效项目、无效科目、非末级科目必须为 0。
SELECT p.tenant_id,
       SUM(project.id IS NULL OR project.deleted_flag <> 0) AS invalid_project_count,
       SUM(s.id IS NULL OR s.deleted_flag <> 0 OR s.status <> 'ENABLE') AS invalid_subject_count,
       SUM(EXISTS (
           SELECT 1 FROM cost_subject child
           WHERE child.tenant_id = p.tenant_id
             AND child.parent_id = p.cost_subject_id
             AND child.deleted_flag = 0
       )) AS non_leaf_subject_count
FROM project_cost_subject_scope p
LEFT JOIN pm_project project ON project.id = p.project_id AND project.tenant_id = p.tenant_id
LEFT JOIN cost_subject s ON s.id = p.cost_subject_id AND s.tenant_id = p.tenant_id
GROUP BY p.tenant_id
HAVING invalid_project_count <> 0 OR invalid_subject_count <> 0 OR non_leaf_subject_count <> 0;

-- 5. 投标成本转入头行对账。头行差额必须为 0。
SELECT h.tenant_id, h.id AS transfer_id, h.transfer_code, h.status,
       h.total_amount AS header_amount, COALESCE(SUM(l.amount), 0) AS line_amount,
       h.total_amount - COALESCE(SUM(l.amount), 0) AS difference
FROM bid_cost_target_transfer h
LEFT JOIN bid_cost_target_transfer_line l
       ON l.transfer_id = h.id AND l.tenant_id = h.tenant_id
GROUP BY h.tenant_id, h.id, h.transfer_code, h.status, h.total_amount
HAVING difference <> 0;

-- 同一目标成本版本、同一来源事实的净转入只能为 0（已冲销）或原始事实金额；大于一份为异常。
SELECT h.tenant_id, h.target_id, l.source_cost_item_id,
       COUNT(*) AS fact_count, SUM(l.amount) AS net_transferred_amount,
       MAX(c.amount_without_tax) AS source_amount
FROM bid_cost_target_transfer_line l
JOIN bid_cost_target_transfer h ON h.id = l.transfer_id AND h.tenant_id = l.tenant_id
JOIN cost_item c ON c.id = l.source_cost_item_id AND c.tenant_id = l.tenant_id
GROUP BY h.tenant_id, h.target_id, l.source_cost_item_id
HAVING SUM(l.amount) NOT IN (0, MAX(c.amount_without_tax));

-- 冲销必须一对一引用原始事实，以下结果必须为空。
SELECT original.tenant_id, original.id AS original_id, COUNT(reversal.id) AS reversal_count
FROM bid_cost_target_transfer original
LEFT JOIN bid_cost_target_transfer reversal
       ON reversal.tenant_id = original.tenant_id AND reversal.reversal_of_id = original.id
WHERE original.reversal_of_id IS NULL
GROUP BY original.tenant_id, original.id
HAVING COUNT(reversal.id) > 1;

-- 6. 财务费用分摊头行对账。头行差额必须为 0。
SELECT b.tenant_id, b.id AS batch_id, b.batch_code, b.status,
       b.source_amount AS header_amount, COALESCE(SUM(l.allocated_amount), 0) AS line_amount,
       b.source_amount - COALESCE(SUM(l.allocated_amount), 0) AS difference
FROM finance_cost_allocation_batch b
LEFT JOIN finance_cost_allocation_line l
       ON l.batch_id = b.id AND l.tenant_id = b.tenant_id
GROUP BY b.tenant_id, b.id, b.batch_code, b.status, b.source_amount
HAVING difference <> 0;

-- 同一来源净分摊不得小于 0；正向累计不得超过来源金额。
SELECT tenant_id, source_type, source_id,
       SUM(source_amount) AS net_source_amount
FROM finance_cost_allocation_batch
GROUP BY tenant_id, source_type, source_id
HAVING SUM(source_amount) < 0;

SELECT original.tenant_id, original.id AS original_id, COUNT(reversal.id) AS reversal_count
FROM finance_cost_allocation_batch original
LEFT JOIN finance_cost_allocation_batch reversal
       ON reversal.tenant_id = original.tenant_id AND reversal.reversal_of_id = original.id
WHERE original.reversal_of_id IS NULL
GROUP BY original.tenant_id, original.id
HAVING COUNT(reversal.id) > 1;

-- 7. 项目双口径汇总：历史事实不改写，V2 转入/分摊按净额展示。
SELECT p.tenant_id, p.id AS project_id, p.project_name,
       COALESCE(actual.actual_cost, 0) AS historical_actual_cost,
       COALESCE(target.target_cost, 0) AS current_target_cost,
       COALESCE(bid.bid_transferred, 0) AS net_bid_cost_transferred,
       COALESCE(finance.finance_allocated, 0) AS net_finance_cost_allocated,
       COALESCE(actual.unclassified_count, 0) AS unclassified_fact_count
FROM pm_project p
LEFT JOIN (
    SELECT tenant_id, project_id, SUM(amount_without_tax) AS actual_cost,
           SUM(cost_subject_id IS NULL) AS unclassified_count
    FROM cost_item
    WHERE deleted_flag = 0 AND cost_status <> 'WRITE_OFF'
    GROUP BY tenant_id, project_id
) actual ON actual.tenant_id = p.tenant_id AND actual.project_id = p.id
LEFT JOIN (
    SELECT tenant_id, project_id, SUM(target_amount) AS target_cost
    FROM cost_target_item
    WHERE deleted_flag = 0
    GROUP BY tenant_id, project_id
) target ON target.tenant_id = p.tenant_id AND target.project_id = p.id
LEFT JOIN (
    SELECT h.tenant_id, h.project_id, SUM(l.amount) AS bid_transferred
    FROM bid_cost_target_transfer h
    JOIN bid_cost_target_transfer_line l ON l.transfer_id = h.id AND l.tenant_id = h.tenant_id
    GROUP BY h.tenant_id, h.project_id
) bid ON bid.tenant_id = p.tenant_id AND bid.project_id = p.id
LEFT JOIN (
    SELECT b.tenant_id, l.project_id, SUM(l.allocated_amount) AS finance_allocated
    FROM finance_cost_allocation_batch b
    JOIN finance_cost_allocation_line l ON l.batch_id = b.id AND l.tenant_id = b.tenant_id
    GROUP BY b.tenant_id, l.project_id
) finance ON finance.tenant_id = p.tenant_id AND finance.project_id = p.id
WHERE p.deleted_flag = 0
ORDER BY p.tenant_id, p.id;
