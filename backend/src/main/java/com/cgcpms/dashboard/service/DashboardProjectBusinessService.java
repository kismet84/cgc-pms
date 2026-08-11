package com.cgcpms.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.entity.CtContractChange;
import com.cgcpms.contract.mapper.CtContractChangeMapper;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.dashboard.vo.*;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.settlement.entity.StlSettlement;
import com.cgcpms.settlement.mapper.StlSettlementMapper;
import com.cgcpms.subcontract.entity.SubMeasure;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import com.cgcpms.variation.entity.VarOrder;
import com.cgcpms.variation.mapper.VarOrderMapper;
import com.cgcpms.workflow.WorkflowConstants;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.entity.WfTask;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.mapper.WfTaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.cgcpms.dashboard.service.DashboardViewSupport.*;

@Service
@Transactional(readOnly = true)
public class DashboardProjectBusinessService {

    private final PmProjectMapper projectMapper;
    private final CtContractMapper ctContractMapper;
    private final WfTaskMapper wfTaskMapper;
    private final WfInstanceMapper wfInstanceMapper;
    private final StlSettlementMapper stlSettlementMapper;
    private final VarOrderMapper varOrderMapper;
    private final SubMeasureMapper subMeasureMapper;
    private final ProjectAccessChecker projectAccessChecker;
    private final CtContractChangeMapper ctContractChangeMapper;

    public DashboardProjectBusinessService(
            PmProjectMapper projectMapper,
            CtContractMapper ctContractMapper,
            CtContractChangeMapper ctContractChangeMapper,
            WfTaskMapper wfTaskMapper,
            WfInstanceMapper wfInstanceMapper,
            StlSettlementMapper stlSettlementMapper,
            VarOrderMapper varOrderMapper,
            SubMeasureMapper subMeasureMapper,
            ProjectAccessChecker projectAccessChecker) {
        this.projectMapper = projectMapper;
        this.ctContractMapper = ctContractMapper;
        this.wfTaskMapper = wfTaskMapper;
        this.wfInstanceMapper = wfInstanceMapper;
        this.stlSettlementMapper = stlSettlementMapper;
        this.varOrderMapper = varOrderMapper;
        this.subMeasureMapper = subMeasureMapper;
        this.projectAccessChecker = projectAccessChecker;
        this.ctContractChangeMapper = ctContractChangeMapper;
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

        // Lagging projects as of the selected report period.
        LocalDate periodCutoff = selectedMonth == null
                ? LocalDate.now()
                : selectedMonth.atEndOfMonth().plusDays(1);
        List<DashboardProjectSummaryVO> lagging = Stream.of(project)
                .filter(p -> p.getPlannedEndDate() != null && p.getPlannedEndDate().isBefore(periodCutoff)
                        && !"COMPLETED".equals(p.getStatus()))
                .map(DashboardViewSupport::toProjectSummary)
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
        LocalDate windowStart = selectedMonth == null ? LocalDate.now() : selectedMonth.atDay(1);
        LocalDate cutoff = selectedMonth == null ? windowStart.plusDays(30) : selectedMonth.atEndOfMonth();
        List<CtContract> expiringContracts = ctContractMapper.selectList(
                new LambdaQueryWrapper<CtContract>()
                        .eq(CtContract::getTenantId, tenantId)
                        .eq(CtContract::getProjectId, projectId)
                        .le(CtContract::getEndDate, cutoff)
                        .ge(CtContract::getEndDate, windowStart)
                        .eq(CtContract::getContractStatus, "PERFORMING"));
        vo.setExpiringContracts(expiringContracts.stream().map(DashboardViewSupport::toContractItem).collect(Collectors.toList()));
        vo.setExpiringContractCount((long) expiringContracts.size());

        return vo;
    }

    // ========================================================================
    // 2. Business Manager Dashboard
    // ========================================================================
    public BusinessManagerDashboardVO getBusinessManagerView(Long projectId) {
        return getBusinessManagerView(projectId, null);
    }

    public BusinessManagerDashboardVO getBusinessManagerView(Long projectId, String month) {
        Long tenantId = UserContext.getCurrentTenantId();
        YearMonth selectedMonth = parseDashboardMonth(month);

        if (projectId == null) {
            return getBusinessManagerViewAllProjects(tenantId, selectedMonth);
        }

        PmProject project = requireProject(tenantId, projectId);
        projectAccessChecker.checkAccess(project, "查看商务经理驾驶舱");

        BusinessManagerDashboardVO vo = new BusinessManagerDashboardVO();
        vo.setProjectId(projectId.toString());
        vo.setProjectName(project.getProjectName());

        // Contract totals
        List<CtContract> contracts = ctContractMapper.selectList(
                new LambdaQueryWrapper<CtContract>()
                        .eq(CtContract::getTenantId, tenantId)
                        .eq(CtContract::getProjectId, projectId));
        contracts = contracts.stream()
                .filter(contract -> existedBy(contract.getSignedDate(), contract.getCreatedAt(), selectedMonth))
                .collect(Collectors.toList());
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
        settlements = settlements.stream()
                .filter(settlement -> existedBy(null, settlement.getCreatedAt(), selectedMonth))
                .collect(Collectors.toList());
        long finalizedCount = settlements.stream()
                .filter(s -> "FINALIZED".equals(s.getSettlementStatus()))
                .filter(s -> selectedMonth == null || (s.getFinalizedAt() != null
                        && !s.getFinalizedAt().toLocalDate().isAfter(selectedMonth.atEndOfMonth())))
                .count();
        vo.setSettlementProgress(settlements.isEmpty() ? "0/0" : finalizedCount + "/" + settlements.size());

        // Var order amount: SUM(approvedAmount) WHERE approvalStatus='APPROVED'
        BigDecimal varOrderTotal = varOrderMapper.selectList(
                new LambdaQueryWrapper<VarOrder>()
                        .eq(VarOrder::getTenantId, tenantId)
                        .eq(VarOrder::getProjectId, projectId)
                        .eq(VarOrder::getApprovalStatus, "APPROVED"))
                .stream()
                .filter(v -> selectedMonth == null || (v.getEventDate() != null
                        && !v.getEventDate().isAfter(selectedMonth.atEndOfMonth())))
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
                .filter(s -> selectedMonth == null || (s.getMeasureDate() != null
                        && !s.getMeasureDate().isAfter(selectedMonth.atEndOfMonth())))
                .map(s -> s.getApprovedAmount() != null ? s.getApprovedAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        vo.setSubMeasureAmount(subMeasureTotal.toPlainString());

        vo.setRecentChanges(recentChangedContracts(tenantId, List.of(projectId), selectedMonth,
                Map.of(projectId, project.getProjectName())));

        // Settlement items
        vo.setSettlementItems(settlements.stream().map(s -> {
            DashboardProjectSummaryVO item = new DashboardProjectSummaryVO();
            item.setProjectId(String.valueOf(s.getProjectId()));
            item.setProjectName(project.getProjectName());
            item.setProjectCode(project.getProjectCode());
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

        LocalDate periodCutoff = selectedMonth == null
                ? LocalDate.now()
                : selectedMonth.atEndOfMonth().plusDays(1);
        List<DashboardProjectSummaryVO> lagging = activeProjects.stream()
                .filter(p -> p.getPlannedEndDate() != null && p.getPlannedEndDate().isBefore(periodCutoff))
                .map(DashboardViewSupport::toProjectSummary)
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
        LocalDate windowStart = selectedMonth == null ? LocalDate.now() : selectedMonth.atDay(1);
        LocalDate cutoff = selectedMonth == null ? windowStart.plusDays(30) : selectedMonth.atEndOfMonth();
        List<CtContract> expiringContracts = visibleProjectIds.isEmpty()
                ? Collections.emptyList()
                : ctContractMapper.selectList(new LambdaQueryWrapper<CtContract>()
                .eq(CtContract::getTenantId, tenantId)
                .in(CtContract::getProjectId, visibleProjectIds)
                .le(CtContract::getEndDate, cutoff)
                .ge(CtContract::getEndDate, windowStart)
                .eq(CtContract::getContractStatus, "PERFORMING"));
        vo.setExpiringContracts(expiringContracts.stream().map(DashboardViewSupport::toContractItem).collect(Collectors.toList()));
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

    private BusinessManagerDashboardVO getBusinessManagerViewAllProjects(Long tenantId, YearMonth selectedMonth) {
        List<PmProject> activeProjects = projectAccessChecker.filterAccessible(projectMapper.selectList(
                new LambdaQueryWrapper<PmProject>()
                        .eq(PmProject::getTenantId, tenantId)
                        .eq(PmProject::getStatus, "ACTIVE")));
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
        allContracts = allContracts.stream()
                .filter(contract -> existedBy(contract.getSignedDate(), contract.getCreatedAt(), selectedMonth))
                .collect(Collectors.toList());
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
        allSettlements = allSettlements.stream()
                .filter(settlement -> existedBy(null, settlement.getCreatedAt(), selectedMonth))
                .collect(Collectors.toList());
        long finalizedCount = allSettlements.stream()
                .filter(s -> "FINALIZED".equals(s.getSettlementStatus()))
                .filter(s -> selectedMonth == null || (s.getFinalizedAt() != null
                        && !s.getFinalizedAt().toLocalDate().isAfter(selectedMonth.atEndOfMonth())))
                .count();
        vo.setSettlementProgress(allSettlements.isEmpty() ? "0/0" : finalizedCount + "/" + allSettlements.size());

        // Var order amount — tenant-wide
        BigDecimal varOrderTotal = varOrderMapper.selectList(
                new LambdaQueryWrapper<VarOrder>()
                        .eq(VarOrder::getTenantId, tenantId)
                        .in(VarOrder::getProjectId, projectIds)
                        .eq(VarOrder::getApprovalStatus, "APPROVED"))
                .stream()
                .filter(v -> selectedMonth == null || (v.getEventDate() != null
                        && !v.getEventDate().isAfter(selectedMonth.atEndOfMonth())))
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
                .filter(s -> selectedMonth == null || (s.getMeasureDate() != null
                        && !s.getMeasureDate().isAfter(selectedMonth.atEndOfMonth())))
                .map(s -> s.getApprovedAmount() != null ? s.getApprovedAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        vo.setSubMeasureAmount(subMeasureTotal.toPlainString());

        Map<Long, String> projectNames = projectNameMap(activeProjects);
        Map<Long, String> projectCodes = activeProjects.stream()
                .collect(Collectors.toMap(PmProject::getId, PmProject::getProjectCode));
        vo.setRecentChanges(recentChangedContracts(tenantId, projectIds, selectedMonth, projectNames));

        // Settlement items
        vo.setSettlementItems(allSettlements.stream().map(s -> {
            DashboardProjectSummaryVO item = new DashboardProjectSummaryVO();
            item.setProjectId(String.valueOf(s.getProjectId()));
            item.setProjectName(projectNames.get(s.getProjectId()));
            item.setProjectCode(projectCodes.get(s.getProjectId()));
            item.setStatus(s.getSettlementStatus());
            return item;
        }).collect(Collectors.toList()));

        return vo;
    }

    private List<DashboardContractItemVO> recentChangedContracts(Long tenantId, List<Long> projectIds,
                                                                   YearMonth selectedMonth,
                                                                   Map<Long, String> projectNames) {
        LocalDateTime nextMonthStart = selectedMonth == null ? null : selectedMonth.plusMonths(1).atDay(1).atStartOfDay();
        Map<Long, CtContractChange> latestByContract = ctContractChangeMapper.selectList(
                        new LambdaQueryWrapper<CtContractChange>()
                                .eq(CtContractChange::getTenantId, tenantId)
                                .in(CtContractChange::getProjectId, projectIds)
                                .eq(CtContractChange::getApprovalStatus, "APPROVED")
                                .eq(CtContractChange::getEffectiveFlag, 1))
                .stream()
                .filter(change -> changeTime(change) != null
                        && (nextMonthStart == null || changeTime(change).isBefore(nextMonthStart)))
                .collect(Collectors.toMap(CtContractChange::getContractId, change -> change,
                        (left, right) -> changeComparator().compare(left, right) >= 0 ? left : right));
        if (latestByContract.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, CtContract> contracts = ctContractMapper.selectList(new LambdaQueryWrapper<CtContract>()
                        .eq(CtContract::getTenantId, tenantId)
                        .in(CtContract::getId, latestByContract.keySet()))
                .stream().collect(Collectors.toMap(CtContract::getId, contract -> contract));
        return latestByContract.values().stream()
                .sorted(changeComparator().reversed())
                .map(change -> contracts.get(change.getContractId()))
                .filter(Objects::nonNull)
                .limit(5)
                .map(contract -> {
                    DashboardContractItemVO item = toContractItem(contract);
                    item.setProjectName(projectNames.get(contract.getProjectId()));
                    return item;
                })
                .collect(Collectors.toList());
    }

    private PmProject requireProject(Long tenantId, Long projectId) {
        if (projectId == null) {
            throw new BusinessException("PROJECT_NOT_FOUND", "请指定项目");
        }
        PmProject project = projectMapper.selectById(projectId);
        if (project == null || !Objects.equals(project.getTenantId(), tenantId)) {
            throw new BusinessException("PROJECT_NOT_FOUND", "项目不存在");
        }
        return project;
    }

    private Map<Long, WfInstance> batchLoadInstances(List<WfTask> tasks) {
        if (CollectionUtils.isEmpty(tasks)) {
            return Collections.emptyMap();
        }
        Set<Long> instanceIds = tasks.stream()
                .map(WfTask::getInstanceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (instanceIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Long tenantId = UserContext.getCurrentTenantId();
        return wfInstanceMapper.selectList(new LambdaQueryWrapper<WfInstance>()
                        .eq(WfInstance::getTenantId, tenantId)
                        .in(WfInstance::getId, instanceIds))
                .stream()
                .collect(Collectors.toMap(WfInstance::getId, instance -> instance, (left, right) -> left));
    }

    private Comparator<CtContractChange> changeComparator() {
        return Comparator.comparing(this::changeTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(CtContractChange::getId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private LocalDateTime changeTime(CtContractChange change) {
        return change.getUpdatedTime() != null ? change.getUpdatedTime() : change.getCreatedTime();
    }

    private boolean existedBy(LocalDate businessDate, LocalDateTime createdAt, YearMonth selectedMonth) {
        if (selectedMonth == null) return true;
        LocalDate date = businessDate != null
                ? businessDate
                : createdAt == null ? null : createdAt.toLocalDate();
        return date != null && !date.isAfter(selectedMonth.atEndOfMonth());
    }
}
