-- V25__backfill_cost_subject_id.sql
-- H2-compatible version
-- Note: Uses H2's UPDATE ... SET col = (SELECT ...) syntax instead of MySQL UPDATE INNER JOIN

-- 1. VAR_ORDER: var_order_item table already has cost_subject_id
UPDATE cost_item ci
SET ci.cost_subject_id = (
    SELECT voi.cost_subject_id
    FROM var_order_item voi
    WHERE ci.source_item_id = voi.id
      AND voi.cost_subject_id IS NOT NULL
    LIMIT 1
)
WHERE ci.source_type = 'VAR_ORDER'
  AND ci.cost_subject_id IS NULL
  AND ci.deleted_flag = 0
  AND EXISTS (
    SELECT 1 FROM var_order_item voi2
    WHERE ci.source_item_id = voi2.id
      AND voi2.cost_subject_id IS NOT NULL
  );

-- 2. MAT_RECEIPT: map to cost_subject.subject_type = '材料'
UPDATE cost_item ci
SET ci.cost_subject_id = (
    SELECT cs.id
    FROM cost_subject cs
    WHERE cs.tenant_id = ci.tenant_id
      AND cs.subject_type = '材料'
      AND cs.status = 'ENABLE'
      AND cs.deleted_flag = 0
    ORDER BY cs.level ASC
    LIMIT 1
)
WHERE ci.source_type = 'MAT_RECEIPT'
  AND ci.cost_subject_id IS NULL
  AND ci.deleted_flag = 0;

-- 3. SUB_MEASURE: map to cost_subject.subject_type = '分包'
UPDATE cost_item ci
SET ci.cost_subject_id = (
    SELECT cs.id
    FROM cost_subject cs
    WHERE cs.tenant_id = ci.tenant_id
      AND cs.subject_type = '分包'
      AND cs.status = 'ENABLE'
      AND cs.deleted_flag = 0
    ORDER BY cs.level ASC
    LIMIT 1
)
WHERE ci.source_type = 'SUB_MEASURE'
  AND ci.cost_subject_id IS NULL
  AND ci.deleted_flag = 0;

-- 4. CT_CONTRACT: map to cost_subject.subject_type = '合同'
UPDATE cost_item ci
SET ci.cost_subject_id = (
    SELECT cs.id
    FROM cost_subject cs
    WHERE cs.tenant_id = ci.tenant_id
      AND cs.subject_type = '合同'
      AND cs.status = 'ENABLE'
      AND cs.deleted_flag = 0
    ORDER BY cs.level ASC
    LIMIT 1
)
WHERE ci.source_type = 'CT_CONTRACT'
  AND ci.cost_subject_id IS NULL
  AND ci.deleted_flag = 0;
