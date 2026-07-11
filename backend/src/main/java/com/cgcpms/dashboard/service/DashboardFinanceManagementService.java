package com.cgcpms.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.cgcpms.alert.entity.AlertLog;
import com.cgcpms.alert.mapper.AlertLogMapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.entity.CostSummary;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import com.cgcpms.cost.mapper.CostSummaryMapper;
import com.cgcpms.cost.service.CostSummaryService;
import com.cgcpms.cost.vo.CostProjectSummaryVO;
import com.cgcpms.cost.vo.CostSummaryVO;
import com.cgcpms.dashboard.vo.*;
import com.cgcpms.inventory.entity.MatStock;
import com.cgcpms.inventory.entity.MatWarehouse;
import com.cgcpms.inventory.mapper.MatStockMapper;
import com.cgcpms.inventory.mapper.MatWarehouseMapper;
import com.cgcpms.material.entity.MdMaterial;
import com.cgcpms.material.mapper.MdMaterialMapper;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.purchase.entity.MatPurchaseOrder;
import com.cgcpms.purchase.entity.MatPurchaseOrderItem;
import com.cgcpms.purchase.entity.MatPurchaseRequest;
import com.cgcpms.purchase.entity.MatPurchaseRequestItem;
import com.cgcpms.purchase.mapper.MatPurchaseOrderItemMapper;
import com.cgcpms.purchase.mapper.MatPurchaseOrderMapper;
import com.cgcpms.purchase.mapper.MatPurchaseRequestItemMapper;
import com.cgcpms.purchase.mapper.MatPurchaseRequestMapper;
import com.cgcpms.receipt.entity.MatReceipt;
import com.cgcpms.receipt.entity.MatReceiptItem;
import com.cgcpms.receipt.mapper.MatReceiptItemMapper;
import com.cgcpms.receipt.mapper.MatReceiptMapper;
import com.cgcpms.requisition.entity.MatRequisition;
import com.cgcpms.requisition.mapper.MatRequisitionMapper;
import com.cgcpms.settlement.entity.StlSettlement;
import com.cgcpms.settlement.mapper.StlSettlementMapper;
import com.cgcpms.subcontract.entity.SubMeasure;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import com.cgcpms.tech.entity.TechItem;
import com.cgcpms.tech.mapper.TechItemMapper;
import com.cgcpms.tech.vo.ChiefEngineerDashboardVO;
import com.cgcpms.system.entity.SysUser;
import com.cgcpms.system.mapper.SysUserMapper;
import com.cgcpms.variation.entity.VarOrder;
import com.cgcpms.variation.mapper.VarOrderMapper;
import com.cgcpms.workflow.WorkflowConstants;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.entity.WfTask;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.mapper.WfTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import com.cgcpms.common.util.DateTimeUtils;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class DashboardFinanceManagementService extends DashboardSharedSupport {

    private final ProjectAccessChecker projectAccessChecker;

    public DashboardFinanceManagementService(
            CostSummaryService costSummaryService,
            CostSummaryMapper costSummaryMapper,
            CostSubjectMapper costSubjectMapper,
            CostItemMapper costItemMapper,
            PmProjectMapper projectMapper,
            CtContractMapper ctContractMapper,
            WfTaskMapper wfTaskMapper,
            WfInstanceMapper wfInstanceMapper,
            PayRecordMapper payRecordMapper,
            StlSettlementMapper stlSettlementMapper,
            VarOrderMapper varOrderMapper,
            SubMeasureMapper subMeasureMapper,
            AlertLogMapper alertLogMapper,
            MatPurchaseRequestMapper purchaseRequestMapper,
            MatPurchaseRequestItemMapper purchaseRequestItemMapper,
            MatPurchaseOrderMapper purchaseOrderMapper,
            MatPurchaseOrderItemMapper purchaseOrderItemMapper,
            MatReceiptMapper receiptMapper,
            MatReceiptItemMapper receiptItemMapper,
            MatRequisitionMapper requisitionMapper,
            MatWarehouseMapper warehouseMapper,
            MatStockMapper stockMapper,
            TechItemMapper techItemMapper,
            MdPartnerMapper partnerMapper,
            MdMaterialMapper materialMapper,
            SysUserMapper userMapper,
            ProjectAccessChecker projectAccessChecker) {
        super(costSummaryService, costSummaryMapper, costSubjectMapper, costItemMapper, projectMapper, ctContractMapper, wfTaskMapper, wfInstanceMapper, payRecordMapper, stlSettlementMapper, varOrderMapper, subMeasureMapper, alertLogMapper, purchaseRequestMapper, purchaseRequestItemMapper, purchaseOrderMapper, purchaseOrderItemMapper, receiptMapper, receiptItemMapper, requisitionMapper, warehouseMapper, stockMapper, techItemMapper, partnerMapper, materialMapper, userMapper);
        this.projectAccessChecker = projectAccessChecker;
    }

    public FinanceDashboardVO getFinanceView(Long projectId) {
        Long tenantId = UserContext.getCurrentTenantId();

        if (projectId == null) {
            return getFinanceViewAllProjects(tenantId);
        }

        PmProject project = requireProject(tenantId, projectId);

        FinanceDashboardVO vo = new FinanceDashboardVO();
        vo.setProjectId(projectId.toString());
        vo.setProjectName(project.getProjectName());

        // Pay records
        List<PayRecord> payRecords = payRecordMapper.selectList(
                new LambdaQueryWrapper<PayRecord>()
                        .eq(PayRecord::getTenantId, tenantId)
                        .eq(PayRecord::getProjectId, projectId));

        // Pending payments
        List<PayRecord> pendingPayments = payRecords.stream()
                .filter(p -> !"SUCCESS".equals(p.getPayStatus()))
                .collect(Collectors.toList());
        BigDecimal pendingAmount = pendingPayments.stream()
                .map(p -> p.getPayAmount() != null ? p.getPayAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        vo.setPendingPaymentAmount(pendingAmount.toPlainString());
        vo.setPendingPaymentCount((long) pendingPayments.size());

        // Approved unpaid (SUCCESS status = paid, so approved unpaid = non-SUCCESS)
        vo.setApprovedUnpaidAmount(pendingAmount.toPlainString());

        // Over-ratio payments: SUM of excess where SUCCESS paid > contract_amount
        BigDecimal overRatioTotal = BigDecimal.ZERO;
        List<PayRecord> successRecords = payRecords.stream()
                .filter(p -> "SUCCESS".equals(p.getPayStatus()) && p.getContractId() != null)
                .collect(Collectors.toList());
        if (!successRecords.isEmpty()) {
            Map<Long, BigDecimal> paidByContract = new HashMap<>();
            for (PayRecord r : successRecords) {
                paidByContract.merge(r.getContractId(),
                        r.getPayAmount() != null ? r.getPayAmount() : BigDecimal.ZERO, BigDecimal::add);
            }
            List<CtContract> relatedContracts = ctContractMapper.selectList(
                    new LambdaQueryWrapper<CtContract>()
                            .eq(CtContract::getTenantId, tenantId)
                            .eq(CtContract::getProjectId, projectId)
                            .in(CtContract::getId, paidByContract.keySet()));
            Map<Long, CtContract> contractMap = relatedContracts.stream()
                    .collect(Collectors.toMap(CtContract::getId, c -> c));
            for (Map.Entry<Long, BigDecimal> entry : paidByContract.entrySet()) {
                CtContract contract = contractMap.get(entry.getKey());
                if (contract == null) continue;
                BigDecimal contractAmount = contract.getContractAmount() != null ? contract.getContractAmount() : BigDecimal.ZERO;
                if (contractAmount.compareTo(BigDecimal.ZERO) <= 0) continue;
                if (entry.getValue().compareTo(contractAmount) > 0) {
                    overRatioTotal = overRatioTotal.add(entry.getValue().subtract(contractAmount));
                }
            }
        }
        vo.setOverRatioAmount(overRatioTotal.toPlainString());

        // Warranty expiring: SUM contractAmount WHERE endDate within 30 days
        LocalDate today = LocalDate.now();
        LocalDate threshold = today.plusDays(30);
        BigDecimal warrantyExpiringTotal = ctContractMapper.selectList(
                new LambdaQueryWrapper<CtContract>()
                        .eq(CtContract::getTenantId, tenantId)
                        .eq(CtContract::getProjectId, projectId)
                        .eq(CtContract::getContractStatus, "PERFORMING")
                        .ge(CtContract::getEndDate, today)
                        .le(CtContract::getEndDate, threshold))
                .stream()
                .map(c -> c.getContractAmount() != null ? c.getContractAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        vo.setWarrantyExpiringAmount(warrantyExpiringTotal.toPlainString());

        // Detail lists
        vo.setPendingPayments(pendingPayments.stream().limit(20).map(p -> {
            DashboardPaymentItemVO item = new DashboardPaymentItemVO();
            item.setPayRecordId(String.valueOf(p.getId()));
            item.setContractId(p.getContractId() != null ? String.valueOf(p.getContractId()) : null);
            item.setPayAmount(p.getPayAmount() != null ? p.getPayAmount().toPlainString() : "0");
            item.setPayDate(p.getPayDate() != null ? p.getPayDate().toString() : null);
            item.setPayStatus(p.getPayStatus());
            item.setProjectId(String.valueOf(p.getProjectId()));
            item.setProjectName(project.getProjectName());
            return item;
        }).collect(Collectors.toList()));

        vo.setOverRatioPayments(Collections.emptyList());

        return vo;
    }

    // ========================================================================
    // 5. Management Dashboard (tenant-wide)
    // ========================================================================
    public ManagementDashboardVO getManagementView() {
        Long tenantId = UserContext.getCurrentTenantId();

        ManagementDashboardVO vo = new ManagementDashboardVO();

        // Active projects
        List<PmProject> activeProjects = projectAccessChecker.filterAccessible(projectMapper.selectList(
                new LambdaQueryWrapper<PmProject>()
                        .eq(PmProject::getTenantId, tenantId)
                        .eq(PmProject::getStatus, "ACTIVE")));
        vo.setActiveProjectCount((long) activeProjects.size());

        if (activeProjects.isEmpty()) {
            vo.setTotalContractAmount("0");
            vo.setTotalDynamicCost("0");
            vo.setTotalExpectedProfit("0");
            vo.setTotalPaidAmount("0");
            vo.setTotalPendingTaskCount(0L);
            vo.setTotalRiskCount(0L);
            vo.setProjectRankings(Collections.emptyList());
            vo.setMetricSources(Collections.emptyList());
            vo.setOverdueItems(Collections.emptyList());
            vo.setMajorRisks(Collections.emptyList());
            return vo;
        }

        // Aggregate totals across all active projects
        BigDecimal totalContractAmount = BigDecimal.ZERO;
        BigDecimal totalDynamicCost = BigDecimal.ZERO;
        BigDecimal totalExpectedProfit = BigDecimal.ZERO;
        BigDecimal totalPaidAmount = BigDecimal.ZERO;

        List<DashboardProjectSummaryVO> rankings = new ArrayList<>();

        // Batch load all project summaries to avoid N+1 per-project queries
        List<Long> projectIds = activeProjects.stream().map(PmProject::getId).collect(Collectors.toList());
        Set<Long> visibleProjectIds = new HashSet<>(projectIds);
        Map<Long, CostProjectSummaryVO> summaryMap = costSummaryService.getBatchProjectSummaries(tenantId, projectIds);

        for (PmProject project : activeProjects) {
            CostProjectSummaryVO summary = summaryMap.get(project.getId());
            if (summary == null) {
                log.warn("No summary found for project {}", project.getId());
                continue;
            }

            DashboardProjectSummaryVO rank = new DashboardProjectSummaryVO();
            rank.setProjectId(String.valueOf(project.getId()));
            rank.setProjectName(project.getProjectName());
            rank.setProjectCode(project.getProjectCode());
            rank.setStatus(project.getStatus());
            rank.setTargetCost(summary.getTargetCost());
            rank.setDynamicCost(summary.getDynamicCost());
            rank.setContractIncome(summary.getContractIncome());
            rank.setExpectedProfit(summary.getExpectedProfit());
            rank.setCostDeviation(summary.getCostDeviation());
            rank.setPaidAmount(summary.getPaidAmount());
            rank.setContractAmount(summary.getContractIncome());

            rankings.add(rank);

            totalContractAmount = totalContractAmount.add(
                    summary.getContractIncome() != null ? new BigDecimal(summary.getContractIncome()) : BigDecimal.ZERO);
            totalDynamicCost = totalDynamicCost.add(
                    summary.getDynamicCost() != null ? new BigDecimal(summary.getDynamicCost()) : BigDecimal.ZERO);
            totalExpectedProfit = totalExpectedProfit.add(
                    summary.getExpectedProfit() != null ? new BigDecimal(summary.getExpectedProfit()) : BigDecimal.ZERO);
            totalPaidAmount = totalPaidAmount.add(
                    summary.getPaidAmount() != null ? new BigDecimal(summary.getPaidAmount()) : BigDecimal.ZERO);
        }

        vo.setTotalContractAmount(totalContractAmount.toPlainString());
        vo.setTotalDynamicCost(totalDynamicCost.toPlainString());
        vo.setTotalExpectedProfit(totalExpectedProfit.toPlainString());
        vo.setTotalPaidAmount(totalPaidAmount.toPlainString());

        // Rank by expected profit descending
        rankings.sort(Comparator.comparing(
                r -> new BigDecimal(r.getExpectedProfit() != null ? r.getExpectedProfit() : "0"),
                Comparator.reverseOrder()));
        vo.setProjectRankings(rankings);
        vo.setMetricSources(rankings.stream()
                .map(this::toManagementMetricSource)
                .collect(Collectors.toList()));

        // Pending tasks count (tenant-wide)
        List<WfTask> allPending = wfTaskMapper.selectList(
                new LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getTenantId, tenantId)
                        .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING)
                        .orderByDesc(WfTask::getReceivedAt));
        Map<Long, WfInstance> pendingInstanceMap = batchLoadInstances(allPending);
        List<WfTask> visiblePending = allPending.stream()
                .filter(task -> {
                    WfInstance instance = pendingInstanceMap.get(task.getInstanceId());
                    return instance != null
                            && Objects.equals(tenantId, instance.getTenantId())
                            && instance.getProjectId() != null
                            && visibleProjectIds.contains(instance.getProjectId());
                })
                .collect(Collectors.toList());
        vo.setTotalPendingTaskCount((long) visiblePending.size());

        // Overdue items: pending tasks older than 7 days
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        List<DashboardTaskItemVO> overdueItems = visiblePending.stream()
                .filter(t -> t.getReceivedAt() != null && t.getReceivedAt().isBefore(sevenDaysAgo))
                .limit(20)
                .map(t -> {
                    DashboardTaskItemVO item = new DashboardTaskItemVO();
                    item.setTaskId(String.valueOf(t.getId()));
                    item.setInstanceId(String.valueOf(t.getInstanceId()));
                    item.setBusinessType(t.getBusinessType());
                    item.setTaskStatus(t.getTaskStatus());
                    if (t.getReceivedAt() != null) item.setReceivedAt(DateTimeUtils.DTF.format(t.getReceivedAt()));
                    return item;
                }).collect(Collectors.toList());
        vo.setOverdueItems(overdueItems);

        // Risks from alert_log: HIGH severity unread alerts tenant-wide
        List<AlertLog> highAlerts = alertLogMapper.selectList(
                new LambdaQueryWrapper<AlertLog>()
                        .eq(AlertLog::getTenantId, tenantId)
                        .in(AlertLog::getProjectId, visibleProjectIds)
                        .eq(AlertLog::getIsRead, 0)
                        .eq(AlertLog::getSeverity, "HIGH")
                        .orderByDesc(AlertLog::getTriggeredAt));
        vo.setTotalRiskCount((long) highAlerts.size());
        vo.setMajorRisks(highAlerts.stream().limit(10).map(this::toAlertItem).collect(Collectors.toList()));

        return vo;
    }

    // ========================================================================

    private ManagementDashboardVO.MetricSourceVO toManagementMetricSource(DashboardProjectSummaryVO project) {
        ManagementDashboardVO.MetricSourceVO source = new ManagementDashboardVO.MetricSourceVO();
        source.setProjectId(project.getProjectId());
        source.setProjectName(project.getProjectName());
        source.setSourceType("PROJECT_SUMMARY");
        source.setSourceId(project.getProjectId());
        source.setContractAmount(project.getContractAmount());
        source.setDynamicCost(project.getDynamicCost());
        source.setExpectedProfit(project.getExpectedProfit());
        source.setPaidAmount(project.getPaidAmount());
        return source;
    }

    private FinanceDashboardVO getFinanceViewAllProjects(Long tenantId) {
        List<PmProject> activeProjects = projectMapper.selectList(
                new LambdaQueryWrapper<PmProject>()
                        .eq(PmProject::getTenantId, tenantId)
                        .eq(PmProject::getStatus, "ACTIVE"));
        List<Long> projectIds = activeProjects.stream().map(PmProject::getId).collect(Collectors.toList());

        FinanceDashboardVO vo = new FinanceDashboardVO();
        vo.setProjectId(null);
        vo.setProjectName("全部项目");

        if (projectIds.isEmpty()) {
            vo.setPendingPaymentAmount("0");
            vo.setPendingPaymentCount(0L);
            vo.setApprovedUnpaidAmount("0");
            vo.setOverRatioAmount("0");
            vo.setWarrantyExpiringAmount("0");
            vo.setPendingPayments(Collections.emptyList());
            vo.setOverRatioPayments(Collections.emptyList());
            return vo;
        }

        // Pay records — tenant-wide
        List<PayRecord> allPayRecords = payRecordMapper.selectList(
                new LambdaQueryWrapper<PayRecord>()
                        .eq(PayRecord::getTenantId, tenantId)
                        .in(PayRecord::getProjectId, projectIds));

        // Pending payments
        List<PayRecord> pendingPayments = allPayRecords.stream()
                .filter(p -> !"SUCCESS".equals(p.getPayStatus()))
                .collect(Collectors.toList());
        BigDecimal pendingAmount = pendingPayments.stream()
                .map(p -> p.getPayAmount() != null ? p.getPayAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        vo.setPendingPaymentAmount(pendingAmount.toPlainString());
        vo.setPendingPaymentCount((long) pendingPayments.size());
        vo.setApprovedUnpaidAmount(pendingAmount.toPlainString());

        // Over-ratio payments
        BigDecimal overRatioTotal = BigDecimal.ZERO;
        List<PayRecord> successRecords = allPayRecords.stream()
                .filter(p -> "SUCCESS".equals(p.getPayStatus()) && p.getContractId() != null)
                .collect(Collectors.toList());
        if (!successRecords.isEmpty()) {
            Map<Long, BigDecimal> paidByContract = new HashMap<>();
            for (PayRecord r : successRecords) {
                paidByContract.merge(r.getContractId(),
                        r.getPayAmount() != null ? r.getPayAmount() : BigDecimal.ZERO, BigDecimal::add);
            }
            List<CtContract> relatedContracts = ctContractMapper.selectList(
                    new LambdaQueryWrapper<CtContract>()
                            .eq(CtContract::getTenantId, tenantId)
                            .in(CtContract::getProjectId, projectIds)
                            .in(CtContract::getId, paidByContract.keySet()));
            Map<Long, CtContract> contractMap = relatedContracts.stream()
                    .collect(Collectors.toMap(CtContract::getId, c -> c));
            for (Map.Entry<Long, BigDecimal> entry : paidByContract.entrySet()) {
                CtContract contract = contractMap.get(entry.getKey());
                if (contract == null) continue;
                BigDecimal contractAmount = contract.getContractAmount() != null ? contract.getContractAmount() : BigDecimal.ZERO;
                if (contractAmount.compareTo(BigDecimal.ZERO) <= 0) continue;
                if (entry.getValue().compareTo(contractAmount) > 0) {
                    overRatioTotal = overRatioTotal.add(entry.getValue().subtract(contractAmount));
                }
            }
        }
        vo.setOverRatioAmount(overRatioTotal.toPlainString());

        // Warranty expiring
        LocalDate today = LocalDate.now();
        LocalDate threshold = today.plusDays(30);
        BigDecimal warrantyExpiringTotal = ctContractMapper.selectList(
                new LambdaQueryWrapper<CtContract>()
                        .eq(CtContract::getTenantId, tenantId)
                        .in(CtContract::getProjectId, projectIds)
                        .eq(CtContract::getContractStatus, "PERFORMING")
                        .ge(CtContract::getEndDate, today)
                        .le(CtContract::getEndDate, threshold))
                .stream()
                .map(c -> c.getContractAmount() != null ? c.getContractAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        vo.setWarrantyExpiringAmount(warrantyExpiringTotal.toPlainString());

        // Detail lists
        Map<Long, String> projectNameMap = activeProjects.stream()
                .collect(Collectors.toMap(PmProject::getId, PmProject::getProjectName, (a, b) -> a));
        vo.setPendingPayments(pendingPayments.stream().limit(20).map(p -> {
            DashboardPaymentItemVO item = new DashboardPaymentItemVO();
            item.setPayRecordId(String.valueOf(p.getId()));
            item.setContractId(p.getContractId() != null ? String.valueOf(p.getContractId()) : null);
            item.setPayAmount(p.getPayAmount() != null ? p.getPayAmount().toPlainString() : "0");
            item.setPayDate(p.getPayDate() != null ? p.getPayDate().toString() : null);
            item.setPayStatus(p.getPayStatus());
            item.setProjectId(String.valueOf(p.getProjectId()));
            item.setProjectName(projectNameMap.getOrDefault(p.getProjectId(), ""));
            return item;
        }).collect(Collectors.toList()));
        vo.setOverRatioPayments(Collections.emptyList());

        return vo;
    }
}
