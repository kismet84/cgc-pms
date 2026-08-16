-- 已执行的旧规则必须人工版本化迁移；禁止自动改写历史规则，也禁止停用后留下无法恢复的成本科目。
CREATE TEMPORARY TABLE m96_overhead_shape_guard (
  id TINYINT PRIMARY KEY,
  executed_unsupported_count INT NOT NULL,
  CONSTRAINT ck_m96_no_executed_unsupported_overhead CHECK (executed_unsupported_count=0)
);
INSERT INTO m96_overhead_shape_guard(id,executed_unsupported_count)
SELECT 1,COUNT(*)
FROM overhead_allocation_rule rule_row
WHERE rule_row.deleted_flag=0
  AND (rule_row.allocation_cycle<>'MONTHLY'
       OR rule_row.allocation_basis NOT IN ('DIRECT_LABOR','CONTRACT_AMOUNT'))
  AND EXISTS (
    SELECT 1 FROM overhead_allocation_run run_row
    WHERE run_row.tenant_id=rule_row.tenant_id AND run_row.rule_id=rule_row.id
      AND run_row.deleted_flag=0
  );
DROP TEMPORARY TABLE m96_overhead_shape_guard;

ALTER TABLE cost_item
    ADD COLUMN classification_business_category VARCHAR(64) NOT NULL DEFAULT '*'
    AFTER classification_status;

UPDATE cost_item item
JOIN cost_classification_snapshot snapshot
  ON snapshot.tenant_id = item.tenant_id
 AND snapshot.id = item.classification_snapshot_id
SET item.classification_business_category = snapshot.business_category
WHERE item.deleted_flag = 0
  AND snapshot.business_category IS NOT NULL
  AND snapshot.business_category <> '';

UPDATE cost_item item
JOIN mat_receipt receipt ON receipt.tenant_id=item.tenant_id AND receipt.id=item.source_id
SET item.classification_business_category=receipt.receipt_mode
WHERE item.deleted_flag=0 AND item.source_type='MAT_RECEIPT';

UPDATE cost_item item
JOIN ct_contract contract_row ON contract_row.tenant_id=item.tenant_id AND contract_row.id=item.source_id
SET item.classification_business_category=contract_row.contract_type
WHERE item.deleted_flag=0 AND item.source_type='CT_CONTRACT';

UPDATE cost_item
SET classification_business_category='BID'
WHERE deleted_flag=0 AND source_type='BID_COST';

UPDATE cost_item item
JOIN overhead_allocation_run run_row ON run_row.tenant_id=item.tenant_id AND run_row.id=item.source_id
JOIN overhead_allocation_rule rule_row ON rule_row.tenant_id=run_row.tenant_id AND rule_row.id=run_row.rule_id
SET item.classification_business_category=rule_row.allocation_basis
WHERE item.deleted_flag=0 AND item.source_type='OVERHEAD_ALLOCATION';

UPDATE cost_item item
JOIN finance_cost_allocation_batch batch_row ON batch_row.tenant_id=item.tenant_id AND batch_row.id=item.source_id
SET item.classification_business_category=batch_row.allocation_basis
WHERE item.deleted_flag=0 AND item.source_type='FINANCE_COST_ALLOCATION';

-- 所有反向/调整事实继承权威父事实的分类上下文，不从派生 source_type 重新猜测。
UPDATE cost_item item
JOIN cost_item original ON original.tenant_id=item.tenant_id AND original.id=item.original_cost_item_id
SET item.classification_business_category=original.classification_business_category
WHERE item.deleted_flag=0 AND item.original_cost_item_id IS NOT NULL;

-- 收入、业主结算与产值确认不属于成本治理。修正 V301 对空科目历史行的默认 ACTUAL。
UPDATE cost_item
SET recognition_role = 'NON_COST'
WHERE deleted_flag = 0
  AND source_type IN ('CT_REVENUE','CONTRACT_REVENUE','OWNER_SETTLEMENT',
                      'PRODUCTION_MEASUREMENT','OWNER_MEASUREMENT_SUBMISSION');

-- 旧 USAGE/PER_OCCURRENCE 等规则没有权威执行依据，先停用，禁止继续静默等比分摊。
UPDATE overhead_allocation_rule
SET status = 'DISABLE', updated_at = CURRENT_TIMESTAMP
WHERE deleted_flag = 0
  AND status = 'ENABLE'
  AND (allocation_cycle <> 'MONTHLY'
       OR allocation_basis NOT IN ('DIRECT_LABOR','CONTRACT_AMOUNT'));
