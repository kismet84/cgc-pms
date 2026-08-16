package com.cgcpms.cost.service;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.mapper.CostItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/** Resolves authoritative leaf in append-only cost reclassification lineage. */
@Component
@RequiredArgsConstructor
public class CostFactLineageResolver {
    private final JdbcTemplate jdbc;
    private final CostItemMapper costItemMapper;

    public CostItem requireCurrentLeaf(Long tenantId, Long rootCostItemId) {
        Long leafId = currentLeafId(tenantId, rootCostItemId);
        for (int attempt = 0; attempt < 3; attempt++) {
            List<Long> locked = jdbc.queryForList("""
                    SELECT id FROM cost_item
                    WHERE tenant_id=? AND id=? AND deleted_flag=0 FOR UPDATE
                    """, Long.class, tenantId, leafId);
            if (locked.size() != 1) {
                throw new BusinessException("COST_FACT_LINEAGE_DRIFT", "成本事实重分类链已变化，请重试");
            }
            Long current = currentLeafId(tenantId, rootCostItemId);
            if (Objects.equals(current, leafId)) {
                Integer reserved = jdbc.queryForObject("""
                        SELECT COUNT(*) FROM cost_recalculation_fact_reservation r
                        JOIN cost_recalculation_batch b ON b.tenant_id=r.tenant_id AND b.id=r.batch_id
                        WHERE r.tenant_id=? AND r.original_cost_item_id=? AND b.status='SUBMITTED'
                        """, Integer.class, tenantId, leafId);
                if (reserved != null && reserved > 0) {
                    throw new BusinessException("COST_FACT_RECALCULATION_PENDING", "成本事实正在历史重算审批中，请完成或撤回后再操作");
                }
                CostItem item = costItemMapper.selectById(leafId);
                if (item == null || !tenantId.equals(item.getTenantId())) {
                    throw new BusinessException("COST_FACT_LINEAGE_DRIFT", "当前成本事实不存在或已变化");
                }
                lockActualCostSubject(tenantId, item.getCostSubjectId());
                return item;
            }
            leafId = current;
        }
        throw new BusinessException("COST_FACT_LINEAGE_DRIFT", "成本事实重分类链并发变化，请重试");
    }

    public Long rootId(Long tenantId, Long costItemId) {
        List<Long> roots = jdbc.queryForList("""
                WITH RECURSIVE ancestors(id,original_cost_item_id) AS (
                  SELECT id,original_cost_item_id FROM cost_item
                  WHERE tenant_id=? AND id=? AND deleted_flag=0
                  UNION ALL
                  SELECT parent.id,parent.original_cost_item_id
                  FROM cost_item parent JOIN ancestors child ON child.original_cost_item_id=parent.id
                  WHERE parent.tenant_id=? AND parent.deleted_flag=0
                )
                SELECT id FROM ancestors WHERE original_cost_item_id IS NULL
                """, Long.class, tenantId, costItemId, tenantId);
        if (roots.size() != 1) {
            throw new BusinessException("COST_FACT_LINEAGE_INVALID", "成本事实来源链不完整或存在冲突");
        }
        return roots.getFirst();
    }

    private Long currentLeafId(Long tenantId, Long rootCostItemId) {
        List<Long> leaves = jdbc.queryForList("""
                WITH RECURSIVE lineage(id,original_cost_item_id,source_type,adjustment_batch_id) AS (
                  SELECT id,original_cost_item_id,source_type,adjustment_batch_id
                  FROM cost_item WHERE tenant_id=? AND id=? AND deleted_flag=0
                  UNION ALL
                  SELECT child.id,child.original_cost_item_id,child.source_type,child.adjustment_batch_id
                  FROM cost_item child JOIN lineage parent ON child.original_cost_item_id=parent.id
                  WHERE child.tenant_id=? AND child.deleted_flag=0
                )
                SELECT candidate.id FROM lineage candidate
                LEFT JOIN cost_recalculation_batch own_batch
                  ON own_batch.tenant_id=? AND own_batch.id=candidate.adjustment_batch_id
                WHERE (candidate.id=? OR candidate.source_type='COST_RECALCULATION_POSITIVE')
                  AND (candidate.adjustment_batch_id IS NULL OR own_batch.status='POSTED')
                  AND NOT EXISTS (
                    SELECT 1 FROM cost_item successor
                    LEFT JOIN cost_recalculation_batch successor_batch
                      ON successor_batch.tenant_id=successor.tenant_id
                     AND successor_batch.id=successor.adjustment_batch_id
                    WHERE successor.tenant_id=? AND successor.original_cost_item_id=candidate.id
                      AND successor.deleted_flag=0
                      AND (successor.source_type='COST_RECALCULATION_REVERSAL'
                           OR (successor.source_type='COST_RECALCULATION_NEGATIVE'
                               AND successor_batch.status='POSTED')))
                """, Long.class, tenantId, rootCostItemId, tenantId, tenantId, rootCostItemId, tenantId);
        if (leaves.size() != 1) {
            throw new BusinessException("COST_FACT_LINEAGE_INVALID", "成本事实当前有效叶子不存在或不唯一");
        }
        return leaves.getFirst();
    }

    private void lockActualCostSubject(Long tenantId, Long subjectId) {
        List<Long> subjects = jdbc.queryForList("""
                SELECT s.id FROM cost_subject s
                WHERE s.tenant_id=? AND s.id=? AND s.deleted_flag=0
                  AND s.status='ENABLE' AND s.account_category='COST'
                  AND NOT EXISTS (SELECT 1 FROM cost_subject child
                    WHERE child.tenant_id=s.tenant_id AND child.parent_id=s.id AND child.deleted_flag=0)
                FOR UPDATE
                """, Long.class, tenantId, subjectId);
        if (subjects.size() != 1) {
            throw new BusinessException("COST_SUBJECT_NOT_LEAF", "成本归集必须使用启用的成本域末级科目");
        }
        Integer disabledRules = jdbc.queryForObject("""
                SELECT COUNT(*) FROM overhead_allocation_rule
                WHERE tenant_id=? AND cost_subject_id=? AND status='DISABLE' AND deleted_flag=0
                """, Integer.class, tenantId, subjectId);
        if (disabledRules != null && disabledRules > 0) {
            throw new BusinessException("OVERHEAD_RULE_DISABLED_FOR_COST",
                    "该间接费科目的分摊规则已停用，重新启用后方可生成后续成本事实");
        }
    }
}
