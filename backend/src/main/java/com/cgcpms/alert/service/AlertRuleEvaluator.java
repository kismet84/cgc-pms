package com.cgcpms.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.alert.auth.AlertAccessScopeResolver;
import com.cgcpms.alert.entity.AlertLog;
import com.cgcpms.alert.entity.AlertRuleConfig;
import com.cgcpms.alert.mapper.AlertLogMapper;
import com.cgcpms.alert.mapper.AlertRuleConfigMapper;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.entity.CostSummary;
import com.cgcpms.cost.mapper.CostSummaryMapper;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.purchase.entity.MatPurchaseOrder;
import com.cgcpms.purchase.mapper.MatPurchaseOrderMapper;
import com.cgcpms.receipt.entity.MatReceipt;
import com.cgcpms.receipt.mapper.MatReceiptMapper;
import com.cgcpms.inventory.mapper.MatStockTxnMapper;
import com.cgcpms.inventory.entity.MatStockTxn;
import com.cgcpms.settlement.entity.StlSettlement;
import com.cgcpms.settlement.mapper.StlSettlementMapper;
import com.cgcpms.subcontract.entity.SubMeasure;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import com.cgcpms.variation.entity.VarOrder;
import com.cgcpms.variation.mapper.VarOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Encapsulates the 9 alert rule evaluation logic, extracted from
 * {@link AlertEvaluationService} to keep individual file sizes manageable.
 */
@Component
@RequiredArgsConstructor
class AlertRuleEvaluator {

    private static final int DEFAULT_DEDUP_HOURS = 24;
    private static final int DEFAULT_EXPIRING_DAYS = 30;
    private static final int DEFAULT_VARIATION_STALE_DAYS = 30;
    private static final BigDecimal DEFAULT_THRESHOLD_RATIO = BigDecimal.ONE;

    private final AlertLogMapper alertLogMapper;
    private final AlertRuleConfigMapper alertRuleConfigMapper;
    private final CostSummaryMapper costSummaryMapper;
    private final CtContractMapper ctContractMapper;
    private final PayRecordMapper payRecordMapper;
    private final SubMeasureMapper subMeasureMapper;
    private final MatReceiptMapper matReceiptMapper;
    private final MatStockTxnMapper matStockTxnMapper;
    private final MatPurchaseOrderMapper purchaseOrderMapper;
    private final VarOrderMapper varOrderMapper;
    private final StlSettlementMapper stlSettlementMapper;

    List<AlertLog> evaluateDynamicCostExceedsTarget(Long tenantId, Long projectId,
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
                        AlertMessageTemplates.format("DYNAMIC_COST_EXCEEDS_TARGET",
                                dynamic.toPlainString(), target.toPlainString(),
                                deviation.toPlainString()),
                        config, null, null, dedupKey));
            }
        }
        return Collections.emptyList();
    }

    List<AlertLog> evaluateMaterialExceedsBudget(Long tenantId, Long projectId,
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
        Map<Long, BigDecimal> receiptByContract = new HashMap<>();
        for (MatReceipt r : receipts) {
            Long cid = r.getContractId();
            if (cid == null) continue;
            receiptByContract.merge(cid, nvl(r.getTotalAmount()), BigDecimal::add);
        }
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
                        AlertMessageTemplates.format("MATERIAL_EXCEEDS_BUDGET",
                                entry.getValue().toPlainString(),
                                contract.getContractCode(),
                                contract.getContractName(),
                                contractAmount.toPlainString()),
                        config, null, null, dedupKey));
            }
        }
        return Collections.emptyList();
    }

    List<AlertLog> evaluateSubcontractExceedsContract(Long tenantId, Long projectId,
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
                        AlertMessageTemplates.format("SUBCONTRACT_EXCEEDS_CONTRACT",
                                entry.getValue().toPlainString(),
                                contract.getContractCode(),
                                contract.getContractName(),
                                contractAmount.toPlainString()),
                        config, null, null, dedupKey));
            }
        }
        return Collections.emptyList();
    }

    List<AlertLog> evaluateContractOverdue(Long tenantId, Long projectId,
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
                    AlertMessageTemplates.format("CONTRACT_OVERDUE", String.join("；", names)),
                    config, null, null, dedupKey));
        }
        return Collections.emptyList();
    }

    List<AlertLog> evaluatePaymentExceedsRatio(Long tenantId, Long projectId,
                                                Map<String, AlertRuleConfig> ruleConfigs) {
        AlertRuleConfig config = configFor(ruleConfigs, "PAYMENT_EXCEEDS_RATIO");
        if (!isEnabled(config)) {
            return Collections.emptyList();
        }
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
        Map<Long, CtContract> contractMap = batchLoadContracts(paidByContract.keySet());
        for (Map.Entry<Long, BigDecimal> entry : paidByContract.entrySet()) {
            CtContract contract = contractMap.get(entry.getKey());
            if (contract == null) continue;
            BigDecimal contractAmount = nvl(contract.getContractAmount());
            if (contractAmount.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal ratioValue = entry.getValue().divide(contractAmount, 4, RoundingMode.HALF_UP);
            String dedupKey = contractRuleDedupKey(entry.getKey(), "PAYMENT_EXCEEDS_RATIO");
            if (isDuplicate(tenantId, dedupKey, dedupHours(config))) continue;
            if (ratioValue.compareTo(thresholdRatio(config)) > 0) {
                return List.of(buildAlert(tenantId, projectId, entry.getKey(),
                        "PAYMENT_EXCEEDS_RATIO", "HIGH",
                        AlertMessageTemplates.format("PAYMENT_EXCEEDS_RATIO",
                                contract.getContractCode(),
                                contract.getContractName(),
                                entry.getValue().toPlainString(),
                                contractAmount.toPlainString(),
                                ratioValue.multiply(BigDecimal.valueOf(100))),
                        config, null, null, dedupKey));
            }
        }
        return Collections.emptyList();
    }

    List<AlertLog> evaluateWarrantyEarlyRelease(Long tenantId, Long projectId,
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
            if (contract.getEndDate() != null && contract.getEndDate().isAfter(LocalDate.now())) {
                return List.of(buildAlert(tenantId, projectId, stl.getContractId(),
                        "WARRANTY_EARLY_RELEASE", "MEDIUM",
                        AlertMessageTemplates.format("WARRANTY_EARLY_RELEASE",
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

    List<AlertLog> evaluateContractExpiring(Long tenantId, Long projectId,
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
                    AlertMessageTemplates.format("CONTRACT_EXPIRING",
                            windowDays(config, DEFAULT_EXPIRING_DAYS), String.join("；", names)),
                    config, null, null, dedupKey));
        }
        return Collections.emptyList();
    }

    List<AlertLog> evaluateVariationUnconfirmed(Long tenantId, Long projectId,
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
                    AlertMessageTemplates.format("VARIATION_UNCONFIRMED",
                            windowDays(config, DEFAULT_VARIATION_STALE_DAYS), String.join("；", names)),
                    config, null, null, dedupKey));
        }
        return Collections.emptyList();
    }

    List<AlertLog> evaluatePurchaseDeliveryOverdue(Long tenantId, Long projectId,
                                                    Map<String, AlertRuleConfig> ruleConfigs) {
        AlertRuleConfig config = configFor(ruleConfigs, "PURCHASE_DELIVERY_OVERDUE");
        if (!isEnabled(config)) {
            return Collections.emptyList();
        }
        List<MatPurchaseOrder> overdueOrders = purchaseOrderMapper.selectList(
                new LambdaQueryWrapper<MatPurchaseOrder>()
                        .eq(MatPurchaseOrder::getTenantId, tenantId)
                        .eq(MatPurchaseOrder::getProjectId, projectId)
                        .lt(MatPurchaseOrder::getDeliveryDate, LocalDate.now())
                        .notIn(MatPurchaseOrder::getOrderStatus, List.of("COMPLETED", "CANCELLED"))
                        .orderByAsc(MatPurchaseOrder::getDeliveryDate));
        for (MatPurchaseOrder order : overdueOrders) {
            if (isReceiptAndStockInCompleted(tenantId, order.getId())) {
                continue;
            }
            String dedupKey = sourceRuleDedupKey("PURCHASE_ORDER", order.getId(), "PURCHASE_DELIVERY_OVERDUE");
            if (isDuplicate(tenantId, dedupKey, dedupHours(config))) {
                continue;
            }
            return List.of(buildAlert(tenantId, projectId, order.getContractId(),
                    "PURCHASE_DELIVERY_OVERDUE", "MEDIUM",
                    AlertMessageTemplates.format("PURCHASE_DELIVERY_OVERDUE", order.getOrderCode(), order.getDeliveryDate()),
                    config, "PURCHASE_ORDER", order.getId(), dedupKey));
        }
        return Collections.emptyList();
    }

    // ── rule config helpers ──

    Map<String, AlertRuleConfig> loadRuleConfigs(Long tenantId) {
        return alertRuleConfigMapper.selectList(
                        new LambdaQueryWrapper<AlertRuleConfig>()
                                .eq(AlertRuleConfig::getTenantId, tenantId)
                                .eq(AlertRuleConfig::getDeletedFlag, 0))
                .stream()
                .collect(Collectors.toMap(AlertRuleConfig::getRuleType, item -> item, (left, right) -> left));
    }

    AlertRuleConfig configFor(Map<String, AlertRuleConfig> ruleConfigs, String ruleType) {
        AlertRuleConfig config = ruleConfigs.get(ruleType);
        if (config != null) {
            return config;
        }
        AlertRuleConfig fallback = new AlertRuleConfig();
        fallback.setRuleType(ruleType);
        fallback.setAlertDomain(AlertMessageTemplates.domain(ruleType));
        fallback.setAlertCategory(AlertMessageTemplates.category(ruleType));
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

    boolean isEnabled(AlertRuleConfig config) {
        return config == null || !Objects.equals(config.getEnabled(), 0);
    }

    int dedupHours(AlertRuleConfig config) {
        return config != null && config.getDedupHours() != null ? config.getDedupHours() : DEFAULT_DEDUP_HOURS;
    }

    int windowDays(AlertRuleConfig config, int fallback) {
        return config != null && config.getWindowDays() != null ? config.getWindowDays() : fallback;
    }

    BigDecimal thresholdRatio(AlertRuleConfig config) {
        return config != null && config.getThresholdRatio() != null ? config.getThresholdRatio() : DEFAULT_THRESHOLD_RATIO;
    }

    // ── shared helpers ──

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

    private boolean isReceiptAndStockInCompleted(Long tenantId, Long orderId) {
        List<MatReceipt> approvedReceipts = matReceiptMapper.selectList(
                new LambdaQueryWrapper<MatReceipt>()
                        .eq(MatReceipt::getTenantId, tenantId)
                        .eq(MatReceipt::getOrderId, orderId)
                        .eq(MatReceipt::getApprovalStatus, "APPROVED"));
        for (MatReceipt receipt : approvedReceipts) {
            Long stockInCount = matStockTxnMapper.selectCount(
                    new LambdaQueryWrapper<MatStockTxn>()
                            .eq(MatStockTxn::getTenantId, tenantId)
                            .eq(MatStockTxn::getSourceType, "MAT_RECEIPT")
                            .eq(MatStockTxn::getSourceId, receipt.getId())
                            .eq(MatStockTxn::getTxnType, "IN"));
            if (stockInCount != null && stockInCount > 0) {
                return true;
            }
        }
        return false;
    }

    private AlertLog buildAlert(Long tenantId, Long projectId, Long contractId,
                                String ruleType, String severity, String message,
                                AlertRuleConfig config, String sourceType, Long sourceId, String dedupKey) {
        AlertLog alert = new AlertLog();
        alert.setTenantId(tenantId);
        alert.setProjectId(projectId);
        alert.setContractId(contractId);
        alert.setAlertDomain(StringUtils.hasText(config.getAlertDomain())
                ? config.getAlertDomain() : AlertMessageTemplates.domain(ruleType));
        alert.setAlertCategory(StringUtils.hasText(config.getAlertCategory())
                ? config.getAlertCategory() : AlertMessageTemplates.category(ruleType));
        alert.setSourceType(sourceType);
        alert.setSourceId(sourceId);
        alert.setDedupKey(dedupKey);
        alert.setRuleType(ruleType);
        alert.setSeverity(StringUtils.hasText(config.getSeverityOverride())
                ? config.getSeverityOverride() : severity);
        alert.setMessage(message);
        alert.setTriggeredAt(LocalDateTime.now());
        alert.setIsRead(0);
        alert.setProcessStatus("OPEN");
        alert.setDeletedFlag(0);
        return alert;
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private BigDecimal ratio(BigDecimal actual, BigDecimal base) {
        if (base == null || base.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return nvl(actual).divide(base, 4, RoundingMode.HALF_UP);
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

    private Map<Long, CtContract> batchLoadContracts(Set<Long> contractIds) {
        if (contractIds.isEmpty()) return Collections.emptyMap();
        List<CtContract> contracts = ctContractMapper.selectBatchIds(contractIds);
        return contracts.stream().collect(Collectors.toMap(CtContract::getId, c -> c));
    }
}
