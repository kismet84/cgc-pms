package com.cgcpms.overhead.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.cost.service.CostSummaryService;
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
    private final PmProjectMapper projectMapper;
    private final CostSummaryService costSummaryService;
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
    public void update(OverheadAllocationRule rule) {
        Long tenantId = UserContext.getCurrentTenantId();
        OverheadAllocationRule existing = ruleMapper.selectById(rule.getId());
        if (existing == null || !existing.getTenantId().equals(tenantId)) {
            throw new BusinessException("RULE_NOT_FOUND", "分摊规则不存在");
        }
        rule.setTenantId(existing.getTenantId());
        ruleMapper.updateById(rule);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        OverheadAllocationRule existing = ruleMapper.selectById(id);
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId())) {
            throw new BusinessException("RULE_NOT_FOUND", "分摊规则不存在");
        }
        ruleMapper.deleteById(id);
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
        return executeAllocationInCurrentTenant(tenantId, period, "MANUAL", UserContext.getCurrentUserId());
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
            OverheadAllocationRun run = newRun(tenantId, rule.getId(), period, triggerType, executedBy);
            try {
                runMapper.insert(run);
                createdRuns++;
            } catch (DuplicateKeyException duplicate) {
                duplicateRuns++;
                continue;
            }

            BigDecimal sourceAmount = getPeriodAmount(tenantId, rule.getCostSubjectId(), period)
                    .setScale(2, RoundingMode.HALF_UP);
            if (sourceAmount.signum() == 0) {
                completeRun(run, "SKIPPED_ZERO", BigDecimal.ZERO, 0);
                continue;
            }

            Map<Long, BigDecimal> ratios = positiveRatios(computeRatios(rule, projects, period));
            if (ratios.isEmpty()) {
                completeRun(run, "SKIPPED_NO_WEIGHT", BigDecimal.ZERO, 0);
                continue;
            }

            List<Allocation> allocations = allocateWithRemainder(sourceAmount, ratios);
            int runItemCount = 0;
            BigDecimal runAllocatedAmount = BigDecimal.ZERO;
            for (Allocation allocation : allocations) {
                if (allocation.amount().signum() == 0) {
                    continue;
                }
                CostItem item = new CostItem();
                item.setTenantId(tenantId);
                item.setProjectId(allocation.projectId());
                item.setCostSubjectId(rule.getCostSubjectId());
                item.setCostType("OVERHEAD_ALLOCATED");
                item.setAmount(allocation.amount());
                item.setTaxAmount(BigDecimal.ZERO);
                item.setAmountWithoutTax(allocation.amount());
                item.setSourceType(SOURCE_TYPE);
                item.setSourceId(run.getId());
                item.setSourceItemId(allocation.projectId());
                item.setCostDate(period);
                item.setCostStatus("CONFIRMED");
                item.setGeneratedFlag(1);
                item.setRemark("间接费月度分摊 ruleId=" + rule.getId() + ", period=" + period);
                costItemMapper.insert(item);

                runItemCount++;
                runAllocatedAmount = runAllocatedAmount.add(allocation.amount());
                affectedProjectIds.add(allocation.projectId());
            }
            completeRun(run, "SUCCESS", runAllocatedAmount, runItemCount);
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

    private BigDecimal getPeriodAmount(Long tenantId, Long costSubjectId, LocalDate period) {
        return costItemMapper.selectList(new LambdaQueryWrapper<CostItem>()
                        .eq(CostItem::getTenantId, tenantId)
                        .eq(CostItem::getCostSubjectId, costSubjectId)
                        .eq(CostItem::getCostStatus, "CONFIRMED")
                        .ne(CostItem::getSourceType, SOURCE_TYPE)
                        .ge(CostItem::getCostDate, period.withDayOfMonth(1))
                        .le(CostItem::getCostDate, period))
                .stream()
                .map(CostItem::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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
            default -> {
                for (PmProject project : projects) {
                    ratios.put(project.getId(), BigDecimal.ONE);
                }
            }
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

    private OverheadAllocationExecutionResult result(
            LocalDate period, int ruleCount, int createdRuns, int duplicates,
            int itemCount, BigDecimal amount) {
        return new OverheadAllocationExecutionResult(
                period.toString(), ruleCount, createdRuns, duplicates, itemCount,
                amount.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                createdRuns == 0 && duplicates > 0);
    }

    private record Allocation(Long projectId, BigDecimal amount) {
    }
}
