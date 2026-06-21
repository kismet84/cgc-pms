package com.cgcpms.overhead.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import com.cgcpms.cost.service.CostSummaryService;
import com.cgcpms.overhead.entity.OverheadAllocationRule;
import com.cgcpms.overhead.mapper.OverheadAllocationRuleMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * 间接费用分摊执行引擎。
 * <p>
 * 按月定时执行分摊 Job 将所有 ENABLE 状态的规则分摊到各活跃项目。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OverheadAllocationService {

    private final OverheadAllocationRuleMapper ruleMapper;
    private final CostItemMapper costItemMapper;
    private final CostSubjectMapper costSubjectMapper;
    private final PmProjectMapper projectMapper;
    private final CostSummaryService costSummaryService;

    public IPage<OverheadAllocationRule> getPage(long pageNo, long pageSize) {
        Long tenantId = UserContext.getCurrentTenantId();
        LambdaQueryWrapper<OverheadAllocationRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OverheadAllocationRule::getTenantId, tenantId);
        wrapper.orderByAsc(OverheadAllocationRule::getCostSubjectId);
        return ruleMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
    }

    @Transactional
    public Long create(OverheadAllocationRule rule) {
        rule.setTenantId(UserContext.getCurrentTenantId());
        rule.setStatus("ENABLE");
        ruleMapper.insert(rule);
        return rule.getId();
    }

    @Transactional
    public void update(OverheadAllocationRule rule) {
        Long tenantId = UserContext.getCurrentTenantId();
        OverheadAllocationRule existing = ruleMapper.selectById(rule.getId());
        if (existing == null || !existing.getTenantId().equals(tenantId))
            throw new BusinessException("RULE_NOT_FOUND", "分摊规则不存在");
        // Preserve tenant from existing record, ignore client-supplied value
        rule.setTenantId(existing.getTenantId());
        ruleMapper.updateById(rule);
    }

    @Transactional
    public void delete(Long id) {
        OverheadAllocationRule existing = ruleMapper.selectById(id);
        if (existing == null || !existing.getTenantId().equals(UserContext.getCurrentTenantId()))
            throw new BusinessException("RULE_NOT_FOUND", "分摊规则不存在");
        ruleMapper.deleteById(id);
    }

    /**
     * 按月定时执行分摊 (每月1日凌晨2点)。
     */
    @Scheduled(cron = "0 0 2 1 * ?")
    public void scheduledMonthlyAllocation() {
        log.info("月度间接费用分摊 Job 开始");
        executeAllocation(UserContext.getCurrentTenantId(), LocalDate.now().withDayOfMonth(1).minusDays(1));
    }

    /**
     * 手动触发分摊。
     */
    @Transactional
    public void executeAllocation(Long tenantId, LocalDate period) {
        List<OverheadAllocationRule> rules = ruleMapper.selectList(
                new LambdaQueryWrapper<OverheadAllocationRule>()
                        .eq(OverheadAllocationRule::getTenantId, tenantId)
                        .eq(OverheadAllocationRule::getStatus, "ENABLE")
                        .eq(OverheadAllocationRule::getAllocationCycle, "MONTHLY"));

        if (rules.isEmpty()) {
            log.info("无启用分摊规则，跳过 tenantId={}", tenantId);
            return;
        }

        List<PmProject> projects = projectMapper.selectList(
                new LambdaQueryWrapper<PmProject>()
                        .eq(PmProject::getTenantId, tenantId)
                        .eq(PmProject::getStatus, "ACTIVE"));

        if (projects.isEmpty()) {
            log.info("无活跃项目，跳过分摊 tenantId={}", tenantId);
            return;
        }

        Set<Long> affectedProjectIds = new HashSet<>();

        for (OverheadAllocationRule rule : rules) {
            BigDecimal totalAmount = getPeriodAmount(tenantId, rule.getCostSubjectId(), period);
            if (totalAmount.compareTo(BigDecimal.ZERO) == 0) continue;

            Map<Long, BigDecimal> ratios = computeRatios(rule, projects, period);
            BigDecimal totalRatio = ratios.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

            for (Map.Entry<Long, BigDecimal> entry : ratios.entrySet()) {
                Long projectId = entry.getKey();
                BigDecimal ratio = entry.getValue();
                if (totalRatio.compareTo(BigDecimal.ZERO) == 0) continue;
                BigDecimal allocatedAmount = totalAmount.multiply(ratio)
                        .divide(totalRatio, 2, RoundingMode.HALF_UP);

                CostItem item = new CostItem();
                item.setTenantId(tenantId);
                item.setProjectId(projectId);
                item.setCostSubjectId(rule.getCostSubjectId());
                item.setCostType("OVERHEAD_ALLOCATED");
                item.setAmount(allocatedAmount);
                item.setAmountWithoutTax(allocatedAmount);
                item.setSourceType("OVERHEAD_ALLOCATION");
                item.setSourceId(rule.getId());
                item.setSourceItemId(0L);
                item.setCostDate(period);
                item.setCostStatus("CONFIRMED");
                item.setGeneratedFlag(1);
                costItemMapper.insert(item);

                affectedProjectIds.add(projectId);
            }
        }

        // 批量刷新受影响项目的摘要
        for (Long projectId : affectedProjectIds) {
            costSummaryService.refreshSummary(tenantId, projectId);
        }

        log.info("月度间接费用分摊完成 tenantId={} period={} 规则数={} 受影响项目={}",
                tenantId, period, rules.size(), affectedProjectIds.size());
    }

    private BigDecimal getPeriodAmount(Long tenantId, Long costSubjectId, LocalDate period) {
        return costItemMapper.selectList(
                new LambdaQueryWrapper<CostItem>()
                        .eq(CostItem::getTenantId, tenantId)
                        .eq(CostItem::getCostSubjectId, costSubjectId)
                        .eq(CostItem::getCostStatus, "CONFIRMED")
                        .ge(CostItem::getCostDate, period.withDayOfMonth(1))
                        .le(CostItem::getCostDate, period))
                .stream()
                .map(CostItem::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<Long, BigDecimal> computeRatios(OverheadAllocationRule rule,
                                                  List<PmProject> projects, LocalDate period) {
        Map<Long, BigDecimal> ratios = new LinkedHashMap<>();
        switch (rule.getAllocationBasis()) {
            case "DIRECT_LABOR" -> {
                // 按直接人工费比例
                for (PmProject project : projects) {
                    BigDecimal laborCost = costItemMapper.selectList(
                            new LambdaQueryWrapper<CostItem>()
                                    .eq(CostItem::getTenantId, project.getTenantId())
                                    .eq(CostItem::getProjectId, project.getId())
                                    .eq(CostItem::getCostType, "LABOR")
                                    .ge(CostItem::getCostDate, period.withDayOfMonth(1))
                                    .le(CostItem::getCostDate, period))
                            .stream()
                            .map(CostItem::getAmount)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    ratios.put(project.getId(), laborCost);
                }
            }
            case "CONTRACT_AMOUNT" -> {
                // 按合同金额比例（简单实现：项目下所有合同金额之和）
                for (PmProject project : projects) {
                    BigDecimal contractTotal = project.getContractAmount() != null
                            ? project.getContractAmount() : BigDecimal.ZERO;
                    ratios.put(project.getId(), contractTotal);
                }
            }
            default -> {
                // 均分
                for (PmProject project : projects) {
                    ratios.put(project.getId(), BigDecimal.ONE);
                }
            }
        }
        return ratios;
    }
}
