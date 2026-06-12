-- V25__backfill_cost_subject_id.sql
-- 建筑工程总包项目全过程管理系统 - 回填 cost_item.cost_subject_id
-- 数据库：MySQL 8.0+
--
-- 背景：4 个 CostGenerationStrategy 在生成 CostItem 时从未设置 costSubjectId，
-- 导致 CostSummaryService.refreshSummary() 按 costSubjectId 分组时所有成本塌缩到 null 分组。
-- 此迁移为历史数据补填 costSubjectId，同时不破坏 uk_cost_source_item 幂等约束。

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 1. VAR_ORDER（变更签证）：var_order_item 表已有 cost_subject_id
-- ============================================================
UPDATE cost_item ci
INNER JOIN var_order_item voi ON ci.source_item_id = voi.id
SET ci.cost_subject_id = voi.cost_subject_id
WHERE ci.source_type = 'VAR_ORDER'
  AND ci.cost_subject_id IS NULL
  AND ci.deleted_flag = 0
  AND voi.cost_subject_id IS NOT NULL;

-- ============================================================
-- 2. MAT_RECEIPT（材料验收）：映射到 cost_subject.subject_type = '材料'
-- ============================================================
UPDATE cost_item ci
SET ci.cost_subject_id = (
    SELECT id FROM (
        SELECT cs.id
        FROM cost_subject cs
        WHERE cs.tenant_id = ci.tenant_id
          AND cs.subject_type = '材料'
          AND cs.status = 'ENABLE'
          AND cs.deleted_flag = 0
        ORDER BY cs.level ASC
        LIMIT 1
    ) t
)
WHERE ci.source_type = 'MAT_RECEIPT'
  AND ci.cost_subject_id IS NULL
  AND ci.deleted_flag = 0;

-- ============================================================
-- 3. SUB_MEASURE（分包计量）：映射到 cost_subject.subject_type = '分包'
-- ============================================================
UPDATE cost_item ci
SET ci.cost_subject_id = (
    SELECT id FROM (
        SELECT cs.id
        FROM cost_subject cs
        WHERE cs.tenant_id = ci.tenant_id
          AND cs.subject_type = '分包'
          AND cs.status = 'ENABLE'
          AND cs.deleted_flag = 0
        ORDER BY cs.level ASC
        LIMIT 1
    ) t
)
WHERE ci.source_type = 'SUB_MEASURE'
  AND ci.cost_subject_id IS NULL
  AND ci.deleted_flag = 0;

-- ============================================================
-- 4. CT_CONTRACT（合同锁定成本）：映射到 cost_subject.subject_type = '合同'
-- ============================================================
UPDATE cost_item ci
SET ci.cost_subject_id = (
    SELECT id FROM (
        SELECT cs.id
        FROM cost_subject cs
        WHERE cs.tenant_id = ci.tenant_id
          AND cs.subject_type = '合同'
          AND cs.status = 'ENABLE'
          AND cs.deleted_flag = 0
        ORDER BY cs.level ASC
        LIMIT 1
    ) t
)
WHERE ci.source_type = 'CT_CONTRACT'
  AND ci.cost_subject_id IS NULL
  AND ci.deleted_flag = 0;

SET FOREIGN_KEY_CHECKS = 1;
