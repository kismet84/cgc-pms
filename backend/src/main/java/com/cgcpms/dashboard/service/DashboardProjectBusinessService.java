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
public class DashboardProjectBusinessService extends DashboardSharedSupport {

    private final ProjectAccessChecker projectAccessChecker;

    public DashboardProjectBusinessService(
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

    public ProjectManagerDashboardVO getProjectManagerView(Long projectId) {
        return getProjectManagerView(projectId, (String) null);
    }

    public ProjectManagerDashboardVO getProjectManagerView(Long projectId, String month) {
        Long tenantId = UserContext.getCurrentTenantId();
        YearMonth selectedMonth = parseDashboardMonth(month);

        if (projectId == null) {
            return getProjectManagerViewAllProjects(tenantId, selectedMonth);
        }

        PmProject project = requireProject(tenantId, projectId);
        projectAccessChecker.checkAccess(project, "查看项目经理驾驶舱");
        if (!"ACTIVE".equals(project.getStatus())) {
            throw new BusinessException("PROJECT_ACCESS_DENIED", "无权查看非进行中项目驾驶舱");
        }

        ProjectManagerDashboardVO vo = new ProjectManagerDashboardVO();
        vo.setProjectId(projectId.toString());
        vo.setProjectName(project.getProjectName());

        // Pending tasks for current user
        Long currentUserId = UserContext.getCurrentUserId();
        List<WfTask> pendingTasks = wfTaskMapper.selectList(
                new LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getTenantId, tenantId)
                        .eq(WfTask::getApproverId, currentUserId)
                        .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING)
                        .orderByDesc(WfTask::getReceivedAt));
        if (selectedMonth != null) {
            pendingTasks = pendingTasks.stream()
                    .filter(t -> t.getReceivedAt() != null
                            && !t.getReceivedAt().toLocalDate().isBefore(selectedMonth.atDay(1))
                            && !t.getReceivedAt().toLocalDate().isAfter(selectedMonth.atEndOfMonth()))
                    .collect(Collectors.toList());
        }

        // Enrich with instance info (batch to avoid N+1)
        Map<Long, WfInstance> instanceMap = batchLoadInstances(pendingTasks);
        Map<Long, String> projectNameMap = Map.of(projectId, project.getProjectName());
        List<DashboardTaskItemVO> taskItems = pendingTasks.stream()
                .filter(t -> isProjectManagerWorkflowTask(t, instanceMap.get(t.getInstanceId()))
                        && isWorkflowInstanceInProject(instanceMap.get(t.getInstanceId()), projectId))
                .map(t -> toTaskItem(t, instanceMap.get(t.getInstanceId()), projectNameMap))
                .collect(Collectors.toList());

        vo.setPendingTasks(taskItems);
        vo.setPendingTaskCount((long) taskItems.size());

        // Lagging projects: planned end date in the past, not completed
        // NOT filtered by month — current-state indicator
        List<DashboardProjectSummaryVO> lagging = Stream.of(project)
                .filter(p -> p.getPlannedEndDate() != null && p.getPlannedEndDate().isBefore(LocalDate.now())
                        && !"COMPLETED".equals(p.getStatus()))
                .map(this::toProjectSummary)
                .collect(Collectors.toList());
        vo.setLaggingProjects(lagging);
        vo.setLaggingProjectCount((long) lagging.size());

        // Pending approvals: wf_task count for the project (via wf_instance.projectId)
        List<WfInstance> projectInstances = wfInstanceMapper.selectList(
                new LambdaQueryWrapper<WfInstance>()
                        .eq(WfInstance::getTenantId, tenantId)
                        .eq(WfInstance::getProjectId, projectId));
        Set<Long> instanceIds = projectInstances.stream().map(WfInstance::getId).collect(Collectors.toSet());
        List<DashboardTaskItemVO> pendingApprovals = Collections.emptyList();
        long pendingApprovalCount = 0;
        if (!instanceIds.isEmpty()) {
            List<WfTask> projectPendingTasks = wfTaskMapper.selectList(
                    new LambdaQueryWrapper<WfTask>()
                            .eq(WfTask::getTenantId, tenantId)
                            .in(WfTask::getInstanceId, instanceIds)
                            .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING)
                            .orderByDesc(WfTask::getReceivedAt));
            if (selectedMonth != null) {
                projectPendingTasks = projectPendingTasks.stream()
                        .filter(t -> t.getReceivedAt() != null
                                && !t.getReceivedAt().toLocalDate().isBefore(selectedMonth.atDay(1))
                                && !t.getReceivedAt().toLocalDate().isAfter(selectedMonth.atEndOfMonth()))
                        .collect(Collectors.toList());
            }
            Map<Long, WfInstance> approvalInstanceMap = batchLoadInstances(projectPendingTasks);
            List<WfTask> projectManagerPendingTasks = projectPendingTasks.stream()
                    .filter(t -> isProjectManagerWorkflowTask(t, approvalInstanceMap.get(t.getInstanceId())))
                    .collect(Collectors.toList());
            pendingApprovals = projectManagerPendingTasks.stream()
                    .limit(10)
                    .map(t -> toTaskItem(t, approvalInstanceMap.get(t.getInstanceId()), projectNameMap))
                    .collect(Collectors.toList());
            pendingApprovalCount = projectManagerPendingTasks.size();
        }
        vo.setPendingApprovals(pendingApprovals);
        vo.setPendingApprovalCount(pendingApprovalCount);

        // Expiring contracts (end date within 30 days)
        LocalDate cutoff = LocalDate.now().plusDays(30);
        List<CtContract> expiringContracts = ctContractMapper.selectList(
                new LambdaQueryWrapper<CtContract>()
                        .eq(CtContract::getTenantId, tenantId)
                        .eq(CtContract::getProjectId, projectId)
                        .le(CtContract::getEndDate, cutoff)
                        .ge(CtContract::getEndDate, LocalDate.now())
                        .eq(CtContract::getContractStatus, "PERFORMING"));
        if (selectedMonth != null) {
            expiringContracts = expiringContracts.stream()
                    .filter(c -> c.getEndDate() != null
                            && !c.getEndDate().isBefore(selectedMonth.atDay(1))
                            && !c.getEndDate().isAfter(selectedMonth.atEndOfMonth()))
                    .collect(Collectors.toList());
        }
        vo.setExpiringContracts(expiringContracts.stream().map(this::toContractItem).collect(Collectors.toList()));
        vo.setExpiringContractCount((long) expiringContracts.size());

        return vo;
    }

    // ========================================================================
    // 2. Business Manager Dashboard
    // ========================================================================
    public BusinessManagerDashboardVO getBusinessManagerView(Long projectId) {
        Long tenantId = UserContext.getCurrentTenantId();

        if (projectId == null) {
            return getBusinessManagerViewAllProjects(tenantId);
        }

        PmProject project = requireProject(tenantId, projectId);

        BusinessManagerDashboardVO vo = new BusinessManagerDashboardVO();
        vo.setProjectId(projectId.toString());
        vo.setProjectName(project.getProjectName());

        // Contract totals
        List<CtContract> contracts = ctContractMapper.selectList(
                new LambdaQueryWrapper<CtContract>()
                        .eq(CtContract::getTenantId, tenantId)
                        .eq(CtContract::getProjectId, projectId));
        BigDecimal totalContractAmount = contracts.stream()
                .map(c -> c.getContractAmount() != null ? c.getContractAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCurrentAmount = contracts.stream()
                .map(c -> c.getCurrentAmount() != null ? c.getCurrentAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPaidAmount = contracts.stream()
                .map(c -> c.getPaidAmount() != null ? c.getPaidAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        vo.setTotalContractAmount(totalContractAmount.toPlainString());

        // Contract change amount = current - original
        BigDecimal changeAmount = totalCurrentAmount.subtract(totalContractAmount);
        vo.setContractChangeAmount(changeAmount.toPlainString());

        // Payment ratio
        if (totalContractAmount.compareTo(BigDecimal.ZERO) > 0) {
            vo.setPaidRatio(totalPaidAmount.divide(totalContractAmount, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP).toPlainString() + "%");
        } else {
            vo.setPaidRatio("0%");
        }

        // Settlement progress
        List<StlSettlement> settlements = stlSettlementMapper.selectList(
                new LambdaQueryWrapper<StlSettlement>()
                        .eq(StlSettlement::getTenantId, tenantId)
                        .eq(StlSettlement::getProjectId, projectId));
        long finalizedCount = settlements.stream().filter(s -> "FINALIZED".equals(s.getSettlementStatus())).count();
        vo.setSettlementProgress(settlements.isEmpty() ? "0/0" : finalizedCount + "/" + settlements.size());

        // Var order amount: SUM(approvedAmount) WHERE approvalStatus='APPROVED'
        BigDecimal varOrderTotal = varOrderMapper.selectList(
                new LambdaQueryWrapper<VarOrder>()
                        .eq(VarOrder::getTenantId, tenantId)
                        .eq(VarOrder::getProjectId, projectId)
                        .eq(VarOrder::getApprovalStatus, "APPROVED"))
                .stream()
                .map(v -> v.getApprovedAmount() != null ? v.getApprovedAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        vo.setVarOrderAmount(varOrderTotal.toPlainString());

        // Sub measure amount: SUM(approvedAmount) WHERE approvalStatus='APPROVED'
        BigDecimal subMeasureTotal = subMeasureMapper.selectList(
                new LambdaQueryWrapper<SubMeasure>()
                        .eq(SubMeasure::getTenantId, tenantId)
                        .eq(SubMeasure::getProjectId, projectId)
                        .eq(SubMeasure::getApprovalStatus, "APPROVED"))
                .stream()
                .map(s -> s.getApprovedAmount() != null ? s.getApprovedAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        vo.setSubMeasureAmount(subMeasureTotal.toPlainString());

        // Recent changes (top 5 contracts by currentAmount)
        vo.setRecentChanges(contracts.stream()
                .sorted(Comparator.comparing(c -> c.getCurrentAmount() != null ? c.getCurrentAmount() : BigDecimal.ZERO,
                        Comparator.reverseOrder()))
                .limit(5)
                .map(this::toContractItem)
                .collect(Collectors.toList()));

        // Settlement items
        vo.setSettlementItems(settlements.stream().map(s -> {
            DashboardProjectSummaryVO item = new DashboardProjectSummaryVO();
            item.setProjectId(String.valueOf(s.getProjectId()));
            item.setProjectName(project.getProjectName());
            item.setStatus(s.getSettlementStatus());
            return item;
        }).collect(Collectors.toList()));

        return vo;
    }

    private ProjectManagerDashboardVO getProjectManagerViewAllProjects(Long tenantId, YearMonth selectedMonth) {
        ProjectManagerDashboardVO vo = new ProjectManagerDashboardVO();
        vo.setProjectId(null);
        vo.setProjectName("全部项目");

        List<PmProject> activeProjects = projectAccessChecker.filterAccessible(projectMapper.selectList(
                new LambdaQueryWrapper<PmProject>()
                        .eq(PmProject::getTenantId, tenantId)
                        .eq(PmProject::getStatus, "ACTIVE")));
        Set<Long> visibleProjectIds = activeProjects.stream().map(PmProject::getId).collect(Collectors.toSet());

        // Pending tasks for current user (tenant-wide, already not scoped to project)
        Long currentUserId = UserContext.getCurrentUserId();
        List<WfTask> pendingTasks = wfTaskMapper.selectList(
                new LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getTenantId, tenantId)
                        .eq(WfTask::getApproverId, currentUserId)
                        .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING)
                        .orderByDesc(WfTask::getReceivedAt));
        if (selectedMonth != null) {
            pendingTasks = pendingTasks.stream()
                    .filter(t -> t.getReceivedAt() != null
                            && !t.getReceivedAt().toLocalDate().isBefore(selectedMonth.atDay(1))
                            && !t.getReceivedAt().toLocalDate().isAfter(selectedMonth.atEndOfMonth()))
                    .collect(Collectors.toList());
        }

        Map<Long, WfInstance> instanceMap = batchLoadInstances(pendingTasks);
        Map<Long, String> activeProjectNameMap = projectNameMap(activeProjects);
        List<DashboardTaskItemVO> taskItems = pendingTasks.stream()
                .filter(t -> isVisibleWorkflowTask(t, instanceMap, tenantId, visibleProjectIds))
                .filter(t -> isProjectManagerWorkflowTask(t, instanceMap.get(t.getInstanceId())))
                .map(t -> toTaskItem(t, instanceMap.get(t.getInstanceId()), activeProjectNameMap))
                .collect(Collectors.toList());
        vo.setPendingTasks(taskItems);
        vo.setPendingTaskCount((long) taskItems.size());

        // Lagging projects: all active projects with planned end date in the past
        // NOT filtered by month — current-state indicator
        List<DashboardProjectSummaryVO> lagging = activeProjects.stream()
                .filter(p -> p.getPlannedEndDate() != null && p.getPlannedEndDate().isBefore(LocalDate.now()))
                .map(this::toProjectSummary)
                .collect(Collectors.toList());
        vo.setLaggingProjects(lagging);
        vo.setLaggingProjectCount((long) lagging.size());

        // Pending approvals: tenant-wide pending tasks
        List<WfTask> allPendingApprovals = wfTaskMapper.selectList(
                new LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getTenantId, tenantId)
                        .eq(WfTask::getTaskStatus, WorkflowConstants.TASK_PENDING)
                        .orderByDesc(WfTask::getReceivedAt));
        if (selectedMonth != null) {
            allPendingApprovals = allPendingApprovals.stream()
                    .filter(t -> t.getReceivedAt() != null
                            && !t.getReceivedAt().toLocalDate().isBefore(selectedMonth.atDay(1))
                            && !t.getReceivedAt().toLocalDate().isAfter(selectedMonth.atEndOfMonth()))
                    .collect(Collectors.toList());
        }
        Map<Long, WfInstance> approvalInstanceMap = batchLoadInstances(allPendingApprovals);
        List<WfTask> projectManagerPendingApprovals = allPendingApprovals.stream()
                .filter(t -> isVisibleWorkflowTask(t, approvalInstanceMap, tenantId, visibleProjectIds))
                .filter(t -> isProjectManagerWorkflowTask(t, approvalInstanceMap.get(t.getInstanceId())))
                .collect(Collectors.toList());
        List<DashboardTaskItemVO> pendingApprovals = projectManagerPendingApprovals.stream()
                .limit(10)
                .map(t -> toTaskItem(t, approvalInstanceMap.get(t.getInstanceId()), activeProjectNameMap))
                .collect(Collectors.toList());
        vo.setPendingApprovals(pendingApprovals);
        vo.setPendingApprovalCount((long) projectManagerPendingApprovals.size());

        // Expiring contracts: tenant-wide within 30 days
        LocalDate cutoff = LocalDate.now().plusDays(30);
        List<CtContract> expiringContracts = visibleProjectIds.isEmpty()
                ? Collections.emptyList()
                : ctContractMapper.selectList(new LambdaQueryWrapper<CtContract>()
                .eq(CtContract::getTenantId, tenantId)
                .in(CtContract::getProjectId, visibleProjectIds)
                .le(CtContract::getEndDate, cutoff)
                .ge(CtContract::getEndDate, LocalDate.now())
                .eq(CtContract::getContractStatus, "PERFORMING"));
        if (selectedMonth != null) {
            expiringContracts = expiringContracts.stream()
                    .filter(c -> c.getEndDate() != null
                            && !c.getEndDate().isBefore(selectedMonth.atDay(1))
                            && !c.getEndDate().isAfter(selectedMonth.atEndOfMonth()))
                    .collect(Collectors.toList());
        }
        vo.setExpiringContracts(expiringContracts.stream().map(this::toContractItem).collect(Collectors.toList()));
        vo.setExpiringContractCount((long) expiringContracts.size());

        return vo;
    }

    private boolean isVisibleWorkflowTask(WfTask task,
                                          Map<Long, WfInstance> instanceMap,
                                          Long tenantId,
                                          Set<Long> visibleProjectIds) {
        WfInstance instance = instanceMap.get(task.getInstanceId());
        return instance != null
                && Objects.equals(tenantId, instance.getTenantId())
                && instance.getProjectId() != null
                && visibleProjectIds.contains(instance.getProjectId());
    }

    private BusinessManagerDashboardVO getBusinessManagerViewAllProjects(Long tenantId) {
        List<PmProject> activeProjects = projectMapper.selectList(
                new LambdaQueryWrapper<PmProject>()
                        .eq(PmProject::getTenantId, tenantId)
                        .eq(PmProject::getStatus, "ACTIVE"));
        List<Long> projectIds = activeProjects.stream().map(PmProject::getId).collect(Collectors.toList());

        BusinessManagerDashboardVO vo = new BusinessManagerDashboardVO();
        vo.setProjectId(null);
        vo.setProjectName("全部项目");

        if (projectIds.isEmpty()) {
            vo.setTotalContractAmount("0");
            vo.setContractChangeAmount("0");
            vo.setVarOrderAmount("0");
            vo.setSubMeasureAmount("0");
            vo.setPaidRatio("0%");
            vo.setSettlementProgress("0/0");
            vo.setRecentChanges(Collections.emptyList());
            vo.setSettlementItems(Collections.emptyList());
            return vo;
        }

        // Contract totals — tenant-wide across all active projects
        List<CtContract> allContracts = ctContractMapper.selectList(
                new LambdaQueryWrapper<CtContract>()
                        .eq(CtContract::getTenantId, tenantId)
                        .in(CtContract::getProjectId, projectIds));
        BigDecimal totalContractAmount = BigDecimal.ZERO;
        BigDecimal totalCurrentAmount = BigDecimal.ZERO;
        BigDecimal totalPaidAmount = BigDecimal.ZERO;
        for (CtContract c : allContracts) {
            totalContractAmount = totalContractAmount.add(c.getContractAmount() != null ? c.getContractAmount() : BigDecimal.ZERO);
            totalCurrentAmount = totalCurrentAmount.add(c.getCurrentAmount() != null ? c.getCurrentAmount() : BigDecimal.ZERO);
            totalPaidAmount = totalPaidAmount.add(c.getPaidAmount() != null ? c.getPaidAmount() : BigDecimal.ZERO);
        }
        vo.setTotalContractAmount(totalContractAmount.toPlainString());
        BigDecimal changeAmount = totalCurrentAmount.subtract(totalContractAmount);
        vo.setContractChangeAmount(changeAmount.toPlainString());
        if (totalContractAmount.compareTo(BigDecimal.ZERO) > 0) {
            vo.setPaidRatio(totalPaidAmount.divide(totalContractAmount, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP).toPlainString() + "%");
        } else {
            vo.setPaidRatio("0%");
        }

        // Settlement progress — tenant-wide
        List<StlSettlement> allSettlements = stlSettlementMapper.selectList(
                new LambdaQueryWrapper<StlSettlement>()
                        .eq(StlSettlement::getTenantId, tenantId)
                        .in(StlSettlement::getProjectId, projectIds));
        long finalizedCount = allSettlements.stream().filter(s -> "FINALIZED".equals(s.getSettlementStatus())).count();
        vo.setSettlementProgress(allSettlements.isEmpty() ? "0/0" : finalizedCount + "/" + allSettlements.size());

        // Var order amount — tenant-wide
        BigDecimal varOrderTotal = varOrderMapper.selectList(
                new LambdaQueryWrapper<VarOrder>()
                        .eq(VarOrder::getTenantId, tenantId)
                        .in(VarOrder::getProjectId, projectIds)
                        .eq(VarOrder::getApprovalStatus, "APPROVED"))
                .stream()
                .map(v -> v.getApprovedAmount() != null ? v.getApprovedAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        vo.setVarOrderAmount(varOrderTotal.toPlainString());

        // Sub measure amount — tenant-wide
        BigDecimal subMeasureTotal = subMeasureMapper.selectList(
                new LambdaQueryWrapper<SubMeasure>()
                        .eq(SubMeasure::getTenantId, tenantId)
                        .in(SubMeasure::getProjectId, projectIds)
                        .eq(SubMeasure::getApprovalStatus, "APPROVED"))
                .stream()
                .map(s -> s.getApprovedAmount() != null ? s.getApprovedAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        vo.setSubMeasureAmount(subMeasureTotal.toPlainString());

        // Recent changes: top 5 contracts by currentAmount
        vo.setRecentChanges(allContracts.stream()
                .sorted(Comparator.comparing(c -> c.getCurrentAmount() != null ? c.getCurrentAmount() : BigDecimal.ZERO,
                        Comparator.reverseOrder()))
                .limit(5)
                .map(this::toContractItem)
                .collect(Collectors.toList()));

        // Settlement items
        vo.setSettlementItems(allSettlements.stream().map(s -> {
            DashboardProjectSummaryVO item = new DashboardProjectSummaryVO();
            item.setProjectId(String.valueOf(s.getProjectId()));
            item.setStatus(s.getSettlementStatus());
            return item;
        }).collect(Collectors.toList()));

        return vo;
    }
}
