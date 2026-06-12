package com.cgcpms.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.alert.entity.AlertLog;
import com.cgcpms.alert.mapper.AlertLogMapper;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.entity.CostSummary;
import com.cgcpms.cost.mapper.CostSummaryMapper;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

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
public class AlertEvaluationService {

    private static final int DEDUP_HOURS = 24;
    private static final int EXPIRING_DAYS = 30;
    private static final int VARIATION_STALE_DAYS = 30;

    private final AlertLogMapper alertLogMapper;
    private final PmProjectMapper projectMapper;
    private final CostSummaryMapper costSummaryMapper;
    private final CtContractMapper ctContractMapper;
    private final PayRecordMapper payRecordMapper;
    private final SubMeasureMapper subMeasureMapper;
    private final MatReceiptMapper matReceiptMapper;
    private final VarOrderMapper varOrderMapper;
    private final StlSettlementMapper stlSettlementMapper;

    // ──────────────────────────────────────────────
    // Scheduled entry point
    // ──────────────────────────────────────────────

    @Scheduled(cron = "0 */30 * * * ?")
    public void scheduledEvaluate() {
        log.info("Starting scheduled alert evaluation...");
        try {
            LambdaQueryWrapper<PmProject> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(PmProject::getStatus, "ACTIVE");
            List<PmProject> activeProjects = projectMapper.selectList(wrapper);

            log.info("Found {} active projects for alert evaluation", activeProjects.size());
            for (PmProject project : activeProjects) {
                try {
                    evaluateProject(project.getTenantId(), project.getId());
                } catch (Exception e) {
                    log.error("Failed to evaluate alerts for project {}", project.getId(), e);
                }
            }
        } catch (Exception e) {
            log.error("Scheduled alert evaluation failed", e);
        }
        log.info("Scheduled alert evaluation completed");
    }

    // ──────────────────────────────────────────────
    // Public API — manual trigger
    // ──────────────────────────────────────────────

    @Transactional
    public int batchEvaluate(Long tenantId) {
        log.info("Manual alert evaluation triggered for tenantId={}", tenantId);
        List<PmProject> activeProjects = projectMapper.selectList(
                new LambdaQueryWrapper<PmProject>()
                        .eq(PmProject::getTenantId, tenantId)
                        .eq(PmProject::getStatus, "ACTIVE"));
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

    @Transactional
    public int evaluateProject(Long tenantId, Long projectId) {
        List<AlertLog> alerts = new ArrayList<>();

        // Rule 1: 动态成本超目标
        alerts.addAll(evaluateDynamicCostExceedsTarget(tenantId, projectId));

        // Rule 2: 材料超预算
        alerts.addAll(evaluateMaterialExceedsBudget(tenantId, projectId));

        // Rule 3: 分包超合同
        alerts.addAll(evaluateSubcontractExceedsContract(tenantId, projectId));

        // Rule 4: 合同超期
        alerts.addAll(evaluateContractOverdue(tenantId, projectId));

        // Rule 5: 付款超比例
        alerts.addAll(evaluatePaymentExceedsRatio(tenantId, projectId));

        // Rule 6: 质保金提前释放
        alerts.addAll(evaluateWarrantyEarlyRelease(tenantId, projectId));

        // Rule 7: 合同到期
        alerts.addAll(evaluateContractExpiring(tenantId, projectId));

        // Rule 8: 变更未确认
        alerts.addAll(evaluateVariationUnconfirmed(tenantId, projectId));

        // Persist
        for (AlertLog alert : alerts) {
            alertLogMapper.insert(alert);
        }
        if (!alerts.isEmpty()) {
            log.info("Project {}: {} alert(s) generated", projectId, alerts.size());
        }
        return alerts.size();
    }

    // ──────────────────────────────────────────────
    // Rule 1: 动态成本超目标
    // ──────────────────────────────────────────────

    private List<AlertLog> evaluateDynamicCostExceedsTarget(Long tenantId, Long projectId) {
        if (isDuplicate(tenantId, projectId, "DYNAMIC_COST_EXCEEDS_TARGET")) {
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
            if (dynamic.compareTo(target) > 0) {
                BigDecimal deviation = dynamic.subtract(target);
                return List.of(buildAlert(tenantId, projectId,
                        "DYNAMIC_COST_EXCEEDS_TARGET", "HIGH",
                        String.format("动态成本 %s 超出目标成本 %s，偏差 %s",
                                dynamic.toPlainString(), target.toPlainString(),
                                deviation.toPlainString())));
            }
        }
        return Collections.emptyList();
    }

    // ──────────────────────────────────────────────
    // Rule 2: 材料超预算 — approved receipt total vs contract amount
    // ──────────────────────────────────────────────

    private List<AlertLog> evaluateMaterialExceedsBudget(Long tenantId, Long projectId) {
        if (isDuplicate(tenantId, projectId, "MATERIAL_EXCEEDS_BUDGET")) {
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
        for (Map.Entry<Long, BigDecimal> entry : receiptByContract.entrySet()) {
            CtContract contract = ctContractMapper.selectById(entry.getKey());
            if (contract == null) continue;
            BigDecimal contractAmount = nvl(contract.getContractAmount());
            if (contractAmount.compareTo(BigDecimal.ZERO) <= 0) continue;
            if (entry.getValue().compareTo(contractAmount) > 0) {
                return List.of(buildAlert(tenantId, projectId,
                        "MATERIAL_EXCEEDS_BUDGET", "MEDIUM",
                        String.format("材料验收金额 %s 超出合同 %s(%s) 金额 %s",
                                entry.getValue().toPlainString(),
                                contract.getContractCode(),
                                contract.getContractName(),
                                contractAmount.toPlainString())));
            }
        }
        return Collections.emptyList();
    }

    // ──────────────────────────────────────────────
    // Rule 3: 分包超合同 — approved measure total vs contract amount
    // ──────────────────────────────────────────────

    private List<AlertLog> evaluateSubcontractExceedsContract(Long tenantId, Long projectId) {
        if (isDuplicate(tenantId, projectId, "SUBCONTRACT_EXCEEDS_CONTRACT")) {
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
        for (Map.Entry<Long, BigDecimal> entry : measureByContract.entrySet()) {
            CtContract contract = ctContractMapper.selectById(entry.getKey());
            if (contract == null) continue;
            BigDecimal contractAmount = nvl(contract.getContractAmount());
            if (contractAmount.compareTo(BigDecimal.ZERO) <= 0) continue;
            if (entry.getValue().compareTo(contractAmount) > 0) {
                return List.of(buildAlert(tenantId, projectId,
                        "SUBCONTRACT_EXCEEDS_CONTRACT", "HIGH",
                        String.format("分包计量累计金额 %s 超出合同 %s(%s) 金额 %s",
                                entry.getValue().toPlainString(),
                                contract.getContractCode(),
                                contract.getContractName(),
                                contractAmount.toPlainString())));
            }
        }
        return Collections.emptyList();
    }

    // ──────────────────────────────────────────────
    // Rule 4: 合同超期
    // ──────────────────────────────────────────────

    private List<AlertLog> evaluateContractOverdue(Long tenantId, Long projectId) {
        if (isDuplicate(tenantId, projectId, "CONTRACT_OVERDUE")) {
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
            return List.of(buildAlert(tenantId, projectId,
                    "CONTRACT_OVERDUE", "HIGH",
                    String.format("以下合同已超期：%s", String.join("；", names))));
        }
        return Collections.emptyList();
    }

    // ──────────────────────────────────────────────
    // Rule 5: 付款超比例 — total paid > contract amount
    // ──────────────────────────────────────────────

    private List<AlertLog> evaluatePaymentExceedsRatio(Long tenantId, Long projectId) {
        if (isDuplicate(tenantId, projectId, "PAYMENT_EXCEEDS_RATIO")) {
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
        for (Map.Entry<Long, BigDecimal> entry : paidByContract.entrySet()) {
            CtContract contract = ctContractMapper.selectById(entry.getKey());
            if (contract == null) continue;
            BigDecimal contractAmount = nvl(contract.getContractAmount());
            if (contractAmount.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal ratio = entry.getValue().divide(contractAmount, 4, RoundingMode.HALF_UP);
            if (ratio.compareTo(BigDecimal.ONE) > 0) {
                return List.of(buildAlert(tenantId, projectId,
                        "PAYMENT_EXCEEDS_RATIO", "HIGH",
                        String.format("合同 %s(%s) 累计付款 %s 超过合同金额 %s（比例 %.0f%%）",
                                contract.getContractCode(),
                                contract.getContractName(),
                                entry.getValue().toPlainString(),
                                contractAmount.toPlainString(),
                                ratio.multiply(BigDecimal.valueOf(100)))));
            }
        }
        return Collections.emptyList();
    }

    // ──────────────────────────────────────────────
    // Rule 6: 质保金提前释放
    // ──────────────────────────────────────────────

    private List<AlertLog> evaluateWarrantyEarlyRelease(Long tenantId, Long projectId) {
        if (isDuplicate(tenantId, projectId, "WARRANTY_EARLY_RELEASE")) {
            return Collections.emptyList();
        }
        List<StlSettlement> settlements = stlSettlementMapper.selectList(
                new LambdaQueryWrapper<StlSettlement>()
                        .eq(StlSettlement::getTenantId, tenantId)
                        .eq(StlSettlement::getProjectId, projectId)
                        .eq(StlSettlement::getSettlementStatus, "FINALIZED")
                        .gt(StlSettlement::getWarrantyAmount, BigDecimal.ZERO));
        for (StlSettlement stl : settlements) {
            if (stl.getContractId() == null) continue;
            CtContract contract = ctContractMapper.selectById(stl.getContractId());
            if (contract == null) continue;
            // Warranty is "early-released" if finalised but contract warranty period
            // (endDate) hasn't passed yet, or no endDate is set
            if (contract.getEndDate() != null && contract.getEndDate().isAfter(LocalDate.now())) {
                return List.of(buildAlert(tenantId, projectId,
                        "WARRANTY_EARLY_RELEASE", "MEDIUM",
                        String.format("合同 %s(%s) 质保金 %.2f 已于 %s 定案，但保修期至 %s 尚未届满",
                                contract.getContractCode(),
                                contract.getContractName(),
                                nvl(stl.getWarrantyAmount()),
                                stl.getFinalizedAt(),
                                contract.getEndDate())));
            }
        }
        return Collections.emptyList();
    }

    // ──────────────────────────────────────────────
    // Rule 7: 合同到期 — endDate within 30 days
    // ──────────────────────────────────────────────

    private List<AlertLog> evaluateContractExpiring(Long tenantId, Long projectId) {
        if (isDuplicate(tenantId, projectId, "CONTRACT_EXPIRING")) {
            return Collections.emptyList();
        }
        LocalDate today = LocalDate.now();
        LocalDate threshold = today.plusDays(EXPIRING_DAYS);
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
            return List.of(buildAlert(tenantId, projectId,
                    "CONTRACT_EXPIRING", "LOW",
                    String.format("以下合同即将到期（%d天内）：%s",
                            EXPIRING_DAYS, String.join("；", names))));
        }
        return Collections.emptyList();
    }

    // ──────────────────────────────────────────────
    // Rule 8: 变更未确认 — approved variation without owner confirmation
    // ──────────────────────────────────────────────

    private List<AlertLog> evaluateVariationUnconfirmed(Long tenantId, Long projectId) {
        if (isDuplicate(tenantId, projectId, "VARIATION_UNCONFIRMED")) {
            return Collections.emptyList();
        }
        LocalDateTime staleThreshold = LocalDateTime.now().minusDays(VARIATION_STALE_DAYS);
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
            return List.of(buildAlert(tenantId, projectId,
                    "VARIATION_UNCONFIRMED", "MEDIUM",
                    String.format("以下变更签证已审批超%d天仍未获甲方确认：%s",
                            VARIATION_STALE_DAYS, String.join("；", names))));
        }
        return Collections.emptyList();
    }

    // ──────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────

    /**
     * Check whether an unread alert of the same rule/project already exists
     * within the deduplication window.
     */
    private boolean isDuplicate(Long tenantId, Long projectId, String ruleType) {
        LocalDateTime since = LocalDateTime.now().minusHours(DEDUP_HOURS);
        Long count = alertLogMapper.selectCount(
                new LambdaQueryWrapper<AlertLog>()
                        .eq(AlertLog::getTenantId, tenantId)
                        .eq(AlertLog::getProjectId, projectId)
                        .eq(AlertLog::getRuleType, ruleType)
                        .eq(AlertLog::getIsRead, 0)
                        .ge(AlertLog::getTriggeredAt, since));
        return count != null && count > 0;
    }

    private AlertLog buildAlert(Long tenantId, Long projectId,
                                 String ruleType, String severity, String message) {
        AlertLog alert = new AlertLog();
        alert.setTenantId(tenantId);
        alert.setProjectId(projectId);
        alert.setRuleType(ruleType);
        alert.setSeverity(severity);
        alert.setMessage(message);
        alert.setTriggeredAt(LocalDateTime.now());
        alert.setIsRead(0);
        alert.setDeletedFlag(0);
        return alert;
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    // ──────────────────────────────────────────────
    // Query helpers for Controller
    // ──────────────────────────────────────────────

    public List<AlertLog> list(Long tenantId, Long projectId, String severity, Integer isRead) {
        LambdaQueryWrapper<AlertLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlertLog::getTenantId, tenantId);
        if (projectId != null) {
            wrapper.eq(AlertLog::getProjectId, projectId);
        }
        if (severity != null && !severity.isEmpty()) {
            wrapper.eq(AlertLog::getSeverity, severity);
        }
        if (isRead != null) {
            wrapper.eq(AlertLog::getIsRead, isRead);
        }
        wrapper.orderByDesc(AlertLog::getTriggeredAt);
        return alertLogMapper.selectList(wrapper);
    }

    public boolean markRead(Long tenantId, Long alertId) {
        AlertLog alert = alertLogMapper.selectById(alertId);
        if (alert == null || !Objects.equals(alert.getTenantId(), tenantId)) {
            return false;
        }
        alert.setIsRead(1);
        return alertLogMapper.updateById(alert) > 0;
    }
}
