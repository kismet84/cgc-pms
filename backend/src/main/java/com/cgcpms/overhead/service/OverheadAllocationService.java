package com.cgcpms.overhead.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.accounting.service.AccountingPeriodGuard;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.cost.service.CostFactLineageResolver;
import com.cgcpms.cost.strategy.CostSubjectResolver;
import com.cgcpms.cost.service.CostSummaryService;
import com.cgcpms.system.role.SystemRoleContract;
import com.cgcpms.overhead.entity.OverheadAllocationRule;
import com.cgcpms.overhead.entity.OverheadAllocationRun;
import com.cgcpms.overhead.mapper.OverheadAllocationRuleMapper;
import com.cgcpms.overhead.mapper.OverheadAllocationRunMapper;
import com.cgcpms.overhead.vo.OverheadAllocationExecutionResult;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** 间接费用分摊执行引擎。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OverheadAllocationService {

    private static final String SOURCE_TYPE = "OVERHEAD_ALLOCATION";

    private final OverheadAllocationRuleMapper ruleMapper;
    private final OverheadAllocationRunMapper runMapper;
    private final CostItemMapper costItemMapper;
    private final CostFactLineageResolver costFactLineageResolver;
    private final JdbcTemplate jdbc;
    private final CostSubjectResolver costSubjectResolver;
    private final PmProjectMapper projectMapper;
    private final CostSummaryService costSummaryService;
    private final AccountingPeriodGuard accountingPeriodGuard;
    private final PlatformTransactionManager transactionManager;

    private final AtomicBoolean scheduledMonthlyAllocationRunning = new AtomicBoolean(false);

    public IPage<OverheadAllocationRule> getPage(long pageNo, long pageSize) {
        Long tenantId = UserContext.getCurrentTenantId();
        return ruleMapper.selectPage(new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<OverheadAllocationRule>()
                        .eq(OverheadAllocationRule::getTenantId, tenantId)
                        .orderByAsc(OverheadAllocationRule::getCostSubjectId));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(OverheadAllocationRule rule) {
        rule.setTenantId(UserContext.getCurrentTenantId());
        rule.setStatus("ENABLE");
        ruleMapper.insert(rule);
        return rule.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createValidated(Long costSubjectId, String allocationBasis, String allocationCycle) {
        Long tenantId = UserContext.getCurrentTenantId();
        requireCompanyFinanceOperator();
        requireSupportedRuleShape(allocationBasis, allocationCycle);
        requireValidOverheadSubject(costSubjectId, tenantId);
        OverheadAllocationRule rule = new OverheadAllocationRule();
        rule.setCostSubjectId(costSubjectId);
        rule.setAllocationBasis(allocationBasis);
        rule.setAllocationCycle(allocationCycle);
        return create(rule);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateValidated(Long id, Long costSubjectId, String allocationBasis, String allocationCycle) {
        Long tenantId = UserContext.getCurrentTenantId();
        requireCompanyFinanceOperator();
        OverheadAllocationRule existing = ruleForUpdate(tenantId, id);
        requireRuleNotExecuted(tenantId, id);
        requireSupportedRuleShape(allocationBasis, allocationCycle);
        requireValidOverheadSubject(costSubjectId, tenantId);
        OverheadAllocationRule update = new OverheadAllocationRule();
        update.setId(existing.getId());
        update.setTenantId(existing.getTenantId());
        update.setCostSubjectId(costSubjectId);
        update.setAllocationBasis(allocationBasis);
        update.setAllocationCycle(allocationCycle);
        update.setStatus(existing.getStatus());
        ruleMapper.updateById(update);
    }

    private CostSubject requireValidOverheadSubject(Long costSubjectId, Long tenantId) {
        List<CostSubject> subjects = jdbc.query("""
                SELECT id,tenant_id,subject_type,account_category,status
                FROM cost_subject
                WHERE tenant_id=? AND id=? AND deleted_flag=0
                FOR UPDATE
                """, (rs, rowNum) -> {
                    CostSubject value = new CostSubject();
                    value.setId(rs.getLong("id"));
                    value.setTenantId(rs.getLong("tenant_id"));
                    value.setSubjectType(rs.getString("subject_type"));
                    value.setAccountCategory(rs.getString("account_category"));
                    value.setStatus(rs.getString("status"));
                    return value;
                }, tenantId, costSubjectId);
        CostSubject subject = subjects.size() == 1 ? subjects.getFirst() : null;
        Integer children = subject == null ? null : jdbc.queryForObject("""
                SELECT COUNT(*) FROM cost_subject
                WHERE tenant_id=? AND parent_id=? AND deleted_flag=0
                """, Integer.class, tenantId, costSubjectId);
        if (subject == null
                || !"ENABLE".equals(subject.getStatus())
                || !"OVERHEAD".equals(subject.getSubjectType())
                || !"COST".equals(subject.getAccountCategory())
                || children == null || children != 0) {
            throw new BusinessException("OVERHEAD_SUBJECT_INVALID", "间接费科目不存在或不可用");
        }
        return subject;
    }

    private void requireSupportedRuleShape(String allocationBasis, String allocationCycle) {
        if (!List.of("DIRECT_LABOR", "CONTRACT_AMOUNT").contains(allocationBasis)
                || !"MONTHLY".equals(allocationCycle)) {
            throw new BusinessException("OVERHEAD_RULE_UNSUPPORTED",
                    "当前仅支持按月、按直接人工或合同金额分摊；使用量和按次规则尚无权威来源");
        }
    }

    private OverheadAllocationRule ruleForUpdate(Long tenantId, Long id) {
        List<Long> locked = jdbc.query("""
                SELECT id FROM overhead_allocation_rule
                WHERE tenant_id=? AND id=? FOR UPDATE
                """, (rs, rowNum) -> rs.getLong(1), tenantId, id);
        if (locked.size() != 1) {
            throw new BusinessException("RULE_NOT_FOUND", "分摊规则不存在");
        }
        OverheadAllocationRule existing = ruleMapper.selectById(id);
        if (existing == null || !tenantId.equals(existing.getTenantId())) {
            throw new BusinessException("RULE_NOT_FOUND", "分摊规则不存在");
        }
        return existing;
    }

    private void requireRuleNotExecuted(Long tenantId, Long id) {
        long runCount = runMapper.selectCount(new LambdaQueryWrapper<OverheadAllocationRun>()
                .eq(OverheadAllocationRun::getTenantId, tenantId)
                .eq(OverheadAllocationRun::getRuleId, id));
        if (runCount > 0) {
            throw new BusinessException("RULE_ALREADY_EXECUTED", "规则已有执行事实，不允许修改或删除");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(OverheadAllocationRule rule) {
        Long tenantId = UserContext.getCurrentTenantId();
        OverheadAllocationRule existing = ruleForUpdate(tenantId, rule.getId());
        requireRuleNotExecuted(tenantId, rule.getId());
        rule.setTenantId(existing.getTenantId());
        ruleMapper.updateById(rule);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long tenantId = UserContext.getCurrentTenantId();
        requireCompanyFinanceOperator();
        ruleForUpdate(tenantId, id);
        requireRuleNotExecuted(tenantId, id);
        ruleMapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void setStatus(Long id, String status) {
        if (!"ENABLE".equals(status) && !"DISABLE".equals(status)) {
            throw new BusinessException("OVERHEAD_RULE_STATUS_INVALID", "分摊规则状态只允许启用或停用");
        }
        Long tenantId = UserContext.getCurrentTenantId();
        requireCompanyFinanceOperator();
        OverheadAllocationRule existing = ruleForUpdate(tenantId, id);
        if (status.equals(existing.getStatus())) {
            return;
        }
        if ("DISABLE".equals(status)) {
            List<Long> lockedSubjects = jdbc.queryForList("""
                    SELECT id FROM cost_subject
                    WHERE tenant_id=? AND id=? AND deleted_flag=0 FOR UPDATE
                    """, Long.class, tenantId, existing.getCostSubjectId());
            if (lockedSubjects.size() != 1) {
                throw new BusinessException("COST_SUBJECT_NOT_FOUND", "间接费科目不存在或已失效");
            }
            Integer pendingFacts = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM (
                      SELECT YEAR(ci.cost_date) cost_year,MONTH(ci.cost_date) cost_month
                      FROM cost_item ci
                      WHERE ci.tenant_id=? AND ci.cost_subject_id=? AND ci.deleted_flag=0
                        AND ci.cost_status IN ('CONFIRMED','POSTED')
                        AND ci.classification_status<>'UNCLASSIFIED' AND ci.recognition_role='ACTUAL'
                        AND ci.source_type NOT IN ('OVERHEAD_ALLOCATION','OVERHEAD_ALLOCATION_CLEARING',
                          'COST_RECALCULATION_NEGATIVE','COST_RECALCULATION_POSITIVE','COST_RECALCULATION_REVERSAL')
                        AND NOT EXISTS (
                          SELECT 1 FROM cost_item clearing
                          WHERE clearing.tenant_id=ci.tenant_id AND clearing.original_cost_item_id=ci.id
                            AND clearing.source_type='OVERHEAD_ALLOCATION_CLEARING'
                            AND clearing.deleted_flag=0 AND clearing.cost_status<>'WRITE_OFF')
                      GROUP BY ci.project_id,YEAR(ci.cost_date),MONTH(ci.cost_date)
                      HAVING SUM(ci.amount)<>0 OR SUM(ci.tax_amount)<>0 OR SUM(ci.amount_without_tax)<>0
                    ) pending_period
                    """, Integer.class, tenantId, existing.getCostSubjectId());
            if (pendingFacts != null && pendingFacts > 0) {
                throw new BusinessException("OVERHEAD_RULE_PENDING_ALLOCATION",
                        "仍有尚未分摊的间接费成本，完成对应期间分摊后方可停用规则");
            }
        }
        OverheadAllocationRule update = new OverheadAllocationRule();
        update.setId(existing.getId());
        update.setTenantId(existing.getTenantId());
        update.setStatus(status);
        ruleMapper.updateById(update);
    }

    @Scheduled(cron = "0 0 2 1 * ?")
    public void scheduledMonthlyAllocation() {
        if (!scheduledMonthlyAllocationRunning.compareAndSet(false, true)) {
            log.warn("Previous monthly allocation still running, skipping this trigger");
            return;
        }
        try {
            LocalDate period = YearMonth.now().minusMonths(1).atEndOfMonth();
            for (Long tenantId : runMapper.selectActiveTenantIds()) {
                try {
                    executeScheduledAllocation(tenantId, period);
                } catch (Exception e) {
                    log.error("月度分摊失败 tenantId={} period={}", tenantId, period, e);
                }
            }
        } finally {
            scheduledMonthlyAllocationRunning.set(false);
        }
    }

    public OverheadAllocationExecutionResult executeAllocation(Long tenantId, LocalDate period) {
        Long authenticatedTenantId = UserContext.getCurrentTenantId();
        if (authenticatedTenantId == null || !authenticatedTenantId.equals(tenantId)) {
            throw new BusinessException("TENANT_ACCESS_DENIED", "认证租户与执行租户不一致");
        }
        requireCompanyFinanceOperator();
        return executeAllocationInCurrentTenant(tenantId, period, "MANUAL", UserContext.getCurrentUserId());
    }

    private void requireCompanyFinanceOperator() {
        Integer matches = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM sys_user u
                JOIN sys_user_role ur ON ur.tenant_id=u.tenant_id AND ur.user_id=u.id
                JOIN sys_role r ON r.tenant_id=ur.tenant_id AND r.id=ur.role_id
                WHERE u.tenant_id=? AND u.id=? AND u.status='ENABLE' AND u.deleted_flag=0
                  AND r.role_code=? AND r.status='ENABLE' AND r.deleted_flag=0
                """, Integer.class, UserContext.getCurrentTenantId(), UserContext.getCurrentUserId(),
                SystemRoleContract.COMPANY_FINANCE);
        if (matches == null || matches == 0) {
            throw new BusinessException("COST_COMPANY_FINANCE_REQUIRED", "仅公司财务可维护并执行间接费用分摊");
        }
    }

    private void executeScheduledAllocation(Long tenantId, LocalDate period) {
        UserContext.Snapshot original = UserContext.capture();
        try {
            // 租户插件从线程上下文追加条件，定时线程必须显式绑定当前遍历租户。
            UserContext.restore(new UserContext.Snapshot(null, "overhead-scheduler", tenantId, List.of()));
            executeAllocationInCurrentTenant(tenantId, period, "SCHEDULED", null);
        } finally {
            UserContext.restore(original);
        }
    }

    /** 显式 TransactionTemplate 保证同类内定时调用也覆盖完整执行事务。 */
    private OverheadAllocationExecutionResult executeAllocationInCurrentTenant(
            Long tenantId, LocalDate period, String triggerType, Long executedBy) {
        validatePeriod(tenantId, period);
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        OverheadAllocationExecutionResult result = template.execute(
                status -> doExecuteAllocation(tenantId, period, triggerType, executedBy));
        if (result == null) {
            throw new BusinessException("OVERHEAD_EXECUTION_FAILED", "间接费分摊未返回执行结果");
        }
        return result;
    }

    private OverheadAllocationExecutionResult doExecuteAllocation(
            Long tenantId, LocalDate period, String triggerType, Long executedBy) {
        accountingPeriodGuard.assertWritable(period);
        List<OverheadAllocationRule> rules = ruleMapper.selectList(
                new LambdaQueryWrapper<OverheadAllocationRule>()
                        .eq(OverheadAllocationRule::getTenantId, tenantId)
                        .eq(OverheadAllocationRule::getStatus, "ENABLE")
                        .eq(OverheadAllocationRule::getAllocationCycle, "MONTHLY")
                        .orderByAsc(OverheadAllocationRule::getId));

        List<PmProject> projects = projectMapper.selectList(
                new LambdaQueryWrapper<PmProject>()
                        .eq(PmProject::getTenantId, tenantId)
                        .eq(PmProject::getStatus, "ACTIVE")
                        .orderByAsc(PmProject::getId));

        if (rules.isEmpty() || projects.isEmpty()) {
            return result(period, rules.size(), 0, 0, 0, BigDecimal.ZERO);
        }

        int createdRuns = 0;
        int duplicateRuns = 0;
        int costItemCount = 0;
        BigDecimal allocatedTotal = BigDecimal.ZERO;
        Set<Long> affectedProjectIds = new HashSet<>();

        for (OverheadAllocationRule rule : rules) {
            rule = ruleForUpdate(tenantId, rule.getId());
            if (!"ENABLE".equals(rule.getStatus()) || !"MONTHLY".equals(rule.getAllocationCycle())) {
                continue;
            }
            requireSupportedRuleShape(rule.getAllocationBasis(), rule.getAllocationCycle());
            OverheadAllocationRun run = newRun(tenantId, rule.getId(), period, triggerType, executedBy);
            boolean newRun = false;
            try {
                runMapper.insert(run);
                createdRuns++;
                newRun = true;
            } catch (DuplicateKeyException duplicate) {
                duplicateRuns++;
                run = existingRunForUpdate(tenantId, rule.getId(), period);
            }

            List<CostItem> sourceCandidates = periodFactCandidates(tenantId, rule.getCostSubjectId(), period);
            Set<Long> lockedProjectIds = new HashSet<>();
            projects.forEach(project -> lockedProjectIds.add(project.getId()));
            sourceCandidates.forEach(fact -> lockedProjectIds.add(fact.getProjectId()));
            lockActiveProjects(tenantId, lockedProjectIds);
            List<CostItem> sourceFacts = getPeriodFacts(tenantId, sourceCandidates);
            BigDecimal sourceAmount = sourceFacts.stream().map(CostItem::getAmount).filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
            BigDecimal sourceTax = sourceFacts.stream().map(CostItem::getTaxAmount).filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
            BigDecimal sourceNet = sourceFacts.stream().map(CostItem::getAmountWithoutTax).filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
            if (sourceAmount.compareTo(sourceTax.add(sourceNet)) != 0) {
                throw new BusinessException("COST_FACT_AMOUNT_NOT_CONSERVED", "间接费来源事实价税合计不守恒");
            }
            if (sourceFacts.isEmpty()) {
                if (newRun) completeRun(run, "SKIPPED_ZERO", BigDecimal.ZERO, 0);
                continue;
            }
            if (sourceAmount.signum() == 0 && sourceTax.signum() == 0 && sourceNet.signum() == 0) {
                int clearedCount = insertClearingFacts(tenantId, period, rule, run, sourceFacts, affectedProjectIds);
                appendRun(run, BigDecimal.ZERO, clearedCount);
                costItemCount += clearedCount;
                continue;
            }

            Map<Long, BigDecimal> ratios = positiveRatios(computeRatios(rule, projects, period));
            if (ratios.isEmpty()) {
                if (newRun) completeRun(run, "SKIPPED_NO_WEIGHT", BigDecimal.ZERO, 0);
                continue;
            }

            Map<Long, BigDecimal> taxAllocations = allocationMap(allocateWithRemainder(sourceTax, ratios));
            Map<Long, BigDecimal> netAllocations = allocationMap(allocateWithRemainder(sourceNet, ratios));
            List<Allocation> allocations = ratios.keySet().stream()
                    .map(projectId -> new Allocation(projectId,
                            taxAllocations.getOrDefault(projectId, BigDecimal.ZERO)
                                    .add(netAllocations.getOrDefault(projectId, BigDecimal.ZERO))))
                    .toList();
            int runItemCount = 0;
            BigDecimal runAllocatedAmount = BigDecimal.ZERO;
            runItemCount += insertClearingFacts(tenantId, period, rule, run, sourceFacts, affectedProjectIds);
            for (Allocation allocation : allocations) {
                BigDecimal allocatedTax = taxAllocations.getOrDefault(allocation.projectId(), BigDecimal.ZERO);
                BigDecimal allocatedNet = netAllocations.getOrDefault(allocation.projectId(), BigDecimal.ZERO);
                if (allocation.amount().signum() == 0 && allocatedTax.signum() == 0 && allocatedNet.signum() == 0) {
                    continue;
                }
                CostItem item = new CostItem();
                item.setTenantId(tenantId);
                item.setProjectId(allocation.projectId());
                CostSubjectResolver.Decision decision = costSubjectResolver.resolveForFact(
                        tenantId, allocation.projectId(), SOURCE_TYPE, rule.getAllocationBasis(),
                        run.getId(), allocation.projectId(), rule.getCostSubjectId(), period);
                item.setCostSubjectId(decision.costSubjectId());
                item.setClassificationStatus(decision.classificationStatus());
                item.setClassificationBusinessCategory(rule.getAllocationBasis());
                item.setMappingVersionId(decision.mappingVersionId());
                item.setAssignmentRuleId(decision.assignmentRuleId());
                item.setOriginalCostSubjectId(decision.originalCostSubjectId());
                item.setClassificationOverrideId(decision.overrideId());
                item.setClassificationSnapshotId(decision.snapshotId());
                item.setCostType("OVERHEAD_ALLOCATED");
                item.setAmount(allocation.amount());
                item.setTaxAmount(allocatedTax);
                item.setAmountWithoutTax(allocatedNet);
                item.setSourceType(SOURCE_TYPE);
                item.setSourceId(run.getId());
                item.setSourceItemId(IdWorker.getId());
                item.setCostDate(period);
                item.setCostStatus("CONFIRMED");
                item.setGeneratedFlag(1);
                item.setRemark("间接费月度分摊 ruleId=" + rule.getId() + ", period=" + period);
                costItemMapper.insert(item);
                costSubjectResolver.markSnapshotPosted(decision);

                runItemCount++;
                runAllocatedAmount = runAllocatedAmount.add(allocation.amount());
                affectedProjectIds.add(allocation.projectId());
            }
            appendRun(run, runAllocatedAmount, runItemCount);
            costItemCount += runItemCount;
            allocatedTotal = allocatedTotal.add(runAllocatedAmount);
        }

        for (Long projectId : affectedProjectIds.stream().sorted().toList()) {
            costSummaryService.refreshSummary(tenantId, projectId);
        }

        log.info("间接费分摊完成 tenantId={} period={} triggerType={} executedBy={} newRuns={} duplicates={} items={} amount={}",
                tenantId, period, triggerType, executedBy, createdRuns, duplicateRuns, costItemCount, allocatedTotal);
        return result(period, rules.size(), createdRuns, duplicateRuns, costItemCount, allocatedTotal);
    }

    private int insertClearingFacts(
            Long tenantId,
            LocalDate period,
            OverheadAllocationRule rule,
            OverheadAllocationRun run,
            List<CostItem> sourceFacts,
            Set<Long> affectedProjectIds) {
        for (CostItem source : sourceFacts) {
            CostItem clearing = new CostItem();
            clearing.setTenantId(tenantId);
            clearing.setProjectId(source.getProjectId());
            clearing.setWbsTaskId(source.getWbsTaskId());
            clearing.setContractId(source.getContractId());
            clearing.setPartnerId(source.getPartnerId());
            clearing.setCostSubjectId(source.getCostSubjectId());
            clearing.setClassificationStatus("REVERSAL");
            clearing.setClassificationBusinessCategory(source.getClassificationBusinessCategory());
            clearing.setRecognitionRole(source.getRecognitionRole());
            clearing.setRootSourceType(source.getRootSourceType() == null
                    ? source.getSourceType() : source.getRootSourceType());
            clearing.setMappingVersionId(source.getMappingVersionId());
            clearing.setAssignmentRuleId(source.getAssignmentRuleId());
            clearing.setOriginalCostSubjectId(source.getOriginalCostSubjectId());
            clearing.setClassificationOverrideId(source.getClassificationOverrideId());
            clearing.setClassificationSnapshotId(source.getClassificationSnapshotId());
            clearing.setOriginalCostItemId(source.getId());
            clearing.setCostType("OVERHEAD_CLEARING");
            clearing.setAmount(money(source.getAmount()).negate());
            clearing.setTaxAmount(money(source.getTaxAmount()).negate());
            clearing.setAmountWithoutTax(money(source.getAmountWithoutTax()).negate());
            clearing.setSourceType("OVERHEAD_ALLOCATION_CLEARING");
            clearing.setSourceId(run.getId());
            clearing.setSourceItemId(source.getId());
            clearing.setCostDate(period);
            clearing.setCostStatus("CONFIRMED");
            clearing.setGeneratedFlag(1);
            clearing.setRemark("间接费月度分摊转出 ruleId=" + rule.getId() + ", period=" + period);
            costItemMapper.insert(clearing);
            affectedProjectIds.add(source.getProjectId());
        }
        return sourceFacts.size();
    }

    private void validatePeriod(Long tenantId, LocalDate period) {
        if (tenantId == null) {
            throw new BusinessException("UNAUTHORIZED", "无法确定租户身份");
        }
        if (period == null || !period.equals(YearMonth.from(period).atEndOfMonth())) {
            throw new BusinessException("INVALID_OVERHEAD_PERIOD", "分摊期间必须是目标自然月月末");
        }
        if (!YearMonth.from(period).isBefore(YearMonth.now())) {
            throw new BusinessException("INVALID_OVERHEAD_PERIOD", "只允许执行已完整结束月份的分摊");
        }
    }

    private OverheadAllocationRun newRun(
            Long tenantId, Long ruleId, LocalDate period, String triggerType, Long executedBy) {
        OverheadAllocationRun run = new OverheadAllocationRun();
        run.setTenantId(tenantId);
        run.setRuleId(ruleId);
        run.setPeriod(period);
        run.setTriggerType(triggerType);
        run.setExecutedBy(executedBy);
        run.setRunStatus("PENDING");
        run.setAllocatedAmount(BigDecimal.ZERO);
        run.setCostItemCount(0);
        return run;
    }

    private void completeRun(OverheadAllocationRun run, String status, BigDecimal amount, int itemCount) {
        run.setRunStatus(status);
        run.setAllocatedAmount(amount.setScale(2, RoundingMode.HALF_UP));
        run.setCostItemCount(itemCount);
        runMapper.updateById(run);
    }

    private void appendRun(OverheadAllocationRun run, BigDecimal amount, int itemCount) {
        int updated = jdbc.update("""
                UPDATE overhead_allocation_run
                SET run_status='SUCCESS',allocated_amount=allocated_amount+?,cost_item_count=cost_item_count+?,
                    updated_by=?,updated_at=CURRENT_TIMESTAMP
                WHERE tenant_id=? AND id=? AND deleted_flag=0
                """, amount.setScale(2, RoundingMode.HALF_UP), itemCount,
                UserContext.getCurrentUserId(), run.getTenantId(), run.getId());
        if (updated != 1) {
            throw new BusinessException("OVERHEAD_RUN_STATE_INVALID", "间接费执行批次状态已变化");
        }
    }

    private OverheadAllocationRun existingRunForUpdate(Long tenantId, Long ruleId, LocalDate period) {
        List<Long> ids = jdbc.queryForList("""
                SELECT id FROM overhead_allocation_run
                WHERE tenant_id=? AND rule_id=? AND period=? AND deleted_flag=0 FOR UPDATE
                """, Long.class, tenantId, ruleId, period);
        if (ids.size() != 1) {
            throw new BusinessException("OVERHEAD_RUN_STATE_INVALID", "间接费执行批次不存在或冲突");
        }
        OverheadAllocationRun run = runMapper.selectById(ids.getFirst());
        if (run == null || !tenantId.equals(run.getTenantId())) {
            throw new BusinessException("OVERHEAD_RUN_STATE_INVALID", "间接费执行批次不存在或跨租户");
        }
        return run;
    }

    private List<CostItem> periodFactCandidates(Long tenantId, Long costSubjectId, LocalDate period) {
        return costItemMapper.selectList(new LambdaQueryWrapper<CostItem>()
                        .eq(CostItem::getTenantId, tenantId)
                        .eq(CostItem::getCostSubjectId, costSubjectId)
                        .in(CostItem::getCostStatus, "CONFIRMED", "POSTED")
                        .eq(CostItem::getRecognitionRole, "ACTUAL")
                        .ne(CostItem::getClassificationStatus, "UNCLASSIFIED")
                        .notIn(CostItem::getSourceType, SOURCE_TYPE, "OVERHEAD_ALLOCATION_CLEARING",
                                "COST_RECALCULATION_NEGATIVE", "COST_RECALCULATION_POSITIVE",
                                "COST_RECALCULATION_REVERSAL")
                        .ge(CostItem::getCostDate, period.withDayOfMonth(1))
                        .le(CostItem::getCostDate, period));
    }

    private List<CostItem> getPeriodFacts(Long tenantId, List<CostItem> facts) {
        List<CostItem> result = new ArrayList<>();
        for (CostItem fact : facts) {
            Integer cleared = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM cost_item
                    WHERE tenant_id=? AND original_cost_item_id=?
                      AND source_type='OVERHEAD_ALLOCATION_CLEARING'
                      AND deleted_flag=0 AND cost_status<>'WRITE_OFF'
                    """, Integer.class, tenantId, fact.getId());
            if (cleared != null && cleared > 0) continue;
            CostItem current = costFactLineageResolver.requireCurrentLeaf(tenantId, fact.getId());
            if (!Objects.equals(current.getId(), fact.getId())) {
                throw new BusinessException("OVERHEAD_RECALCULATION_SOURCE_NOT_SUPPORTED",
                        "成本事实已被历史重算承接，不能进入间接费分摊");
            }
            result.add(fact);
        }
        return result;
    }

    private void lockActiveProjects(Long tenantId, Set<Long> projectIds) {
        if (projectIds.isEmpty() || projectIds.contains(null)) {
            throw new BusinessException("OVERHEAD_PROJECT_INVALID", "间接费来源或分摊目标缺少有效项目");
        }
        List<Long> orderedIds = projectIds.stream().sorted().toList();
        String placeholders = String.join(",", java.util.Collections.nCopies(orderedIds.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.addAll(orderedIds);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id,status FROM pm_project
                WHERE tenant_id=? AND id IN (%s) AND deleted_flag=0
                ORDER BY id FOR UPDATE
                """.formatted(placeholders), args.toArray());
        if (rows.size() != orderedIds.size()
                || rows.stream().anyMatch(row -> !"ACTIVE".equals(row.get("status")))) {
            throw new BusinessException("OVERHEAD_PROJECT_NOT_ACTIVE", "间接费来源与分摊目标项目必须处于在建状态");
        }
    }

    private Map<Long, BigDecimal> computeRatios(
            OverheadAllocationRule rule, List<PmProject> projects, LocalDate period) {
        Map<Long, BigDecimal> ratios = new LinkedHashMap<>();
        switch (rule.getAllocationBasis()) {
            case "DIRECT_LABOR" -> {
                for (PmProject project : projects) {
                    BigDecimal laborCost = costItemMapper.selectList(new LambdaQueryWrapper<CostItem>()
                                    .eq(CostItem::getTenantId, project.getTenantId())
                                    .eq(CostItem::getProjectId, project.getId())
                                    .eq(CostItem::getCostType, "LABOR")
                                    .eq(CostItem::getRecognitionRole, "ACTUAL")
                                    .in(CostItem::getCostStatus, "CONFIRMED", "POSTED")
                                    .ne(CostItem::getClassificationStatus, "UNCLASSIFIED")
                                    .ge(CostItem::getCostDate, period.withDayOfMonth(1))
                                    .le(CostItem::getCostDate, period))
                            .stream().map(CostItem::getAmount).filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    ratios.put(project.getId(), laborCost);
                }
            }
            case "CONTRACT_AMOUNT" -> {
                for (PmProject project : projects) {
                    ratios.put(project.getId(), project.getContractAmount() == null
                            ? BigDecimal.ZERO : project.getContractAmount());
                }
            }
            default -> throw new BusinessException("OVERHEAD_RULE_UNSUPPORTED", "间接费分摊规则类型尚未实现");
        }
        return ratios;
    }

    private Map<Long, BigDecimal> positiveRatios(Map<Long, BigDecimal> ratios) {
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        ratios.forEach((projectId, ratio) -> {
            if (ratio != null && ratio.signum() > 0) {
                result.put(projectId, ratio);
            }
        });
        return result;
    }

    private List<Allocation> allocateWithRemainder(
            BigDecimal sourceAmount, Map<Long, BigDecimal> ratios) {
        BigDecimal totalRatio = ratios.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Map.Entry<Long, BigDecimal>> entries = new ArrayList<>(ratios.entrySet());
        List<Allocation> allocations = new ArrayList<>(entries.size());
        BigDecimal allocated = BigDecimal.ZERO;
        for (int index = 0; index < entries.size(); index++) {
            Map.Entry<Long, BigDecimal> entry = entries.get(index);
            BigDecimal amount = index == entries.size() - 1
                    ? sourceAmount.subtract(allocated)
                    : sourceAmount.multiply(entry.getValue()).divide(totalRatio, 2, RoundingMode.DOWN);
            amount = amount.setScale(2, RoundingMode.HALF_UP);
            allocations.add(new Allocation(entry.getKey(), amount));
            allocated = allocated.add(amount);
        }
        return allocations;
    }

    private Map<Long, BigDecimal> allocationMap(List<Allocation> allocations) {
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        allocations.forEach(allocation -> result.put(allocation.projectId(), allocation.amount()));
        return result;
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private OverheadAllocationExecutionResult result(
            LocalDate period, int ruleCount, int createdRuns, int duplicates,
            int itemCount, BigDecimal amount) {
        return new OverheadAllocationExecutionResult(
                period.toString(), ruleCount, createdRuns, duplicates, itemCount,
                amount.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                createdRuns == 0 && itemCount == 0 && duplicates > 0);
    }

    private record Allocation(Long projectId, BigDecimal amount) {
    }
}
