package com.cgcpms.cost.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CostSubjectV2Service {

    private final JdbcTemplate jdbc;

    public record MappingItem(Long sourceSubjectId, String targetGroupCode, Long targetSubjectId,
                              String historicalDisplayName, String mappingReason) {}

    public record MappingVersionCommand(String versionCode, String versionName, LocalDate effectiveDate,
                                        String remark, List<MappingItem> items) {}

    public record RuleCommand(String ruleCode, Long mappingVersionId, String sourceType,
                              String businessCategory, Long projectId, Long costSubjectId,
                              Integer priority, LocalDate effectiveFrom, LocalDate effectiveTo,
                              String remark) {}

    public record ScopeCommand(Long projectId, Long costSubjectId, Boolean enabled,
                               LocalDate effectiveFrom, LocalDate effectiveTo, String remark) {}

    public record TransferCommand(Long bidCostId, Long projectId, Long targetId, Long mappingVersionId,
                                  Long approvalInstanceId, String idempotencyKey, String remark) {}

    public record AllocationLine(Long projectId, BigDecimal basisValue) {}

    public record FinanceAllocationCommand(String sourceType, Long sourceId, String allocationBasis,
                                           String accountingPeriod, Long costSubjectId,
                                           Long approvalInstanceId, String idempotencyKey,
                                           String remark, List<AllocationLine> lines) {}

    public List<Map<String, Object>> mappingVersions() {
        return jdbc.queryForList("""
                SELECT v.id,v.version_code,v.version_name,v.status,v.effective_date,v.approval_instance_id,
                       v.activated_by,v.activated_at,v.created_at,v.remark,COUNT(i.id) item_count
                FROM cost_subject_mapping_version v
                LEFT JOIN cost_subject_mapping_item i ON i.mapping_version_id=v.id AND i.tenant_id=v.tenant_id
                WHERE v.tenant_id=?
                GROUP BY v.id,v.version_code,v.version_name,v.status,v.effective_date,v.approval_instance_id,
                         v.activated_by,v.activated_at,v.created_at,v.remark
                ORDER BY v.created_at DESC
                """, tenantId());
    }

    public List<Map<String, Object>> mappingItems(Long versionId) {
        return jdbc.queryForList("""
                SELECT i.id,i.source_subject_id,s.subject_code source_subject_code,s.subject_name source_subject_name,
                       i.target_group_code,i.target_subject_id,t.subject_code target_subject_code,
                       t.subject_name target_subject_name,i.historical_display_name,i.mapping_reason
                FROM cost_subject_mapping_item i
                JOIN cost_subject s ON s.id=i.source_subject_id AND s.tenant_id=i.tenant_id
                LEFT JOIN cost_subject t ON t.id=i.target_subject_id AND t.tenant_id=i.tenant_id
                WHERE i.tenant_id=? AND i.mapping_version_id=? ORDER BY s.subject_code
                """, tenantId(), versionId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createMappingVersion(MappingVersionCommand command) {
        requireText(command.versionCode(), "映射版本编码不能为空");
        requireText(command.versionName(), "映射版本名称不能为空");
        if (command.items() == null || command.items().isEmpty()) {
            throw new BusinessException("COST_SUBJECT_MAPPING_EMPTY", "映射版本至少包含一条科目映射");
        }
        Long id = IdWorker.getId();
        try {
            jdbc.update("""
                    INSERT INTO cost_subject_mapping_version
                    (id,tenant_id,version_code,version_name,status,effective_date,created_by,remark)
                    VALUES (?,?,?,?, 'DRAFT',?,?,?)
                    """, id, tenantId(), command.versionCode().trim(), command.versionName().trim(),
                    command.effectiveDate(), userId(), command.remark());
            for (MappingItem item : command.items()) {
                requireSubject(item.sourceSubjectId(), false);
                if (item.targetSubjectId() != null) requireSubject(item.targetSubjectId(), true);
                requireText(item.targetGroupCode(), "V2归集组不能为空");
                requireText(item.historicalDisplayName(), "历史展示名称不能为空");
                jdbc.update("""
                        INSERT INTO cost_subject_mapping_item
                        (id,tenant_id,mapping_version_id,source_subject_id,target_group_code,target_subject_id,
                         historical_display_name,mapping_reason,created_by)
                        VALUES (?,?,?,?,?,?,?,?,?)
                        """, IdWorker.getId(), tenantId(), id, item.sourceSubjectId(),
                        item.targetGroupCode().trim(), item.targetSubjectId(), item.historicalDisplayName().trim(),
                        item.mappingReason(), userId());
            }
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("COST_SUBJECT_MAPPING_DUPLICATE", "版本编码或源科目映射重复", ex);
        }
        return id;
    }

    @Transactional(rollbackFor = Exception.class)
    public void activateMappingVersion(Long id, Long approvalInstanceId) {
        requireApprovedWorkflow(approvalInstanceId, "COST_SUBJECT_MAPPING", id);
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM cost_subject_mapping_item WHERE tenant_id=? AND mapping_version_id=?",
                Integer.class, tenantId(), id);
        if (count == null || count == 0) throw new BusinessException("COST_SUBJECT_MAPPING_EMPTY", "空映射版本不能启用");
        Integer unmapped = jdbc.queryForObject("""
                SELECT COUNT(*) FROM cost_subject_mapping_item i
                WHERE i.tenant_id=? AND i.mapping_version_id=? AND i.target_subject_id IS NOT NULL
                  AND EXISTS (SELECT 1 FROM cost_subject c WHERE c.tenant_id=i.tenant_id AND c.parent_id=i.target_subject_id AND c.deleted_flag=0)
                """, Integer.class, tenantId(), id);
        if (unmapped != null && unmapped > 0) throw new BusinessException("COST_SUBJECT_MAPPING_NON_LEAF", "映射目标必须为末级科目");
        jdbc.update("UPDATE cost_subject_mapping_version SET status='RETIRED',updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND status='ACTIVE' AND id<>?",
                userId(), tenantId(), id);
        int updated = jdbc.update("""
                UPDATE cost_subject_mapping_version SET status='ACTIVE',approval_instance_id=?,activated_by=?,
                    activated_at=CURRENT_TIMESTAMP,updated_by=?,updated_at=CURRENT_TIMESTAMP,version=version+1
                WHERE tenant_id=? AND id=? AND status='DRAFT'
                """, approvalInstanceId, userId(), userId(), tenantId(), id);
        if (updated != 1) throw new BusinessException("COST_SUBJECT_MAPPING_NOT_DRAFT", "仅草稿映射版本可以启用");
        jdbc.update("UPDATE cost_subject_assignment_rule SET status='RETIRED',updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND status='ACTIVE' AND mapping_version_id<>?",
                tenantId(), id);
        jdbc.update("UPDATE cost_subject_assignment_rule SET status='ACTIVE',updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND mapping_version_id=? AND status='DRAFT'",
                tenantId(), id);
    }

    public List<Map<String, Object>> rules() {
        return jdbc.queryForList("""
                SELECT r.*,s.subject_code,s.subject_name,v.version_code
                FROM cost_subject_assignment_rule r
                JOIN cost_subject s ON s.id=r.cost_subject_id AND s.tenant_id=r.tenant_id
                JOIN cost_subject_mapping_version v ON v.id=r.mapping_version_id AND v.tenant_id=r.tenant_id
                WHERE r.tenant_id=? ORDER BY r.status,r.priority,r.rule_code
                """, tenantId());
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createRule(RuleCommand command) {
        requireText(command.ruleCode(), "规则编码不能为空");
        requireText(command.sourceType(), "业务来源不能为空");
        requireSubject(command.costSubjectId(), true);
        requireMappingVersion(command.mappingVersionId(), "DRAFT");
        if (command.projectId() != null) requireProject(command.projectId());
        LocalDate from = command.effectiveFrom() == null ? LocalDate.now() : command.effectiveFrom();
        if (command.effectiveTo() != null && command.effectiveTo().isBefore(from)) {
            throw new BusinessException("COST_SUBJECT_RULE_DATE_INVALID", "规则失效日期不能早于生效日期");
        }
        Long id = IdWorker.getId();
        try {
            jdbc.update("""
                    INSERT INTO cost_subject_assignment_rule
                    (id,tenant_id,mapping_version_id,rule_code,source_type,business_category,project_id,cost_subject_id,
                     priority,status,effective_from,effective_to,created_by,remark)
                    VALUES (?,?,?,?,?,?,?,?,?,'DRAFT',?,?,?,?)
                    """, id, tenantId(), command.mappingVersionId(), command.ruleCode().trim(), command.sourceType().trim(),
                    textOrDefault(command.businessCategory(), "*"), command.projectId(), command.costSubjectId(),
                    command.priority() == null ? 100 : command.priority(), from, command.effectiveTo(), userId(), command.remark());
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("COST_SUBJECT_RULE_DUPLICATE", "归集规则编码已存在", ex);
        }
        return id;
    }

    public Long resolveRule(String sourceType, String businessCategory, Long projectId) {
        requireText(sourceType, "业务来源不能为空");
        List<Map<String, Object>> result = jdbc.queryForList("""
                SELECT r.cost_subject_id,
                       CASE WHEN r.project_id IS NOT NULL THEN 0 ELSE 1 END project_rank,
                       CASE WHEN r.business_category=? THEN 0 ELSE 1 END category_rank,
                       r.priority
                FROM cost_subject_assignment_rule r
                JOIN cost_subject_mapping_version v ON v.id=r.mapping_version_id AND v.tenant_id=r.tenant_id AND v.status='ACTIVE'
                JOIN cost_subject s ON s.id=r.cost_subject_id AND s.tenant_id=r.tenant_id AND s.status='ENABLE' AND s.deleted_flag=0
                WHERE r.tenant_id=? AND r.status='ACTIVE' AND r.source_type=?
                  AND r.business_category IN (?, '*') AND (r.project_id=? OR r.project_id IS NULL)
                  AND r.effective_from<=CURRENT_DATE AND (r.effective_to IS NULL OR r.effective_to>=CURRENT_DATE)
                  AND NOT EXISTS (SELECT 1 FROM cost_subject c WHERE c.tenant_id=s.tenant_id AND c.parent_id=s.id AND c.deleted_flag=0)
                  AND (r.project_id IS NULL OR EXISTS (
                      SELECT 1 FROM project_cost_subject_scope p WHERE p.tenant_id=r.tenant_id AND p.project_id=r.project_id
                        AND p.cost_subject_id=r.cost_subject_id AND p.enabled=1 AND p.effective_from<=CURRENT_DATE
                        AND (p.effective_to IS NULL OR p.effective_to>=CURRENT_DATE)))
                ORDER BY CASE WHEN r.project_id IS NOT NULL THEN 0 ELSE 1 END,
                         CASE WHEN r.business_category=? THEN 0 ELSE 1 END,r.priority,r.id
                LIMIT 2
                """, textOrDefault(businessCategory, "*"), tenantId(), sourceType.trim(),
                textOrDefault(businessCategory, "*"), projectId, textOrDefault(businessCategory, "*"));
        if (result.isEmpty()) throw new BusinessException("COST_SUBJECT_UNCLASSIFIED", "未命中启用的显式归集规则，单据保持待归类");
        if (result.size() > 1 && sameRuleRank(result.get(0), result.get(1))) {
            throw new BusinessException("COST_SUBJECT_RULE_AMBIGUOUS", "归集规则存在同等优先级冲突，请先消除歧义");
        }
        return longValue(result.get(0).get("cost_subject_id"));
    }

    public List<Map<String, Object>> scopes(Long projectId) {
        requireProject(projectId);
        return jdbc.queryForList("""
                SELECT p.*,s.subject_code,s.subject_name FROM project_cost_subject_scope p
                JOIN cost_subject s ON s.id=p.cost_subject_id AND s.tenant_id=p.tenant_id
                WHERE p.tenant_id=? AND p.project_id=? ORDER BY s.subject_code
                """, tenantId(), projectId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long upsertScope(ScopeCommand command) {
        requireProject(command.projectId());
        requireSubject(command.costSubjectId(), true);
        LocalDate from = command.effectiveFrom() == null ? LocalDate.now() : command.effectiveFrom();
        if (command.effectiveTo() != null && command.effectiveTo().isBefore(from)) {
            throw new BusinessException("COST_SUBJECT_SCOPE_DATE_INVALID", "适用范围失效日期不能早于生效日期");
        }
        List<Long> existing = jdbc.query("SELECT id FROM project_cost_subject_scope WHERE tenant_id=? AND project_id=? AND cost_subject_id=?",
                (rs, rowNum) -> rs.getLong(1), tenantId(), command.projectId(), command.costSubjectId());
        if (!existing.isEmpty()) {
            jdbc.update("""
                    UPDATE project_cost_subject_scope SET enabled=?,effective_from=?,effective_to=?,version=version+1,
                        updated_by=?,updated_at=CURRENT_TIMESTAMP,remark=? WHERE tenant_id=? AND id=?
                    """, Boolean.FALSE.equals(command.enabled()) ? 0 : 1, from, command.effectiveTo(), userId(), command.remark(), tenantId(), existing.get(0));
            return existing.get(0);
        }
        Long id = IdWorker.getId();
        jdbc.update("""
                INSERT INTO project_cost_subject_scope
                (id,tenant_id,project_id,cost_subject_id,enabled,effective_from,effective_to,created_by,remark)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, id, tenantId(), command.projectId(), command.costSubjectId(),
                Boolean.FALSE.equals(command.enabled()) ? 0 : 1, from, command.effectiveTo(), userId(), command.remark());
        return id;
    }

    public Map<String, Object> impact(Long subjectId) {
        requireSubject(subjectId, false);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("subjectId", subjectId);
        result.put("costItems", count("cost_item", "deleted_flag=0", subjectId));
        result.put("targetItems", count("cost_target_item", "deleted_flag=0", subjectId));
        result.put("forecastItems", count("cost_forecast_item", "1=1", subjectId));
        result.put("budgetLines", count("project_budget_line", "deleted_flag=0", subjectId));
        result.put("payments", count("pay_application", "deleted_flag=0", subjectId));
        result.put("expenses", count("expense_application", "deleted_flag=0", subjectId));
        result.put("settlementItems", count("stl_settlement_item", "deleted_flag=0", subjectId));
        result.put("accountingLines", count("accounting_entry_line", "1=1", subjectId));
        result.put("assignmentRules", count("cost_subject_assignment_rule", "1=1", subjectId));
        result.put("projectScopes", count("project_cost_subject_scope", "1=1", subjectId));
        return result;
    }

    public List<Map<String, Object>> transfers() {
        return jdbc.queryForList("""
                SELECT t.*,b.bid_project_name,ct.version_no,COUNT(l.id) line_count
                FROM bid_cost_target_transfer t JOIN bid_cost b ON b.id=t.bid_cost_id
                JOIN cost_target ct ON ct.id=t.target_id LEFT JOIN bid_cost_target_transfer_line l ON l.transfer_id=t.id
                WHERE t.tenant_id=? GROUP BY t.id,b.bid_project_name,ct.version_no ORDER BY t.posted_at DESC
                """, tenantId());
    }

    @Transactional(rollbackFor = Exception.class)
    public Long transferBidCost(TransferCommand command) {
        requireText(command.idempotencyKey(), "幂等键不能为空");
        requireApprovedWorkflow(command.approvalInstanceId(), "BID_COST_TARGET_TRANSFER", command.bidCostId());
        Map<String, Object> bid = one("SELECT id,project_id,bid_status FROM bid_cost WHERE tenant_id=? AND id=?", command.bidCostId());
        if (!"WON".equals(bid.get("bid_status")) || !Objects.equals(longValue(bid.get("project_id")), command.projectId())) {
            throw new BusinessException("BID_COST_NOT_WON", "仅已中标且绑定当前项目的投标成本可以转入");
        }
        requireProject(command.projectId());
        Map<String, Object> target = one("SELECT id,project_id,approval_status,is_active FROM cost_target WHERE tenant_id=? AND id=? AND deleted_flag=0 FOR UPDATE", command.targetId());
        if (!Objects.equals(longValue(target.get("project_id")), command.projectId())) throw new BusinessException("COST_TARGET_PROJECT_MISMATCH", "目标成本不属于中标项目");
        if (!List.of("DRAFT", "REJECTED").contains(String.valueOf(target.get("approval_status"))) || intValue(target.get("is_active")) == 1) {
            throw new BusinessException("COST_TARGET_NOT_EDITABLE", "投标成本仅可转入草稿或驳回且未生效的目标成本版本");
        }
        requireMappingVersion(command.mappingVersionId(), "ACTIVE");
        List<Map<String, Object>> sourceItems = jdbc.queryForList("""
                SELECT c.id,c.cost_subject_id,c.amount_without_tax,m.target_subject_id
                FROM cost_item c JOIN cost_subject_mapping_item m ON m.tenant_id=c.tenant_id
                  AND m.mapping_version_id=? AND m.source_subject_id=c.cost_subject_id
                WHERE c.tenant_id=? AND c.source_id=? AND c.source_type IN ('BID_COST','BID_COST_TRANSFERRED')
                  AND c.deleted_flag=0 AND c.cost_status<>'WRITE_OFF' AND m.target_subject_id IS NOT NULL
                """, command.mappingVersionId(), tenantId(), command.bidCostId());
        if (sourceItems.isEmpty()) throw new BusinessException("BID_COST_MAPPING_MISSING", "投标成本没有可转入的末级科目映射");
        for (Map<String, Object> row : sourceItems) {
            requireSubject(longValue(row.get("target_subject_id")), true);
            BigDecimal transferred = jdbc.queryForObject("""
                    SELECT COALESCE(SUM(l.amount),0) FROM bid_cost_target_transfer_line l
                    JOIN bid_cost_target_transfer h ON h.id=l.transfer_id AND h.tenant_id=l.tenant_id
                    WHERE l.tenant_id=? AND h.target_id=? AND l.source_cost_item_id=?
                    """, BigDecimal.class, tenantId(), command.targetId(), longValue(row.get("id")));
            if (transferred != null && transferred.signum() != 0) {
                throw new BusinessException("BID_COST_TRANSFER_DUPLICATE", "同一投标成本事实在当前目标成本版本中已转入");
            }
        }
        BigDecimal total = sourceItems.stream().map(row -> money(row.get("amount_without_tax"))).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() <= 0) throw new BusinessException("BID_COST_TRANSFER_AMOUNT_INVALID", "可转入投标成本必须大于零");
        Long id = IdWorker.getId();
        try {
            jdbc.update("""
                    INSERT INTO bid_cost_target_transfer
                    (id,tenant_id,bid_cost_id,project_id,target_id,mapping_version_id,transfer_code,idempotency_key,total_amount,
                     status,approval_instance_id,posted_by,remark)
                    VALUES (?,?,?,?,?,?,?,?,?,'POSTED',?,?,?)
                    """, id, tenantId(), command.bidCostId(), command.projectId(), command.targetId(), command.mappingVersionId(),
                    "BCT-" + id, command.idempotencyKey().trim(), total, command.approvalInstanceId(), userId(), command.remark());
            for (Map<String, Object> row : sourceItems) {
                Long targetSubjectId = longValue(row.get("target_subject_id"));
                BigDecimal amount = money(row.get("amount_without_tax"));
                jdbc.update("""
                        INSERT INTO bid_cost_target_transfer_line
                        (id,tenant_id,transfer_id,source_cost_item_id,source_subject_id,target_subject_id,amount)
                        VALUES (?,?,?,?,?,?,?)
                        """, IdWorker.getId(), tenantId(), id, longValue(row.get("id")), longValue(row.get("cost_subject_id")), targetSubjectId, amount);
                upsertTargetItem(command.targetId(), command.projectId(), targetSubjectId, amount);
            }
            jdbc.update("""
                    UPDATE cost_target SET total_target_amount=total_target_amount+?,total_bid_cost_amount=total_bid_cost_amount+?,
                        total_responsibility_amount=total_responsibility_amount+?,updated_by=?,updated_at=CURRENT_TIMESTAMP
                    WHERE tenant_id=? AND id=?
                    """, total, total, total, userId(), tenantId(), command.targetId());
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("BID_COST_TRANSFER_DUPLICATE", "同一幂等键或投标成本事实已转入", ex);
        }
        return id;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long reverseBidTransfer(Long originalId, Long approvalInstanceId, String idempotencyKey, String remark) {
        requireText(idempotencyKey, "幂等键不能为空");
        Map<String, Object> original = one("""
                SELECT id,bid_cost_id,project_id,target_id,mapping_version_id,total_amount,status
                FROM bid_cost_target_transfer WHERE tenant_id=? AND id=? AND reversal_of_id IS NULL
                """, originalId);
        if (!"POSTED".equals(original.get("status"))) throw new BusinessException("BID_COST_TRANSFER_NOT_REVERSIBLE", "仅原始已过账转入可冲销");
        requireApprovedWorkflow(approvalInstanceId, "BID_COST_TARGET_TRANSFER_REVERSAL", originalId);
        List<Map<String, Object>> lines = jdbc.queryForList("""
                SELECT source_cost_item_id,source_subject_id,target_subject_id,amount
                FROM bid_cost_target_transfer_line WHERE tenant_id=? AND transfer_id=? ORDER BY id
                """, tenantId(), originalId);
        Long reversalId = IdWorker.getId();
        BigDecimal originalTotal = money(original.get("total_amount"));
        try {
            jdbc.update("""
                    INSERT INTO bid_cost_target_transfer
                    (id,tenant_id,bid_cost_id,project_id,target_id,mapping_version_id,transfer_code,idempotency_key,total_amount,
                     status,approval_instance_id,reversal_of_id,posted_by,remark)
                    VALUES (?,?,?,?,?,?,?,?,?,'REVERSED',?,?,?,?)
                    """, reversalId, tenantId(), longValue(original.get("bid_cost_id")), longValue(original.get("project_id")),
                    longValue(original.get("target_id")), longValue(original.get("mapping_version_id")), "BCTR-" + reversalId,
                    idempotencyKey.trim(), originalTotal.negate(), approvalInstanceId, originalId, userId(), remark);
            for (Map<String, Object> line : lines) {
                BigDecimal amount = money(line.get("amount"));
                Long targetSubjectId = longValue(line.get("target_subject_id"));
                int updated = jdbc.update("""
                        UPDATE cost_target_item SET target_amount=target_amount-?,bid_cost_amount=bid_cost_amount-?,
                            responsibility_amount=responsibility_amount-?,updated_by=?,updated_at=CURRENT_TIMESTAMP
                        WHERE tenant_id=? AND target_id=? AND cost_subject_id=? AND deleted_flag=0
                          AND target_amount>=? AND bid_cost_amount>=? AND responsibility_amount>=?
                        """, amount, amount, amount, userId(), tenantId(), longValue(original.get("target_id")), targetSubjectId,
                        amount, amount, amount);
                if (updated != 1) throw new BusinessException("BID_COST_REVERSAL_TARGET_CONFLICT", "目标成本已变化，无法安全冲销投标转入");
                jdbc.update("""
                        INSERT INTO bid_cost_target_transfer_line
                        (id,tenant_id,transfer_id,source_cost_item_id,source_subject_id,target_subject_id,amount)
                        VALUES (?,?,?,?,?,?,?)
                        """, IdWorker.getId(), tenantId(), reversalId, longValue(line.get("source_cost_item_id")),
                        longValue(line.get("source_subject_id")), targetSubjectId, amount.negate());
            }
            int headerUpdated = jdbc.update("""
                    UPDATE cost_target SET total_target_amount=total_target_amount-?,total_bid_cost_amount=total_bid_cost_amount-?,
                        total_responsibility_amount=total_responsibility_amount-?,updated_by=?,updated_at=CURRENT_TIMESTAMP
                    WHERE tenant_id=? AND id=? AND total_target_amount>=? AND total_bid_cost_amount>=? AND total_responsibility_amount>=?
                    """, originalTotal, originalTotal, originalTotal, userId(), tenantId(), longValue(original.get("target_id")),
                    originalTotal, originalTotal, originalTotal);
            if (headerUpdated != 1) throw new BusinessException("BID_COST_REVERSAL_TARGET_CONFLICT", "目标成本总额已变化，无法安全冲销");
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("BID_COST_TRANSFER_ALREADY_REVERSED", "该转入已冲销或幂等键重复", ex);
        }
        return reversalId;
    }

    public List<Map<String, Object>> financeAllocations() {
        return jdbc.queryForList("""
                SELECT b.*,s.subject_code,s.subject_name,COUNT(l.id) line_count
                FROM finance_cost_allocation_batch b JOIN cost_subject s ON s.id=b.cost_subject_id
                LEFT JOIN finance_cost_allocation_line l ON l.batch_id=b.id
                WHERE b.tenant_id=? GROUP BY b.id,s.subject_code,s.subject_name ORDER BY b.posted_at DESC
                """, tenantId());
    }

    @Transactional(rollbackFor = Exception.class)
    public Long allocateFinanceCost(FinanceAllocationCommand command) {
        requireText(command.idempotencyKey(), "幂等键不能为空");
        requireText(command.accountingPeriod(), "会计期间不能为空");
        if (!command.accountingPeriod().matches("\\d{4}-(0[1-9]|1[0-2])")) {
            throw new BusinessException("FINANCE_COST_PERIOD_INVALID", "会计期间必须为YYYY-MM");
        }
        if (!List.of("DIRECT_PROJECT", "BENEFIT_AMOUNT", "OCCUPIED_DAYS", "CONTRACT_AMOUNT_EXCEPTION")
                .contains(command.allocationBasis())) {
            throw new BusinessException("FINANCE_COST_BASIS_INVALID", "不支持的财务费用分摊依据");
        }
        requireApprovedWorkflow(command.approvalInstanceId(), "FINANCE_COST_ALLOCATION", command.sourceId());
        requireSubject(command.costSubjectId(), true);
        if (command.lines() == null || command.lines().isEmpty()) throw new BusinessException("FINANCE_COST_LINES_EMPTY", "财务费用分摊至少包含一个项目");
        if (command.lines().stream().map(AllocationLine::projectId).distinct().count() != command.lines().size()) {
            throw new BusinessException("FINANCE_COST_PROJECT_DUPLICATE", "同一分摊批次不能重复选择项目");
        }
        if ("DIRECT_PROJECT".equals(command.allocationBasis()) && command.lines().size() != 1) {
            throw new BusinessException("FINANCE_COST_DIRECT_PROJECT_INVALID", "直接归属只能选择一个项目");
        }
        if ("CONTRACT_AMOUNT_EXCEPTION".equals(command.allocationBasis())
                && (command.remark() == null || command.remark().isBlank())) {
            throw new BusinessException("FINANCE_COST_EXCEPTION_REASON_REQUIRED", "合同额例外分摊必须说明原因");
        }
        BigDecimal sourceAmount = sourceAmount(command.sourceType(), command.sourceId());
        BigDecimal allocatedBefore = jdbc.queryForObject("""
                SELECT COALESCE(SUM(source_amount),0)
                FROM finance_cost_allocation_batch WHERE tenant_id=? AND source_type=? AND source_id=?
                """, BigDecimal.class, tenantId(), command.sourceType(), command.sourceId());
        BigDecimal remaining = sourceAmount.subtract(allocatedBefore == null ? BigDecimal.ZERO : allocatedBefore);
        if (remaining.signum() <= 0) throw new BusinessException("FINANCE_COST_ALREADY_ALLOCATED", "来源财务费用已全部分摊");
        BigDecimal basisTotal = command.lines().stream().map(AllocationLine::basisValue).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (basisTotal.signum() <= 0) throw new BusinessException("FINANCE_COST_BASIS_INVALID", "分摊依据合计必须大于零");
        List<BigDecimal> amounts = calculateAllocation(remaining, command.lines(), basisTotal);
        Long id = IdWorker.getId();
        try {
            jdbc.update("""
                    INSERT INTO finance_cost_allocation_batch
                    (id,tenant_id,batch_code,source_type,source_id,source_amount,allocation_basis,accounting_period,
                     cost_subject_id,idempotency_key,status,approval_instance_id,posted_by,remark)
                    VALUES (?,?,?,?,?,?,?,?,?,?,'POSTED',?,?,?)
                    """, id, tenantId(), "FCA-" + id, command.sourceType(), command.sourceId(), remaining,
                    command.allocationBasis(), command.accountingPeriod(), command.costSubjectId(), command.idempotencyKey().trim(),
                    command.approvalInstanceId(), userId(), command.remark());
            for (int index = 0; index < command.lines().size(); index++) {
                AllocationLine line = command.lines().get(index);
                requireProject(line.projectId());
                requireScope(line.projectId(), command.costSubjectId());
                Long costItemId = IdWorker.getId();
                BigDecimal amount = amounts.get(index);
                jdbc.update("""
                        INSERT INTO cost_item
                        (id,tenant_id,project_id,cost_subject_id,cost_type,amount,tax_amount,amount_without_tax,source_type,
                         source_id,source_item_id,cost_date,cost_status,generated_flag,created_by,created_at,updated_at,deleted_flag,remark)
                        VALUES (?,?,?,?,?,?,?,?, 'FINANCE_COST_ALLOCATION',?,?,CURRENT_DATE,'CONFIRMED',1,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,?)
                        """, costItemId, tenantId(), line.projectId(), command.costSubjectId(), "FINANCE",
                        amount, BigDecimal.ZERO, amount, id, index + 1L, userId(), command.remark());
                jdbc.update("""
                        INSERT INTO finance_cost_allocation_line
                        (id,tenant_id,batch_id,project_id,basis_value,allocated_amount,cost_item_id)
                        VALUES (?,?,?,?,?,?,?)
                        """, IdWorker.getId(), tenantId(), id, line.projectId(), line.basisValue(), amount, costItemId);
            }
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("FINANCE_COST_ALLOCATION_DUPLICATE", "分摊幂等键或项目明细重复", ex);
        }
        return id;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long reverseFinanceAllocation(Long originalId, Long approvalInstanceId, String idempotencyKey, String remark) {
        requireText(idempotencyKey, "幂等键不能为空");
        Map<String, Object> original = one("""
                SELECT id,source_type,source_id,source_amount,allocation_basis,accounting_period,cost_subject_id,status
                FROM finance_cost_allocation_batch WHERE tenant_id=? AND id=? AND reversal_of_id IS NULL
                """, originalId);
        if (!"POSTED".equals(original.get("status"))) throw new BusinessException("FINANCE_COST_NOT_REVERSIBLE", "仅原始已过账分摊可冲销");
        requireApprovedWorkflow(approvalInstanceId, "FINANCE_COST_ALLOCATION_REVERSAL", originalId);
        List<Map<String, Object>> lines = jdbc.queryForList("""
                SELECT project_id,basis_value,allocated_amount FROM finance_cost_allocation_line
                WHERE tenant_id=? AND batch_id=? ORDER BY id
                """, tenantId(), originalId);
        Long reversalId = IdWorker.getId();
        BigDecimal total = money(original.get("source_amount")).negate();
        try {
            jdbc.update("""
                    INSERT INTO finance_cost_allocation_batch
                    (id,tenant_id,batch_code,source_type,source_id,source_amount,allocation_basis,accounting_period,
                     cost_subject_id,idempotency_key,status,approval_instance_id,reversal_of_id,posted_by,remark)
                    VALUES (?,?,?,?,?,?,?,?,?,?,'REVERSED',?,?,?,?)
                    """, reversalId, tenantId(), "FCAR-" + reversalId, original.get("source_type"), longValue(original.get("source_id")),
                    total, original.get("allocation_basis"), original.get("accounting_period"), longValue(original.get("cost_subject_id")),
                    idempotencyKey.trim(), approvalInstanceId, originalId, userId(), remark);
            int index = 0;
            for (Map<String, Object> line : lines) {
                BigDecimal amount = money(line.get("allocated_amount")).negate();
                Long costItemId = IdWorker.getId();
                jdbc.update("""
                        INSERT INTO cost_item
                        (id,tenant_id,project_id,cost_subject_id,cost_type,amount,tax_amount,amount_without_tax,source_type,
                         source_id,source_item_id,cost_date,cost_status,generated_flag,created_by,created_at,updated_at,deleted_flag,remark)
                        VALUES (?,?,?,?,?,?,?,?, 'FINANCE_COST_ALLOCATION_REVERSAL',?,?,CURRENT_DATE,'CONFIRMED',1,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,?)
                        """, costItemId, tenantId(), longValue(line.get("project_id")), longValue(original.get("cost_subject_id")),
                        "FINANCE", amount, BigDecimal.ZERO, amount, reversalId, ++index, userId(), remark);
                jdbc.update("""
                        INSERT INTO finance_cost_allocation_line
                        (id,tenant_id,batch_id,project_id,basis_value,allocated_amount,cost_item_id)
                        VALUES (?,?,?,?,?,?,?)
                        """, IdWorker.getId(), tenantId(), reversalId, longValue(line.get("project_id")),
                        money(line.get("basis_value")), amount, costItemId);
            }
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("FINANCE_COST_ALREADY_REVERSED", "该分摊已冲销或幂等键重复", ex);
        }
        return reversalId;
    }

    public Map<String, Object> reconciliation(Long projectId) {
        requireProject(projectId);
        return jdbc.queryForMap("""
                SELECT ? project_id,
                  COALESCE((SELECT SUM(amount_without_tax) FROM cost_item WHERE tenant_id=? AND project_id=? AND deleted_flag=0 AND cost_status<>'WRITE_OFF'),0) actual_cost,
                  COALESCE((SELECT SUM(target_amount) FROM cost_target_item WHERE tenant_id=? AND project_id=? AND deleted_flag=0),0) target_cost,
                  COALESCE((SELECT SUM(l.amount) FROM bid_cost_target_transfer_line l JOIN bid_cost_target_transfer h ON h.id=l.transfer_id WHERE h.tenant_id=? AND h.project_id=?),0) bid_transferred,
                  COALESCE((SELECT SUM(l.allocated_amount) FROM finance_cost_allocation_line l JOIN finance_cost_allocation_batch h ON h.id=l.batch_id WHERE h.tenant_id=? AND l.project_id=?),0) finance_allocated,
                  COALESCE((SELECT COUNT(*) FROM cost_item WHERE tenant_id=? AND project_id=? AND deleted_flag=0 AND cost_subject_id IS NULL),0) unclassified_count,
                  COALESCE((SELECT COUNT(*) FROM cost_subject_assignment_rule r WHERE r.tenant_id=? AND r.status='ACTIVE' AND EXISTS (SELECT 1 FROM cost_subject s WHERE s.tenant_id=r.tenant_id AND s.parent_id=r.cost_subject_id AND s.deleted_flag=0)),0) active_non_leaf_rule_count
                """, projectId, tenantId(), projectId, tenantId(), projectId, tenantId(), projectId,
                tenantId(), projectId, tenantId(), projectId, tenantId());
    }

    private void upsertTargetItem(Long targetId, Long projectId, Long subjectId, BigDecimal amount) {
        int updated = jdbc.update("""
                UPDATE cost_target_item SET target_amount=target_amount+?,bid_cost_amount=bid_cost_amount+?,
                    responsibility_amount=responsibility_amount+?,updated_by=?,updated_at=CURRENT_TIMESTAMP
                WHERE tenant_id=? AND target_id=? AND cost_subject_id=? AND deleted_flag=0
                """, amount, amount, amount, userId(), tenantId(), targetId, subjectId);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO cost_target_item
                    (id,tenant_id,target_id,project_id,cost_subject_id,target_amount,bid_cost_amount,responsibility_amount,
                     sort_order,created_by,created_at,updated_at,deleted_flag,remark)
                    VALUES (?,?,?,?,?,?,?,?,999,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'投标成本V2转入')
                    """, IdWorker.getId(), tenantId(), targetId, projectId, subjectId, amount, amount, amount, userId());
        }
    }

    private List<BigDecimal> calculateAllocation(BigDecimal total, List<AllocationLine> lines, BigDecimal basisTotal) {
        List<BigDecimal> result = new ArrayList<>();
        BigDecimal assigned = BigDecimal.ZERO;
        for (int i = 0; i < lines.size(); i++) {
            BigDecimal amount = i == lines.size() - 1
                    ? total.subtract(assigned)
                    : total.multiply(lines.get(i).basisValue()).divide(basisTotal, 2, RoundingMode.HALF_UP);
            if (amount.signum() <= 0) throw new BusinessException("FINANCE_COST_LINE_AMOUNT_INVALID", "每个项目分摊金额必须大于零");
            result.add(amount);
            assigned = assigned.add(amount);
        }
        return result;
    }

    private BigDecimal sourceAmount(String sourceType, Long sourceId) {
        if ("ACCOUNTING_ENTRY_LINE".equals(sourceType)) {
            return money(one("""
                    SELECT l.amount FROM accounting_entry_line l JOIN accounting_entry e ON e.id=l.entry_id AND e.tenant_id=l.tenant_id
                    WHERE l.tenant_id=? AND l.id=? AND e.deleted_flag=0 AND e.entry_status='POSTED' AND l.direction='DEBIT'
                    FOR UPDATE
                    """, sourceId).get("amount"));
        }
        if ("EXPENSE_APPLICATION".equals(sourceType)) {
            return money(one("SELECT amount FROM expense_application WHERE tenant_id=? AND id=? AND deleted_flag=0 AND approval_status='APPROVED' FOR UPDATE", sourceId).get("amount"));
        }
        throw new BusinessException("FINANCE_COST_SOURCE_INVALID", "财务费用来源仅支持已过账借方凭证明细或已审批费用申请");
    }

    private void requireApprovedWorkflow(Long approvalInstanceId, String businessType, Long businessId) {
        if (approvalInstanceId == null) throw new BusinessException("APPROVAL_REQUIRED", "必须绑定审批实例");
        List<Map<String, Object>> instances = jdbc.queryForList("""
                SELECT business_type,business_id,instance_status FROM wf_instance
                WHERE tenant_id=? AND id=? AND deleted_flag=0
                """, tenantId(), approvalInstanceId);
        if (instances.size() != 1 || !"APPROVED".equals(instances.get(0).get("instance_status"))) {
            throw new BusinessException("APPROVAL_NOT_APPROVED", "审批实例不存在或未通过");
        }
        if (businessType != null && !businessType.equals(instances.get(0).get("business_type"))) {
            throw new BusinessException("APPROVAL_BUSINESS_MISMATCH", "审批实例业务类型不匹配");
        }
        if (businessId != null && !Objects.equals(longValue(instances.get(0).get("business_id")), businessId)) {
            throw new BusinessException("APPROVAL_BUSINESS_MISMATCH", "审批实例业务单据不匹配");
        }
    }

    private void requireMappingVersion(Long id, String status) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM cost_subject_mapping_version WHERE tenant_id=? AND id=? AND status=?",
                Integer.class, tenantId(), id, status);
        if (count == null || count != 1) throw new BusinessException("COST_SUBJECT_MAPPING_VERSION_INVALID", "成本科目映射版本不存在或状态不符");
    }

    private void requireSubject(Long id, boolean leaf) {
        if (id == null) throw new BusinessException("COST_SUBJECT_REQUIRED", "成本科目不能为空");
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM cost_subject s WHERE s.tenant_id=? AND s.id=? AND s.deleted_flag=0
                  AND (?=0 OR (s.status='ENABLE' AND s.account_category='COST' AND NOT EXISTS (
                    SELECT 1 FROM cost_subject c WHERE c.tenant_id=s.tenant_id AND c.parent_id=s.id AND c.deleted_flag=0)))
                """, Integer.class, tenantId(), id, leaf ? 1 : 0);
        if (count == null || count != 1) throw new BusinessException("COST_SUBJECT_NOT_LEAF", leaf ? "成本归集必须使用启用的成本域末级科目" : "成本科目不存在");
    }

    private void requireScope(Long projectId, Long subjectId) {
        Integer scoped = jdbc.queryForObject("SELECT COUNT(*) FROM project_cost_subject_scope WHERE tenant_id=? AND project_id=?",
                Integer.class, tenantId(), projectId);
        if (scoped != null && scoped > 0) {
            Integer allowed = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM project_cost_subject_scope WHERE tenant_id=? AND project_id=? AND cost_subject_id=?
                      AND enabled=1 AND effective_from<=CURRENT_DATE AND (effective_to IS NULL OR effective_to>=CURRENT_DATE)
                    """, Integer.class, tenantId(), projectId, subjectId);
            if (allowed == null || allowed != 1) throw new BusinessException("COST_SUBJECT_NOT_IN_PROJECT_SCOPE", "成本科目不在项目适用范围内");
        }
    }

    private void requireProject(Long projectId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM pm_project WHERE tenant_id=? AND id=? AND deleted_flag=0",
                Integer.class, tenantId(), projectId);
        if (count == null || count != 1) throw new BusinessException("PROJECT_NOT_FOUND", "项目不存在");
    }

    private Map<String, Object> one(String sql, Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, tenantId(), id);
        if (rows.size() != 1) throw new BusinessException("BUSINESS_SOURCE_NOT_FOUND", "业务来源不存在或不可用");
        return rows.get(0);
    }

    private long count(String table, String condition, Long subjectId) {
        Long value = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE tenant_id=? AND cost_subject_id=? AND " + condition,
                Long.class, tenantId(), subjectId);
        return value == null ? 0 : value;
    }

    private Long tenantId() {
        Long value = UserContext.getCurrentTenantId();
        if (value == null) throw new BusinessException("TENANT_CONTEXT_REQUIRED", "租户上下文缺失");
        return value;
    }

    private Long userId() {
        Long value = UserContext.getCurrentUserId();
        if (value == null) throw new BusinessException("USER_CONTEXT_REQUIRED", "用户上下文缺失");
        return value;
    }

    private static String textOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new BusinessException("VALIDATION_ERROR", message);
    }

    private static BigDecimal money(Object value) {
        if (value == null) return BigDecimal.ZERO;
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(String.valueOf(value));
    }

    private static Long longValue(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static boolean sameRuleRank(Map<String, Object> first, Map<String, Object> second) {
        return intValue(first.get("project_rank")) == intValue(second.get("project_rank"))
                && intValue(first.get("category_rank")) == intValue(second.get("category_rank"))
                && intValue(first.get("priority")) == intValue(second.get("priority"));
    }

    private static int intValue(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }
}
