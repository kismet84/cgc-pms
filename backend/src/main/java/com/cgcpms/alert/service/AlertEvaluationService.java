package com.cgcpms.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.alert.auth.AlertAccessScopeResolver;
import com.cgcpms.alert.entity.AlertLog;
import com.cgcpms.alert.entity.AlertRuleConfig;
import com.cgcpms.alert.mapper.AlertLogMapper;
import com.cgcpms.alert.mapper.AlertRuleConfigMapper;
import com.cgcpms.alert.notification.AlertNotificationDispatcher;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.entity.CostSummary;
import com.cgcpms.cost.mapper.CostSummaryMapper;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.entity.PmProjectMember;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.project.mapper.PmProjectMemberMapper;
import com.cgcpms.purchase.entity.MatPurchaseOrder;
import com.cgcpms.purchase.mapper.MatPurchaseOrderMapper;
import com.cgcpms.receipt.entity.MatReceipt;
import com.cgcpms.receipt.mapper.MatReceiptMapper;
import com.cgcpms.settlement.entity.StlSettlement;
import com.cgcpms.settlement.mapper.StlSettlementMapper;
import com.cgcpms.subcontract.entity.SubMeasure;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import com.cgcpms.variation.entity.VarOrder;
import com.cgcpms.variation.mapper.VarOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Alert evaluation engine — batch-evaluates 8 rules for active projects
 * and persists results into {@code alert_log}.
 *
 * <p>Runs every 30 minutes via {@code @Scheduled(cron = "0 *&#47;30 * * * ?")}.
 * Deduplicates by skipping rules that already have an unread alert within
 * the last 24 hours for the same project.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
// TODO: 拆分超大文件 (567行) — 拆分为 RuleEvaluator 接口 + 各规则实现类 (8个 evaluator)
public class AlertEvaluationService {

    private static final int DEFAULT_DEDUP_HOURS = 24;
    private static final int DEFAULT_EXPIRING_DAYS = 30;
    private static final int DEFAULT_VARIATION_STALE_DAYS = 30;
    private static final BigDecimal DEFAULT_THRESHOLD_RATIO = BigDecimal.ONE;
    private static final Map<String, String> ALERT_MESSAGE_TEMPLATES = Map.of(
            "DYNAMIC_COST_EXCEEDS_TARGET", "动态成本 %s 超出目标成本 %s，偏差 %s，请复核项目动态成本。",
            "MATERIAL_EXCEEDS_BUDGET", "材料验收金额 %s 超出合同 %s(%s) 金额 %s，请复核材料采购及损耗。",
            "SUBCONTRACT_EXCEEDS_CONTRACT", "分包计量累计金额 %s 超出合同 %s(%s) 金额 %s，请复核分包计量。",
            "CONTRACT_OVERDUE", "以下合同已超期：%s，请尽快处理合同履约进度。",
            "PAYMENT_EXCEEDS_RATIO", "合同 %s(%s) 累计付款 %s 超过合同金额 %s（比例 %.0f%%），请复核付款计划。",
            "WARRANTY_EARLY_RELEASE", "合同 %s(%s) 质保金 %.2f 已于 %s 定案，但保修期至 %s 尚未届满，请复核质保金释放。",
            "CONTRACT_EXPIRING", "以下合同即将到期（%d天内）：%s，请提前安排续签或收尾。",
            "VARIATION_UNCONFIRMED", "以下变更签证已审批超%d天仍未获甲方确认：%s，请跟进确认。",
            "PURCHASE_DELIVERY_OVERDUE", "采购订单 %s 交期已逾期至 %s，请跟进供应商交付。"
    );

    private final AlertLogMapper alertLogMapper;
    private final AlertRuleConfigMapper alertRuleConfigMapper;
    private final PmProjectMapper projectMapper;
    private final CostSummaryMapper costSummaryMapper;
    private final CtContractMapper ctContractMapper;
    private final PayRecordMapper payRecordMapper;
    private final SubMeasureMapper subMeasureMapper;
    private final MatReceiptMapper matReceiptMapper;
    private final MatPurchaseOrderMapper purchaseOrderMapper;
    private final VarOrderMapper varOrderMapper;
    private final StlSettlementMapper stlSettlementMapper;
    private final PmProjectMemberMapper projectMemberMapper;
    private final AlertNotificationDispatcher notificationDispatcher;
    private final AlertAccessScopeResolver accessScopeResolver;
    private final AlertSubscriptionService alertSubscriptionService;

    /**
     * Prevents overlapping executions of the scheduled alert evaluation task.
     */
    private final AtomicBoolean scheduledEvaluateRunning = new AtomicBoolean(false);

    // ──────────────────────────────────────────────
    // Scheduled entry point
    // ──────────────────────────────────────────────

    @Scheduled(cron = "0 */30 * * * ?")
    public void scheduledEvaluate() {
        if (!scheduledEvaluateRunning.compareAndSet(false, true)) {
            log.warn("Previous scheduled alert evaluation still running, skipping this trigger");
            return;
        }
        log.info("Starting scheduled alert evaluation...");
        try {
            LambdaQueryWrapper<PmProject> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(PmProject::getStatus, "ACTIVE");
            List<PmProject> activeProjects = projectMapper.selectList(wrapper);

            log.info("Found {} active projects for alert evaluation", activeProjects.size());
            for (PmProject project : activeProjects) {
                try {
                    // M-004: Use AOP proxy to ensure @Transactional is applied
                    ((AlertEvaluationService) AopContext.currentProxy()).evaluateProject(project.getTenantId(), project.getId());
                } catch (Exception e) {
                    log.error("Failed to evaluate alerts for project {}", project.getId(), e);
                }
            }
        } catch (Exception e) {
            log.error("Scheduled alert evaluation failed", e);
        } finally {
            scheduledEvaluateRunning.set(false);
        }
        log.info("Scheduled alert evaluation completed");
    }

    // ──────────────────────────────────────────────
    // Public API — manual trigger
    // ──────────────────────────────────────────────

    @Transactional(rollbackFor = Exception.class)
    public int batchEvaluate(Long tenantId) {
        log.info("Manual alert evaluation triggered for tenantId={}", tenantId);
        LambdaQueryWrapper<PmProject> wrapper = new LambdaQueryWrapper<PmProject>()
                .eq(PmProject::getTenantId, tenantId)
                .eq(PmProject::getStatus, "ACTIVE");
        if (!accessScopeResolver.isAdmin()) {
            Set<Long> accessibleProjectIds = accessScopeResolver.accessibleProjectIds(tenantId);
            if (accessibleProjectIds.isEmpty()) {
                return 0;
            }
            wrapper.in(PmProject::getId, accessibleProjectIds);
        }
        List<PmProject> activeProjects = projectMapper.selectList(wrapper);
        int totalAlerts = 0;
        for (PmProject project : activeProjects) {
            totalAlerts += evaluateProject(tenantId, project.getId());
        }
        log.info("Manual alert evaluation done: {} alerts generated for tenantId={}", totalAlerts, tenantId);
        return totalAlerts;
    }

    // ──────────────────────────────────────────────
    // Core evaluation
    // ──────────────────────────────────────────────

    @Transactional(rollbackFor = Exception.class)
    public int evaluateProject(Long tenantId, Long projectId) {
        Map<String, AlertRuleConfig> ruleConfigs = loadRuleConfigs(tenantId);
        List<AlertLog> alerts = new ArrayList<>();

        // Rule 1: 动态成本超目标
        alerts.addAll(evaluateDynamicCostExceedsTarget(tenantId, projectId, ruleConfigs));

        // Rule 2: 材料超预算
        alerts.addAll(evaluateMaterialExceedsBudget(tenantId, projectId, ruleConfigs));

        // Rule 3: 分包超合同
        alerts.addAll(evaluateSubcontractExceedsContract(tenantId, projectId, ruleConfigs));

        // Rule 4: 合同超期
        alerts.addAll(evaluateContractOverdue(tenantId, projectId, ruleConfigs));

        // Rule 5: 付款超比例
        alerts.addAll(evaluatePaymentExceedsRatio(tenantId, projectId, ruleConfigs));

        // Rule 6: 质保金提前释放
        alerts.addAll(evaluateWarrantyEarlyRelease(tenantId, projectId, ruleConfigs));

        // Rule 7: 合同到期
        alerts.addAll(evaluateContractExpiring(tenantId, projectId, ruleConfigs));

        // Rule 8: 变更未确认
        alerts.addAll(evaluateVariationUnconfirmed(tenantId, projectId, ruleConfigs));

        // Rule 9: 采购交期逾期
        alerts.addAll(evaluatePurchaseDeliveryOverdue(tenantId, projectId, ruleConfigs));

        // Persist
        for (AlertLog alert : alerts) {
            alertLogMapper.insert(alert);
            try {
                createAlertNotification(tenantId, projectId, alert);
            } catch (Exception e) {
                log.warn("Failed to create notification for alert id={}, ruleType={}: {}",
                        alert.getId(), alert.getRuleType(), e.getMessage());
            }
        }
        if (!alerts.isEmpty()) {
            log.info("Project {}: {} alert(s) generated", projectId, alerts.size());
        }
        return alerts.size();
    }

    // ──────────────────────────────────────────────
    // Rule 1: 动态成本超目标
    // ──────────────────────────────────────────────

    private List<AlertLog> evaluateDynamicCostExceedsTarget(Long tenantId, Long projectId,
                                                            Map<String, AlertRuleConfig> ruleConfigs) {
        AlertRuleConfig config = configFor(ruleConfigs, "DYNAMIC_COST_EXCEEDS_TARGET");
        if (!isEnabled(config)) {
            return Collections.emptyList();
        }
        String dedupKey = projectRuleDedupKey(projectId, "DYNAMIC_COST_EXCEEDS_TARGET");
        if (isDuplicate(tenantId, dedupKey, dedupHours(config))) {
            return Collections.emptyList();
        }
        List<CostSummary> summaries = costSummaryMapper.selectList(
                new LambdaQueryWrapper<CostSummary>()
                        .eq(CostSummary::getTenantId, tenantId)
                        .eq(CostSummary::getProjectId, projectId));
        for (CostSummary s : summaries) {
            BigDecimal dynamic = nvl(s.getDynamicCost());
            BigDecimal target = nvl(s.getTargetCost());
            if (target.compareTo(BigDecimal.ZERO) <= 0) continue;
            if (ratio(dynamic, target).compareTo(thresholdRatio(config)) > 0) {
                BigDecimal deviation = dynamic.subtract(target);
                return List.of(buildAlert(tenantId, projectId, null,
                        "DYNAMIC_COST_EXCEEDS_TARGET", "HIGH",
                        alertMessage("DYNAMIC_COST_EXCEEDS_TARGET",
                                dynamic.toPlainString(), target.toPlainString(),
                                deviation.toPlainString()),
                        config, null, null, dedupKey));
            }
        }
        return Collections.emptyList();
    }

    // ──────────────────────────────────────────────
    // Rule 2: 材料超预算 — approved receipt total vs contract amount
    // ──────────────────────────────────────────────

    private List<AlertLog> evaluateMaterialExceedsBudget(Long tenantId, Long projectId,
                                                         Map<String, AlertRuleConfig> ruleConfigs) {
        AlertRuleConfig config = configFor(ruleConfigs, "MATERIAL_EXCEEDS_BUDGET");
        if (!isEnabled(config)) {
            return Collections.emptyList();
        }
        List<MatReceipt> receipts = matReceiptMapper.selectList(
                new LambdaQueryWrapper<MatReceipt>()
                        .eq(MatReceipt::getTenantId, tenantId)
                        .eq(MatReceipt::getProjectId, projectId)
                        .eq(MatReceipt::getApprovalStatus, "APPROVED"));
        // Group receipts by contractId
        Map<Long, BigDecimal> receiptByContract = new HashMap<>();
        for (MatReceipt r : receipts) {
            Long cid = r.getContractId();
            if (cid == null) continue;
            receiptByContract.merge(cid, nvl(r.getTotalAmount()), BigDecimal::add);
        }
        // M-009: Batch load contracts to avoid N+1
        Map<Long, CtContract> contractMap = batchLoadContracts(receiptByContract.keySet());
        for (Map.Entry<Long, BigDecimal> entry : receiptByContract.entrySet()) {
            CtContract contract = contractMap.get(entry.getKey());
            if (contract == null) continue;
            BigDecimal contractAmount = nvl(contract.getContractAmount());
            if (contractAmount.compareTo(BigDecimal.ZERO) <= 0) continue;
            String dedupKey = contractRuleDedupKey(entry.getKey(), "MATERIAL_EXCEEDS_BUDGET");
            if (isDuplicate(tenantId, dedupKey, dedupHours(config))) continue;
            if (ratio(entry.getValue(), contractAmount).compareTo(thresholdRatio(config)) > 0) {
                return List.of(buildAlert(tenantId, projectId, entry.getKey(),
                        "MATERIAL_EXCEEDS_BUDGET", "MEDIUM",
                        alertMessage("MATERIAL_EXCEEDS_BUDGET",
                                entry.getValue().toPlainString(),
                                contract.getContractCode(),
                                contract.getContractName(),
                                contractAmount.toPlainString()),
                        config, null, null, dedupKey));
            }
        }
        return Collections.emptyList();
    }

    // ──────────────────────────────────────────────
    // Rule 3: 分包超合同 — approved measure total vs contract amount
    // ──────────────────────────────────────────────

    private List<AlertLog> evaluateSubcontractExceedsContract(Long tenantId, Long projectId,
                                                              Map<String, AlertRuleConfig> ruleConfigs) {
        AlertRuleConfig config = configFor(ruleConfigs, "SUBCONTRACT_EXCEEDS_CONTRACT");
        if (!isEnabled(config)) {
            return Collections.emptyList();
        }
        List<SubMeasure> measures = subMeasureMapper.selectList(
                new LambdaQueryWrapper<SubMeasure>()
                        .eq(SubMeasure::getTenantId, tenantId)
                        .eq(SubMeasure::getProjectId, projectId)
                        .eq(SubMeasure::getApprovalStatus, "APPROVED"));
        Map<Long, BigDecimal> measureByContract = new HashMap<>();
        for (SubMeasure m : measures) {
            Long cid = m.getContractId();
            if (cid == null) continue;
            measureByContract.merge(cid, nvl(m.getApprovedAmount()), BigDecimal::add);
        }
        // M-009: Batch load contracts
        Map<Long, CtContract> contractMap = batchLoadContracts(measureByContract.keySet());
        for (Map.Entry<Long, BigDecimal> entry : measureByContract.entrySet()) {
            CtContract contract = contractMap.get(entry.getKey());
            if (contract == null) continue;
            BigDecimal contractAmount = nvl(contract.getContractAmount());
            if (contractAmount.compareTo(BigDecimal.ZERO) <= 0) continue;
            String dedupKey = contractRuleDedupKey(entry.getKey(), "SUBCONTRACT_EXCEEDS_CONTRACT");
            if (isDuplicate(tenantId, dedupKey, dedupHours(config))) continue;
            if (ratio(entry.getValue(), contractAmount).compareTo(thresholdRatio(config)) > 0) {
                return List.of(buildAlert(tenantId, projectId, entry.getKey(),
                        "SUBCONTRACT_EXCEEDS_CONTRACT", "HIGH",
                        alertMessage("SUBCONTRACT_EXCEEDS_CONTRACT",
                                entry.getValue().toPlainString(),
                                contract.getContractCode(),
                                contract.getContractName(),
                                contractAmount.toPlainString()),
                        config, null, null, dedupKey));
            }
        }
        return Collections.emptyList();
    }

    // ──────────────────────────────────────────────
    // Rule 4: 合同超期
    // ──────────────────────────────────────────────

    private List<AlertLog> evaluateContractOverdue(Long tenantId, Long projectId,
                                                   Map<String, AlertRuleConfig> ruleConfigs) {
        AlertRuleConfig config = configFor(ruleConfigs, "CONTRACT_OVERDUE");
        if (!isEnabled(config)) {
            return Collections.emptyList();
        }
        String dedupKey = projectRuleDedupKey(projectId, "CONTRACT_OVERDUE");
        if (isDuplicate(tenantId, dedupKey, dedupHours(config))) {
            return Collections.emptyList();
        }
        LocalDate today = LocalDate.now();
        List<CtContract> contracts = ctContractMapper.selectList(
                new LambdaQueryWrapper<CtContract>()
                        .eq(CtContract::getTenantId, tenantId)
                        .eq(CtContract::getProjectId, projectId)
                        .eq(CtContract::getContractStatus, "PERFORMING")
                        .lt(CtContract::getEndDate, today));
        if (!contracts.isEmpty()) {
            List<String> names = contracts.stream()
                    .map(c -> c.getContractCode() + "(" + c.getContractName() + ") 截止 " + c.getEndDate())
                    .toList();
            return List.of(buildAlert(tenantId, projectId, null,
                    "CONTRACT_OVERDUE", "HIGH",
                    alertMessage("CONTRACT_OVERDUE", String.join("；", names)),
                    config, null, null, dedupKey));
        }
        return Collections.emptyList();
    }

    // ──────────────────────────────────────────────
    // Rule 5: 付款超比例 — total paid > contract amount
    // ──────────────────────────────────────────────

    private List<AlertLog> evaluatePaymentExceedsRatio(Long tenantId, Long projectId,
                                                       Map<String, AlertRuleConfig> ruleConfigs) {
        AlertRuleConfig config = configFor(ruleConfigs, "PAYMENT_EXCEEDS_RATIO");
        if (!isEnabled(config)) {
            return Collections.emptyList();
        }
        // Get paid amounts grouped by contract
        List<PayRecord> records = payRecordMapper.selectList(
                new LambdaQueryWrapper<PayRecord>()
                        .eq(PayRecord::getTenantId, tenantId)
                        .eq(PayRecord::getProjectId, projectId)
                        .eq(PayRecord::getPayStatus, "SUCCESS"));
        Map<Long, BigDecimal> paidByContract = new HashMap<>();
        for (PayRecord r : records) {
            Long cid = r.getContractId();
            if (cid == null) continue;
            paidByContract.merge(cid, nvl(r.getPayAmount()), BigDecimal::add);
        }
        // M-009: Batch load contracts
        Map<Long, CtContract> contractMap = batchLoadContracts(paidByContract.keySet());
        for (Map.Entry<Long, BigDecimal> entry : paidByContract.entrySet()) {
            CtContract contract = contractMap.get(entry.getKey());
            if (contract == null) continue;
            BigDecimal contractAmount = nvl(contract.getContractAmount());
            if (contractAmount.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal ratio = entry.getValue().divide(contractAmount, 4, RoundingMode.HALF_UP);
            String dedupKey = contractRuleDedupKey(entry.getKey(), "PAYMENT_EXCEEDS_RATIO");
            if (isDuplicate(tenantId, dedupKey, dedupHours(config))) continue;
            if (ratio.compareTo(thresholdRatio(config)) > 0) {
                return List.of(buildAlert(tenantId, projectId, entry.getKey(),
                        "PAYMENT_EXCEEDS_RATIO", "HIGH",
                        alertMessage("PAYMENT_EXCEEDS_RATIO",
                                contract.getContractCode(),
                                contract.getContractName(),
                                entry.getValue().toPlainString(),
                                contractAmount.toPlainString(),
                                ratio.multiply(BigDecimal.valueOf(100))),
                        config, null, null, dedupKey));
            }
        }
        return Collections.emptyList();
    }

    // ──────────────────────────────────────────────
    // Rule 6: 质保金提前释放
    // ──────────────────────────────────────────────

    private List<AlertLog> evaluateWarrantyEarlyRelease(Long tenantId, Long projectId,
                                                        Map<String, AlertRuleConfig> ruleConfigs) {
        AlertRuleConfig config = configFor(ruleConfigs, "WARRANTY_EARLY_RELEASE");
        if (!isEnabled(config)) {
            return Collections.emptyList();
        }
        List<StlSettlement> settlements = stlSettlementMapper.selectList(
                new LambdaQueryWrapper<StlSettlement>()
                        .eq(StlSettlement::getTenantId, tenantId)
                        .eq(StlSettlement::getProjectId, projectId)
                        .eq(StlSettlement::getSettlementStatus, "FINALIZED")
                        .gt(StlSettlement::getWarrantyAmount, BigDecimal.ZERO));
        // M-009: Batch load contracts from settlement contractIds
        Set<Long> contractIds = settlements.stream()
                .map(StlSettlement::getContractId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, CtContract> contractMap = batchLoadContracts(contractIds);
        for (StlSettlement stl : settlements) {
            if (stl.getContractId() == null) continue;
            CtContract contract = contractMap.get(stl.getContractId());
            if (contract == null) continue;
            String dedupKey = contractRuleDedupKey(stl.getContractId(), "WARRANTY_EARLY_RELEASE");
            if (isDuplicate(tenantId, dedupKey, dedupHours(config))) continue;
            // Warranty is "early-released" if finalised but contract warranty period
            // (endDate) hasn't passed yet, or no endDate is set
            if (contract.getEndDate() != null && contract.getEndDate().isAfter(LocalDate.now())) {
                return List.of(buildAlert(tenantId, projectId, stl.getContractId(),
                        "WARRANTY_EARLY_RELEASE", "MEDIUM",
                        alertMessage("WARRANTY_EARLY_RELEASE",
                                contract.getContractCode(),
                                contract.getContractName(),
                                nvl(stl.getWarrantyAmount()),
                                stl.getFinalizedAt(),
                                contract.getEndDate()),
                        config, null, null, dedupKey));
            }
        }
        return Collections.emptyList();
    }

    // ──────────────────────────────────────────────
    // Rule 7: 合同到期 — endDate within 30 days
    // ──────────────────────────────────────────────

    private List<AlertLog> evaluateContractExpiring(Long tenantId, Long projectId,
                                                    Map<String, AlertRuleConfig> ruleConfigs) {
        AlertRuleConfig config = configFor(ruleConfigs, "CONTRACT_EXPIRING");
        if (!isEnabled(config)) {
            return Collections.emptyList();
        }
        String dedupKey = projectRuleDedupKey(projectId, "CONTRACT_EXPIRING");
        if (isDuplicate(tenantId, dedupKey, dedupHours(config))) {
            return Collections.emptyList();
        }
        LocalDate today = LocalDate.now();
        LocalDate threshold = today.plusDays(windowDays(config, DEFAULT_EXPIRING_DAYS));
        List<CtContract> contracts = ctContractMapper.selectList(
                new LambdaQueryWrapper<CtContract>()
                        .eq(CtContract::getTenantId, tenantId)
                        .eq(CtContract::getProjectId, projectId)
                        .eq(CtContract::getContractStatus, "PERFORMING")
                        .ge(CtContract::getEndDate, today)
                        .le(CtContract::getEndDate, threshold));
        if (!contracts.isEmpty()) {
            List<String> names = contracts.stream()
                    .map(c -> c.getContractCode() + "(" + c.getContractName() + ") " + c.getEndDate())
                    .toList();
            return List.of(buildAlert(tenantId, projectId, null,
                    "CONTRACT_EXPIRING", "LOW",
                    alertMessage("CONTRACT_EXPIRING",
                            windowDays(config, DEFAULT_EXPIRING_DAYS), String.join("；", names)),
                    config, null, null, dedupKey));
        }
        return Collections.emptyList();
    }

    // ──────────────────────────────────────────────
    // Rule 8: 变更未确认 — approved variation without owner confirmation
    // ──────────────────────────────────────────────

    private List<AlertLog> evaluateVariationUnconfirmed(Long tenantId, Long projectId,
                                                        Map<String, AlertRuleConfig> ruleConfigs) {
        AlertRuleConfig config = configFor(ruleConfigs, "VARIATION_UNCONFIRMED");
        if (!isEnabled(config)) {
            return Collections.emptyList();
        }
        String dedupKey = projectRuleDedupKey(projectId, "VARIATION_UNCONFIRMED");
        if (isDuplicate(tenantId, dedupKey, dedupHours(config))) {
            return Collections.emptyList();
        }
        LocalDateTime staleThreshold = LocalDateTime.now().minusDays(windowDays(config, DEFAULT_VARIATION_STALE_DAYS));
        List<VarOrder> vars = varOrderMapper.selectList(
                new LambdaQueryWrapper<VarOrder>()
                        .eq(VarOrder::getTenantId, tenantId)
                        .eq(VarOrder::getProjectId, projectId)
                        .eq(VarOrder::getApprovalStatus, "APPROVED")
                        .eq(VarOrder::getOwnerConfirmFlag, 0)
                        .lt(VarOrder::getCreatedAt, staleThreshold));
        if (!vars.isEmpty()) {
            List<String> names = vars.stream()
                    .map(v -> v.getVarCode() + "(" + v.getVarName() + ")")
                    .toList();
            return List.of(buildAlert(tenantId, projectId, null,
                    "VARIATION_UNCONFIRMED", "MEDIUM",
                    alertMessage("VARIATION_UNCONFIRMED",
                            windowDays(config, DEFAULT_VARIATION_STALE_DAYS), String.join("；", names)),
                    config, null, null, dedupKey));
        }
        return Collections.emptyList();
    }

    // ──────────────────────────────────────────────
    // Rule 9: 采购交期逾期 — deliveryDate before today and not completed/cancelled
    // ──────────────────────────────────────────────

    private List<AlertLog> evaluatePurchaseDeliveryOverdue(Long tenantId, Long projectId,
                                                           Map<String, AlertRuleConfig> ruleConfigs) {
        AlertRuleConfig config = configFor(ruleConfigs, "PURCHASE_DELIVERY_OVERDUE");
        if (!isEnabled(config)) {
            return Collections.emptyList();
        }
        MatPurchaseOrder order = purchaseOrderMapper.selectOne(
                new LambdaQueryWrapper<MatPurchaseOrder>()
                        .eq(MatPurchaseOrder::getTenantId, tenantId)
                        .eq(MatPurchaseOrder::getProjectId, projectId)
                        .lt(MatPurchaseOrder::getDeliveryDate, LocalDate.now())
                        .notIn(MatPurchaseOrder::getOrderStatus, List.of("COMPLETED", "CANCELLED"))
                        .orderByAsc(MatPurchaseOrder::getDeliveryDate)
                        .last("LIMIT 1"));
        if (order == null) {
            return Collections.emptyList();
        }
        String dedupKey = sourceRuleDedupKey("PURCHASE_ORDER", order.getId(), "PURCHASE_DELIVERY_OVERDUE");
        if (isDuplicate(tenantId, dedupKey, dedupHours(config))) {
            return Collections.emptyList();
        }
        return List.of(buildAlert(tenantId, projectId, order.getContractId(),
                "PURCHASE_DELIVERY_OVERDUE", "MEDIUM",
                alertMessage("PURCHASE_DELIVERY_OVERDUE", order.getOrderCode(), order.getDeliveryDate()),
                config, "PURCHASE_ORDER", order.getId(), dedupKey));
    }

    // ──────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────

    /**
     * Check whether an active alert of the same dedupKey already exists within the deduplication window.
     */
    private boolean isDuplicate(Long tenantId, String dedupKey, int dedupHours) {
        LocalDateTime since = LocalDateTime.now().minusHours(Math.max(dedupHours, 1));
        Long count = alertLogMapper.selectCount(
                new LambdaQueryWrapper<AlertLog>()
                        .eq(AlertLog::getTenantId, tenantId)
                        .eq(AlertLog::getDedupKey, dedupKey)
                        .in(AlertLog::getProcessStatus, List.of("OPEN", "PROCESSED"))
                        .ge(AlertLog::getTriggeredAt, since));
        return count != null && count > 0;
    }

    private AlertLog buildAlert(Long tenantId, Long projectId, Long contractId,
                                String ruleType, String severity, String message,
                                AlertRuleConfig config, String sourceType, Long sourceId, String dedupKey) {
        AlertLog alert = new AlertLog();
        alert.setTenantId(tenantId);
        alert.setProjectId(projectId);
        alert.setContractId(contractId);
        alert.setAlertDomain(resolveAlertDomain(ruleType, config));
        alert.setAlertCategory(resolveAlertCategory(ruleType, config));
        alert.setSourceType(sourceType);
        alert.setSourceId(sourceId);
        alert.setDedupKey(dedupKey);
        alert.setRuleType(ruleType);
        alert.setSeverity(resolveSeverity(severity, config));
        alert.setMessage(message);
        alert.setTriggeredAt(LocalDateTime.now());
        alert.setIsRead(0);
        alert.setProcessStatus("OPEN");
        alert.setDeletedFlag(0);
        return alert;
    }

    private String resolveAlertDomain(String ruleType, AlertRuleConfig config) {
        return StringUtils.hasText(config.getAlertDomain()) ? config.getAlertDomain() : alertDomain(ruleType);
    }

    private String resolveAlertCategory(String ruleType, AlertRuleConfig config) {
        return StringUtils.hasText(config.getAlertCategory()) ? config.getAlertCategory() : alertCategory(ruleType);
    }

    private String alertMessage(String ruleType, Object... args) {
        return String.format(ALERT_MESSAGE_TEMPLATES.get(ruleType), args);
    }

    private String alertDomain(String ruleType) {
        return switch (ruleType) {
            case "DYNAMIC_COST_EXCEEDS_TARGET", "MATERIAL_EXCEEDS_BUDGET",
                    "SUBCONTRACT_EXCEEDS_CONTRACT" -> "COST";
            case "CONTRACT_OVERDUE", "CONTRACT_EXPIRING", "WARRANTY_EARLY_RELEASE" -> "CONTRACT";
            case "PAYMENT_EXCEEDS_RATIO" -> "PAYMENT";
            case "VARIATION_UNCONFIRMED" -> "VARIATION";
            case "PURCHASE_DELIVERY_OVERDUE" -> "PURCHASE";
            default -> "OTHER";
        };
    }

    private String alertCategory(String ruleType) {
        return switch (ruleType) {
            case "DYNAMIC_COST_EXCEEDS_TARGET" -> "COST_DYNAMIC";
            case "MATERIAL_EXCEEDS_BUDGET" -> "COST_MATERIAL";
            case "SUBCONTRACT_EXCEEDS_CONTRACT" -> "COST_SUBCONTRACT";
            case "CONTRACT_OVERDUE", "CONTRACT_EXPIRING" -> "CONTRACT_TERM";
            case "WARRANTY_EARLY_RELEASE" -> "CONTRACT_WARRANTY";
            case "PAYMENT_EXCEEDS_RATIO" -> "PAYMENT_RATIO";
            case "VARIATION_UNCONFIRMED" -> "VARIATION_CONFIRM";
            case "PURCHASE_DELIVERY_OVERDUE" -> "PURCHASE_DELIVERY";
            default -> "OTHER";
        };
    }

    private void createAlertNotification(Long tenantId, Long projectId, AlertLog alert) {
        String title = getAlertTitle(alert.getRuleType());
        dispatchToSubscribedRecipients(tenantId, projectId, alert, subscription ->
                        Boolean.TRUE.equals(subscription.get("enabled")) && matchesSeverity(alert.getSeverity(), subscription),
                (userId, subscription) -> notificationDispatcher.dispatchAlertCreated(
                        tenantId, userId, alert, title, selectedChannels(subscription)));
    }

    private void dispatchStatusNotification(Long tenantId, AlertLog alert, String processStatus, String statusRemark) {
        dispatchToSubscribedRecipients(tenantId, alert.getProjectId(), alert, subscription ->
                        Boolean.TRUE.equals(subscription.get("enabled"))
                                && Boolean.TRUE.equals(subscription.get("notifyOnStatusChanged"))
                                && matchesSeverity(alert.getSeverity(), subscription),
                (userId, subscription) -> notificationDispatcher.dispatchStatusChanged(
                        tenantId, userId, alert, getStatusTitle(processStatus), statusRemark, selectedChannels(subscription)));
    }

    private void dispatchToSubscribedRecipients(Long tenantId, Long projectId, AlertLog alert,
                                                Predicate<Map<String, Object>> subscriptionFilter,
                                                SubscriptionDispatchAction action) {
        List<PmProjectMember> members = projectMemberMapper.selectList(
                new LambdaQueryWrapper<PmProjectMember>()
                        .eq(PmProjectMember::getTenantId, tenantId)
                        .eq(PmProjectMember::getProjectId, projectId)
                        .eq(PmProjectMember::getStatus, "ACTIVE")
                        .orderByAsc(PmProjectMember::getId));
        if (members.isEmpty()) {
            log.debug("No active alert recipients for projectId={}, skipping notification", projectId);
            return;
        }

        Set<Long> seen = new LinkedHashSet<>();
        for (PmProjectMember m : members) {
            if (m.getUserId() == null || !seen.add(m.getUserId())) {
                continue;
            }
            Map<String, Object> subscription = alertSubscriptionService.getEffectiveSubscription(
                    tenantId, m.getUserId(), List.of(m.getRoleCode()));
            @SuppressWarnings("unchecked")
            List<String> domains = (List<String>) subscription.get("domains");
            if (domains == null || !domains.contains(resolveAlertDomain(alert))) {
                continue;
            }
            if (!subscriptionFilter.test(subscription)) {
                continue;
            }
            action.dispatch(m.getUserId(), subscription);
        }
    }

    /**
     * Map alert rule type to human-readable Chinese title.
     */
    private String getAlertTitle(String ruleType) {
        return switch (ruleType) {
            case "DYNAMIC_COST_EXCEEDS_TARGET" -> "动态成本超目标预警";
            case "MATERIAL_EXCEEDS_BUDGET" -> "材料超预算预警";
            case "SUBCONTRACT_EXCEEDS_CONTRACT" -> "分包超合同预警";
            case "CONTRACT_OVERDUE" -> "合同超期预警";
            case "PAYMENT_EXCEEDS_RATIO" -> "付款超比例预警";
            case "WARRANTY_EARLY_RELEASE" -> "质保金提前释放预警";
            case "CONTRACT_EXPIRING" -> "合同到期预警";
            case "VARIATION_UNCONFIRMED" -> "变更未确认预警";
            case "PURCHASE_DELIVERY_OVERDUE" -> "采购交期逾期预警";
            default -> "项目预警";
        };
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private Map<String, AlertRuleConfig> loadRuleConfigs(Long tenantId) {
        return alertRuleConfigMapper.selectList(
                        new LambdaQueryWrapper<AlertRuleConfig>()
                                .eq(AlertRuleConfig::getTenantId, tenantId)
                                .eq(AlertRuleConfig::getDeletedFlag, 0))
                .stream()
                .collect(Collectors.toMap(AlertRuleConfig::getRuleType, item -> item, (left, right) -> left));
    }

    private AlertRuleConfig configFor(Map<String, AlertRuleConfig> ruleConfigs, String ruleType) {
        AlertRuleConfig config = ruleConfigs.get(ruleType);
        if (config != null) {
            return config;
        }
        AlertRuleConfig fallback = new AlertRuleConfig();
        fallback.setRuleType(ruleType);
        fallback.setAlertDomain(alertDomain(ruleType));
        fallback.setAlertCategory(alertCategory(ruleType));
        fallback.setEnabled(1);
        fallback.setDedupHours(DEFAULT_DEDUP_HOURS);
        if ("CONTRACT_EXPIRING".equals(ruleType)) {
            fallback.setWindowDays(DEFAULT_EXPIRING_DAYS);
        }
        if ("VARIATION_UNCONFIRMED".equals(ruleType)) {
            fallback.setWindowDays(DEFAULT_VARIATION_STALE_DAYS);
        }
        if (List.of("DYNAMIC_COST_EXCEEDS_TARGET", "MATERIAL_EXCEEDS_BUDGET",
                "SUBCONTRACT_EXCEEDS_CONTRACT", "PAYMENT_EXCEEDS_RATIO").contains(ruleType)) {
            fallback.setThresholdRatio(DEFAULT_THRESHOLD_RATIO);
        }
        return fallback;
    }

    private boolean isEnabled(AlertRuleConfig config) {
        return config == null || !Objects.equals(config.getEnabled(), 0);
    }

    private int dedupHours(AlertRuleConfig config) {
        return config != null && config.getDedupHours() != null ? config.getDedupHours() : DEFAULT_DEDUP_HOURS;
    }

    private int windowDays(AlertRuleConfig config, int fallback) {
        return config != null && config.getWindowDays() != null ? config.getWindowDays() : fallback;
    }

    private BigDecimal thresholdRatio(AlertRuleConfig config) {
        return config != null && config.getThresholdRatio() != null ? config.getThresholdRatio() : DEFAULT_THRESHOLD_RATIO;
    }

    private BigDecimal ratio(BigDecimal actual, BigDecimal base) {
        if (base == null || base.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return nvl(actual).divide(base, 4, RoundingMode.HALF_UP);
    }

    private String resolveSeverity(String fallbackSeverity, AlertRuleConfig config) {
        return StringUtils.hasText(config.getSeverityOverride()) ? config.getSeverityOverride() : fallbackSeverity;
    }

    private String projectRuleDedupKey(Long projectId, String ruleType) {
        return "P:" + projectId + ":R:" + ruleType;
    }

    private String contractRuleDedupKey(Long contractId, String ruleType) {
        return "C:" + contractId + ":R:" + ruleType;
    }

    private String sourceRuleDedupKey(String sourceType, Long sourceId, String ruleType) {
        return "S:" + sourceType + ":" + sourceId + ":R:" + ruleType;
    }

    // ──────────────────────────────────────────────
    // Query helpers for Controller
    // ──────────────────────────────────────────────

    public List<AlertLog> list(Long tenantId, Long projectId, String severity, Integer isRead) {
        return page(tenantId, 1, 1000, projectId, null, null, severity, isRead, null, null, null).getRecords();
    }

    public IPage<AlertLog> page(Long tenantId, long pageNum, long pageSize, Long projectId,
                                String ruleType, String alertDomain, String severity, Integer isRead,
                                LocalDateTime triggeredStart, LocalDateTime triggeredEnd, String processStatus) {
        Set<String> allowedDomains = accessScopeResolver.allowedDomains();
        if (!accessScopeResolver.isAdmin()) {
            if (projectId != null) {
                accessScopeResolver.assertProjectAccess(tenantId, projectId);
            }
            if (StringUtils.hasText(alertDomain) && !allowedDomains.contains(alertDomain)) {
                return emptyPage(pageNum, pageSize);
            }
            if (allowedDomains.isEmpty()) {
                return emptyPage(pageNum, pageSize);
            }
        }

        LambdaQueryWrapper<AlertLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlertLog::getTenantId, tenantId);
        if (projectId != null) {
            wrapper.eq(AlertLog::getProjectId, projectId);
        } else if (!accessScopeResolver.isAdmin()) {
            Set<Long> accessibleProjectIds = accessScopeResolver.accessibleProjectIds(tenantId);
            if (accessibleProjectIds.isEmpty()) {
                return emptyPage(pageNum, pageSize);
            }
            wrapper.in(AlertLog::getProjectId, accessibleProjectIds);
        }
        if (StringUtils.hasText(ruleType)) {
            wrapper.eq(AlertLog::getRuleType, ruleType);
        }
        if (StringUtils.hasText(alertDomain)) {
            Set<String> domainRuleTypes = ruleTypesForDomain(alertDomain);
            if (domainRuleTypes.isEmpty()) {
                wrapper.eq(AlertLog::getAlertDomain, alertDomain);
            } else {
                wrapper.and(w -> w.eq(AlertLog::getAlertDomain, alertDomain)
                        .or(legacy -> legacy.in(AlertLog::getRuleType, domainRuleTypes)
                                .and(blank -> blank.isNull(AlertLog::getAlertDomain)
                                        .or()
                                        .eq(AlertLog::getAlertDomain, ""))));
            }
        } else if (!accessScopeResolver.isAdmin()) {
            applyDomainScope(wrapper, allowedDomains);
        }
        if (StringUtils.hasText(severity)) {
            wrapper.eq(AlertLog::getSeverity, severity);
        }
        if (isRead != null) {
            wrapper.eq(AlertLog::getIsRead, isRead);
        }
        if (StringUtils.hasText(processStatus)) {
            wrapper.eq(AlertLog::getProcessStatus, processStatus);
        }
        if (triggeredStart != null) {
            wrapper.ge(AlertLog::getTriggeredAt, triggeredStart);
        }
        if (triggeredEnd != null) {
            wrapper.le(AlertLog::getTriggeredAt, triggeredEnd);
        }
        wrapper.orderByDesc(AlertLog::getTriggeredAt);
        return alertLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    private IPage<AlertLog> emptyPage(long pageNum, long pageSize) {
        return new Page<>(pageNum, pageSize);
    }

    private void applyDomainScope(LambdaQueryWrapper<AlertLog> wrapper, Set<String> allowedDomains) {
        Set<String> domainRuleTypes = ruleTypesForDomains(allowedDomains);
        wrapper.and(w -> w.in(AlertLog::getAlertDomain, allowedDomains)
                .or(legacy -> legacy.in(AlertLog::getRuleType, domainRuleTypes)
                        .and(blank -> blank.isNull(AlertLog::getAlertDomain)
                                .or()
                                .eq(AlertLog::getAlertDomain, ""))));
    }

    private Set<String> ruleTypesForDomain(String domain) {
        return ALERT_MESSAGE_TEMPLATES.keySet().stream()
                .filter(ruleType -> domain.equals(alertDomain(ruleType)))
                .collect(Collectors.toSet());
    }

    private Set<String> ruleTypesForDomains(Set<String> domains) {
        return ALERT_MESSAGE_TEMPLATES.keySet().stream()
                .filter(ruleType -> domains.contains(alertDomain(ruleType)))
                .collect(Collectors.toSet());
    }

    public boolean markRead(Long tenantId, Long alertId) {
        AlertLog alert = alertLogMapper.selectById(alertId);
        if (alert == null || !Objects.equals(alert.getTenantId(), tenantId)) {
            return false;
        }
        accessScopeResolver.assertAlertAccess(tenantId, alert);
        alert.setIsRead(1);
        return alertLogMapper.updateById(alert) > 0;
    }

    public boolean updateStatus(Long tenantId, Long alertId, String processStatus, String statusRemark) {
        if (!List.of("PROCESSED", "ARCHIVED", "INVALID").contains(processStatus)) {
            throw new BusinessException("ALERT_STATUS_INVALID", "预警状态不合法");
        }
        AlertLog alert = alertLogMapper.selectById(alertId);
        if (alert == null || !Objects.equals(alert.getTenantId(), tenantId)) {
            return false;
        }
        accessScopeResolver.assertAlertAccess(tenantId, alert);
        alert.setProcessStatus(processStatus);
        alert.setStatusRemark(StringUtils.hasText(statusRemark) ? statusRemark.trim() : null);
        if ("PROCESSED".equals(processStatus)) {
            alert.setProcessedAt(LocalDateTime.now());
        }
        if ("ARCHIVED".equals(processStatus) || "INVALID".equals(processStatus)) {
            alert.setArchivedAt(LocalDateTime.now());
        }
        boolean updated = alertLogMapper.updateById(alert) > 0;
        if (updated) {
            try {
                dispatchStatusNotification(tenantId, alert, processStatus, statusRemark);
            } catch (Exception e) {
                log.warn("Failed to create alert status notification: alertId={}, status={}",
                        alertId, processStatus, e);
            }
        }
        return updated;
    }

    private String getStatusTitle(String processStatus) {
        return switch (processStatus) {
            case "PROCESSED" -> "预警已处理";
            case "ARCHIVED" -> "预警已归档";
            case "INVALID" -> "预警已失效";
            default -> "预警状态变更";
        };
    }

    private boolean matchesSeverity(String alertSeverity, Map<String, Object> subscription) {
        String minSeverity = String.valueOf(subscription.getOrDefault("minSeverity", "LOW"));
        return severityLevel(alertSeverity) >= severityLevel(minSeverity);
    }

    @SuppressWarnings("unchecked")
    private Set<String> selectedChannels(Map<String, Object> subscription) {
        Object channels = subscription.get("channels");
        if (!(channels instanceof Collection<?> collection)) {
            return Set.of();
        }
        return collection.stream().map(String::valueOf).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private int severityLevel(String severity) {
        return switch (String.valueOf(severity).trim().toUpperCase(Locale.ROOT)) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            default -> 1;
        };
    }

    private String resolveAlertDomain(AlertLog alert) {
        return accessScopeResolver.alertDomain(alert);
    }

    @FunctionalInterface
    private interface SubscriptionDispatchAction {
        void dispatch(Long userId, Map<String, Object> subscription);
    }

    // M-009: Batch-load contracts to avoid N+1 in rule evaluation loops
    private Map<Long, CtContract> batchLoadContracts(Set<Long> contractIds) {
        if (contractIds.isEmpty()) return Collections.emptyMap();
        List<CtContract> contracts = ctContractMapper.selectBatchIds(contractIds);
        return contracts.stream().collect(Collectors.toMap(CtContract::getId, c -> c));
    }
}
