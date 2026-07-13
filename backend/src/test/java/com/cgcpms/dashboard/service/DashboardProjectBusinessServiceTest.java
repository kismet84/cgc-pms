package com.cgcpms.dashboard.service;

import com.cgcpms.alert.entity.AlertLog;
import com.cgcpms.alert.mapper.AlertLogMapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.entity.CostSummary;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import com.cgcpms.cost.mapper.CostSummaryMapper;
import com.cgcpms.inventory.entity.MatStock;
import com.cgcpms.inventory.entity.MatWarehouse;
import com.cgcpms.inventory.mapper.MatStockMapper;
import com.cgcpms.inventory.mapper.MatWarehouseMapper;
import com.cgcpms.dashboard.vo.*;
import com.cgcpms.material.entity.MdMaterial;
import com.cgcpms.material.mapper.MdMaterialMapper;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.project.entity.PmProject;
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
import com.cgcpms.system.entity.SysUser;
import com.cgcpms.system.entity.SysRole;
import com.cgcpms.system.mapper.SysRoleMapper;
import com.cgcpms.system.mapper.SysUserMapper;
import com.cgcpms.tech.entity.TechItem;
import com.cgcpms.tech.mapper.TechItemMapper;
import com.cgcpms.tech.vo.ChiefEngineerDashboardVO;
import com.cgcpms.variation.entity.VarOrder;
import com.cgcpms.variation.mapper.VarOrderMapper;
import com.cgcpms.workflow.WorkflowConstants;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.entity.WfTask;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.mapper.WfTaskMapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@DisplayName("Dashboard project and business views")
class DashboardProjectBusinessServiceTest extends DashboardServiceTestSupport {

    @Autowired private SysRoleMapper sysRoleMapper;

    @Test
    @Transactional
    @DisplayName("1.1 PM view: single project returns correct KPI counts")
    void testPMView_SingleProject() {
        SeedResult sr = seed("PM1");
        ProjectManagerDashboardVO vo = dashboardService.getProjectManagerView(sr.projectId);

        assertNotNull(vo, "PM VO should not be null");
        assertEquals(sr.projectId.toString(), vo.getProjectId());
        assertEquals(sr.projectName, vo.getProjectName());

        assertTrue(vo.getPendingTaskCount() >= 1, "At least 1 pending task");
        assertTrue(vo.getLaggingProjectCount() >= 1, "At least 1 lagging project");
        assertTrue(vo.getExpiringContractCount() >= 1, "At least 1 expiring contract");
        assertNotNull(vo.getPendingApprovals(), "pendingApprovals should not be null");
    }

    @Test
    @Transactional
    @DisplayName("1.1a PM view: pending approvals load instance details outside current user tasks")
    void testPMView_PendingApprovalsLoadOwnInstanceDetails() {
        SeedResult sr = seed("PM_APPROVAL_SUMMARY");

        WfInstance externalInstance = new WfInstance();
        externalInstance.setTenantId(TENANT_ID);
        externalInstance.setTemplateId(1L);
        externalInstance.setBusinessType("CONTRACT");
        externalInstance.setBusinessId(970000000000L);
        externalInstance.setProjectId(sr.projectId);
        externalInstance.setTitle("非当前用户审批-PM_APPROVAL_SUMMARY");
        externalInstance.setBusinessSummary("非当前用户审批摘要-PM_APPROVAL_SUMMARY");
        externalInstance.setAmount(new BigDecimal("123456.00"));
        externalInstance.setInstanceStatus("RUNNING");
        externalInstance.setInitiatorId(USER_ADMIN);
        wfInstanceMapper.insert(externalInstance);

        WfTask externalTask = new WfTask();
        externalTask.setTenantId(TENANT_ID);
        externalTask.setInstanceId(externalInstance.getId());
        externalTask.setNodeInstanceId(2L);
        externalTask.setBusinessType("CONTRACT");
        externalTask.setBusinessId(970000000000L);
        externalTask.setApproverId(2L);
        externalTask.setApproverName("项目总监");
        externalTask.setTaskStatus(WorkflowConstants.TASK_PENDING);
        externalTask.setReceivedAt(LocalDateTime.now().minusDays(4));
        wfTaskMapper.insert(externalTask);

        ProjectManagerDashboardVO vo = dashboardService.getProjectManagerView(sr.projectId);

        DashboardTaskItemVO item = vo.getPendingApprovals().stream()
                .filter(i -> externalTask.getId().toString().equals(i.getTaskId()))
                .findFirst()
                .orElseThrow();
        assertEquals("非当前用户审批-PM_APPROVAL_SUMMARY", item.getTitle());
        assertEquals("非当前用户审批摘要-PM_APPROVAL_SUMMARY", item.getItemSummary());
        assertEquals("项目总监", item.getOwnerName());
        assertEquals("123456.00", item.getAmount());
        assertEquals(4L, item.getPendingDays());
        assertEquals(sr.projectId.toString(), item.getProjectId());
        assertEquals(sr.projectName, item.getProjectName());
    }

    @Test
    @Transactional
    @DisplayName("1.1b PM view: payment approvals are not PM dashboard main-axis tasks")
    void testPMView_ExcludesPaymentApprovalTasks() {
        SeedResult sr = seed("PM_NO_PAYMENT");

        WfInstance payInstance = new WfInstance();
        payInstance.setTenantId(TENANT_ID);
        payInstance.setTemplateId(50005L);
        payInstance.setBusinessType("PAY_APPLICATION");
        payInstance.setBusinessId(970000000001L);
        payInstance.setProjectId(sr.projectId);
        payInstance.setTitle("待审批付款-PM_NO_PAYMENT");
        payInstance.setBusinessSummary("付款审批摘要-PM_NO_PAYMENT");
        payInstance.setAmount(new BigDecimal("98765.00"));
        payInstance.setInstanceStatus("RUNNING");
        payInstance.setInitiatorId(USER_ADMIN);
        wfInstanceMapper.insert(payInstance);

        WfTask payTask = new WfTask();
        payTask.setTenantId(TENANT_ID);
        payTask.setInstanceId(payInstance.getId());
        payTask.setNodeInstanceId(3L);
        payTask.setBusinessType("CONTRACT");
        payTask.setBusinessId(970000000001L);
        payTask.setApproverId(USER_ADMIN);
        payTask.setApproverName("项目经理");
        payTask.setTaskStatus(WorkflowConstants.TASK_PENDING);
        payTask.setReceivedAt(LocalDateTime.now().minusDays(1));
        wfTaskMapper.insert(payTask);

        ProjectManagerDashboardVO vo = dashboardService.getProjectManagerView(sr.projectId);

        assertTrue(vo.getPendingTasks().stream().noneMatch(i -> WorkflowBusinessTypes.PAY_REQUEST.equals(i.getBusinessType())));
        assertTrue(vo.getPendingTasks().stream().noneMatch(i -> "PAY_APPLICATION".equals(i.getBusinessType())));
        assertTrue(vo.getPendingApprovals().stream().noneMatch(i -> WorkflowBusinessTypes.PAY_REQUEST.equals(i.getBusinessType())));
        assertTrue(vo.getPendingApprovals().stream().noneMatch(i -> "PAY_APPLICATION".equals(i.getBusinessType())));
        assertTrue(vo.getPendingTasks().stream().noneMatch(i -> "待审批付款-PM_NO_PAYMENT".equals(i.getTitle())));
        assertTrue(vo.getPendingApprovals().stream().noneMatch(i -> "待审批付款-PM_NO_PAYMENT".equals(i.getTitle())));
    }

    @Test
    @Transactional
    @DisplayName("1.1c PM view: single project pending tasks stay scoped and carry readable fields")
    void testPMView_SingleProjectPendingTasksStayScopedAndCarryReadableFields() {
        SeedResult selected = seed("PM_SCOPE_A");
        SeedResult other = seed("PM_SCOPE_B");

        wfTaskMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getTenantId, TENANT_ID)
                        .eq(WfTask::getApproverId, USER_ADMIN))
                .stream()
                .filter(task -> {
                    WfInstance instance = wfInstanceMapper.selectById(task.getInstanceId());
                    return instance != null && selected.projectId.equals(instance.getProjectId());
                })
                .forEach(task -> {
                    task.setReceivedAt(LocalDateTime.now().minusDays(9));
                    wfTaskMapper.updateById(task);
                });

        ProjectManagerDashboardVO vo = dashboardService.getProjectManagerView(selected.projectId);

        assertTrue(vo.getPendingTasks().stream()
                .noneMatch(i -> other.projectId.toString().equals(i.getProjectId())),
                "single-project PM view must not include pending tasks from another project");

        DashboardTaskItemVO item = vo.getPendingTasks().stream()
                .filter(i -> selected.projectId.toString().equals(i.getProjectId()))
                .findFirst()
                .orElseThrow();
        assertEquals("审批-PM_SCOPE_A", item.getTitle());
        assertEquals("合同审批摘要-PM_SCOPE_A", item.getItemSummary());
        assertEquals("成本经理", item.getOwnerName());
        assertEquals("5000000.00", item.getAmount());
        assertEquals(9L, item.getPendingDays());
        assertEquals(selected.projectName, item.getProjectName());
    }

    @Test
    @Transactional
    @DisplayName("1.2 PM view: null projectId returns all-projects aggregate")
    void testPMView_AllProjects() {
        seed("PM2");
        ProjectManagerDashboardVO vo = dashboardService.getProjectManagerView(null);

        assertNotNull(vo);
        assertNull(vo.getProjectId());
        assertEquals("全部项目", vo.getProjectName());
        assertTrue(vo.getPendingTaskCount() >= 1);
    }

    @Test
    @Transactional
    @DisplayName("1.2a PM view: specified and all-project views respect SELF project scope")
    void testPMView_RespectsProjectDataScope() {
        SeedResult visible = seed("PM_SELF_VISIBLE");
        SeedResult hidden = seed("PM_SELF_HIDDEN");
        SeedResult completed = seed("PM_SELF_COMPLETED");
        long scopedUserId = 88_101L;
        assignProjectTasksTo(visible.projectId, scopedUserId);
        assignProjectTasksTo(hidden.projectId, scopedUserId);
        applySelfScope(visible.projectId, hidden.projectId, scopedUserId);
        PmProject completedProject = projectMapper.selectById(completed.projectId);
        completedProject.setCreatedBy(scopedUserId);
        completedProject.setProjectManagerId(null);
        completedProject.setStatus("COMPLETED");
        projectMapper.updateById(completedProject);

        BusinessException denied = assertThrows(BusinessException.class,
                () -> dashboardService.getProjectManagerView(hidden.projectId));
        assertEquals("PROJECT_ACCESS_DENIED", denied.getCode());
        BusinessException inactiveDenied = assertThrows(BusinessException.class,
                () -> dashboardService.getProjectManagerView(completed.projectId));
        assertEquals("PROJECT_ACCESS_DENIED", inactiveDenied.getCode());

        ProjectManagerDashboardVO specified = dashboardService.getProjectManagerView(visible.projectId);
        assertEquals(List.of(visible.projectId.toString()), specified.getLaggingProjects().stream()
                .map(DashboardProjectSummaryVO::getProjectId).toList());

        ProjectManagerDashboardVO all = dashboardService.getProjectManagerView(null);
        assertEquals(1L, all.getPendingTaskCount());
        assertEquals(1L, all.getPendingApprovalCount());
        assertEquals(1L, all.getExpiringContractCount());
        assertTrue(all.getPendingTasks().stream()
                .allMatch(item -> visible.projectId.toString().equals(item.getProjectId())));
        assertTrue(all.getPendingApprovals().stream()
                .allMatch(item -> visible.projectId.toString().equals(item.getProjectId())));
        assertTrue(all.getExpiringContracts().stream()
                .allMatch(item -> visible.projectId.toString().equals(item.getProjectId())));
        assertEquals(List.of(visible.projectId.toString()), all.getLaggingProjects().stream()
                .map(DashboardProjectSummaryVO::getProjectId).toList());

        TestUserContext.setUser(TENANT_ID, scopedUserId + 99, "dashboard-self-empty",
                UserContext.getCurrentRoles());
        ProjectManagerDashboardVO empty = dashboardService.getProjectManagerView(null);
        assertEquals(0L, empty.getPendingTaskCount());
        assertEquals(0L, empty.getPendingApprovalCount());
        assertEquals(0L, empty.getExpiringContractCount());
        assertEquals(0L, empty.getLaggingProjectCount());
        assertTrue(empty.getPendingTasks().isEmpty());
        assertTrue(empty.getPendingApprovals().isEmpty());
        assertTrue(empty.getExpiringContracts().isEmpty());
        assertTrue(empty.getLaggingProjects().isEmpty());
    }

    private void assignProjectTasksTo(Long projectId, long approverId) {
        List<Long> instanceIds = wfInstanceMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfInstance>()
                                .eq(WfInstance::getTenantId, TENANT_ID)
                                .eq(WfInstance::getProjectId, projectId))
                .stream().map(WfInstance::getId).toList();
        wfTaskMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getTenantId, TENANT_ID)
                        .in(WfTask::getInstanceId, instanceIds))
                .forEach(task -> {
                    task.setApproverId(approverId);
                    wfTaskMapper.updateById(task);
                });
    }

    private void applySelfScope(Long visibleProjectId, Long hiddenProjectId, long scopedUserId) {
        PmProject visible = projectMapper.selectById(visibleProjectId);
        visible.setCreatedBy(scopedUserId);
        visible.setProjectManagerId(null);
        projectMapper.updateById(visible);
        PmProject hidden = projectMapper.selectById(hiddenProjectId);
        hidden.setCreatedBy(scopedUserId + 1);
        hidden.setProjectManagerId(null);
        projectMapper.updateById(hidden);

        String roleCode = "PM_DASH_SELF_" + System.nanoTime();
        SysRole role = new SysRole();
        role.setTenantId(TENANT_ID);
        role.setRoleCode(roleCode);
        role.setRoleName("PM dashboard SELF scope");
        role.setRoleType("CUSTOM");
        role.setStatus("ENABLE");
        role.setDataScope("SELF");
        sysRoleMapper.insert(role);
        TestUserContext.setUser(TENANT_ID, scopedUserId, "pm-dashboard-self", List.of(roleCode));
    }

    // ========================================================================
    // 2. Business Manager View
    // ========================================================================

    @Test
    @Transactional
    @DisplayName("2.1 BM view: single project returns correct contract totals")
    void testBMView_SingleProject() {
        SeedResult sr = seed("BM1");
        BusinessManagerDashboardVO vo = dashboardService.getBusinessManagerView(sr.projectId);

        assertNotNull(vo);
        assertEquals(sr.projectId.toString(), vo.getProjectId());
        assertTrue(new BigDecimal(vo.getTotalContractAmount()).compareTo(BigDecimal.ZERO) > 0);
        assertTrue(new BigDecimal(vo.getVarOrderAmount()).compareTo(BigDecimal.ZERO) > 0);
        assertTrue(new BigDecimal(vo.getSubMeasureAmount()).compareTo(BigDecimal.ZERO) > 0);
        assertTrue(vo.getPaidRatio().contains("%"));
        assertNotNull(vo.getSettlementProgress());
        assertNotNull(vo.getRecentChanges());
        assertTrue(vo.getRecentChanges().size() <= 5);
    }

    @Test
    @Transactional
    @DisplayName("2.2 BM view: null projectId returns tenant-wide aggregate")
    void testBMView_AllProjects() {
        seed("BM2");
        BusinessManagerDashboardVO vo = dashboardService.getBusinessManagerView(null);

        assertNotNull(vo);
        assertNull(vo.getProjectId());
        assertEquals("全部项目", vo.getProjectName());
        assertNotNull(vo.getTotalContractAmount());
        assertNotNull(vo.getPaidRatio());
    }

    @Test
    @Transactional
    @DisplayName("2.3 BM view: grace over zero contracts")
    void testBMView_GracefulEmpty() {
        seed("BM3");
        BusinessManagerDashboardVO vo = dashboardService.getBusinessManagerView(null);
        assertNotNull(vo);
        assertNotNull(vo.getTotalContractAmount());
    }

    // ========================================================================
    // 3. Cost Manager View
    // ========================================================================

    @Test
    @Transactional
    @DisplayName("7.1 Paid ratio: format is N%")
    void testPaidRatio_Format() {
        SeedResult sr = seed("EDGE1");
        BusinessManagerDashboardVO vo = dashboardService.getBusinessManagerView(sr.projectId);
        assertNotNull(vo.getPaidRatio());
        assertTrue(vo.getPaidRatio().endsWith("%"), "paidRatio should end with %");
    }

    @Test
    @Transactional
    @DisplayName("7.2 Var order: only APPROVED amounts counted")
    void testVarOrder_OnlyApproved() {
        SeedResult sr = seed("EDGE2");
        BusinessManagerDashboardVO vo = dashboardService.getBusinessManagerView(sr.projectId);
        // BigDecimal.toPlainString() produces "100000.00"
        assertEquals("100000.00", vo.getVarOrderAmount());
    }

    @Test
    @Transactional
    @DisplayName("7.3 Lagging project: past plannedEndDate appears")
    void testLaggingProject_Present() {
        SeedResult sr = seed("EDGE3");
        ProjectManagerDashboardVO vo = dashboardService.getProjectManagerView(sr.projectId);
        boolean found = vo.getLaggingProjects().stream()
                .anyMatch(p -> sr.projectId.toString().equals(p.getProjectId()));
        assertTrue(found, "Seeded project should appear in lagging list");
    }

    // ========================================================================
    // 8. Month parameter tests
    // ========================================================================

    @Test
    @Transactional
    @DisplayName("8.1 PM view: accepts month parameter and filters by WfTask.receivedAt / CtContract.endDate")
    void testPMView_WithMonthParameter() {
        SeedResult sr = seed("PM_MONTH");
        String currentMonth = LocalDate.now().toString().substring(0, 7); // yyyy-MM

        ProjectManagerDashboardVO vo = dashboardService.getProjectManagerView(sr.projectId, currentMonth);

        assertNotNull(vo);
        assertTrue(vo.getPendingTaskCount() >= 1, "Should have tasks in current month");
        assertTrue(vo.getLaggingProjectCount() >= 1, "Lagging projects should NOT be filtered by month");

        // Test with month in the future — should filter out all date-scoped items
        String futureMonth = LocalDate.now().plusMonths(12).toString().substring(0, 7);
        ProjectManagerDashboardVO futureVo = dashboardService.getProjectManagerView(sr.projectId, futureMonth);
        assertEquals(0L, futureVo.getPendingTaskCount(), "Future month should have 0 pending tasks");
        assertEquals(0L, futureVo.getExpiringContractCount(), "Future month should have 0 expiring contracts");
        // Lagging projects are NOT month-filtered
        assertTrue(futureVo.getLaggingProjectCount() >= 1, "Lagging projects still visible even with month filter");
    }

    @Test
    @Transactional
    @DisplayName("8.1a PM view: invalid month returns data without 500")
    void testPMView_InvalidMonthDoesNotThrow() {
        SeedResult sr = seed("PM_BAD_MONTH");
        ProjectManagerDashboardVO vo = dashboardService.getProjectManagerView(sr.projectId, "not-a-month");
        assertNotNull(vo);
        assertTrue(vo.getPendingTaskCount() >= 1, "Invalid month should be ignored, return full data");
    }
}
