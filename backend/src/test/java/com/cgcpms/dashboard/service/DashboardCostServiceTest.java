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
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@DisplayName("Dashboard cost view and breakdown")
class DashboardCostServiceTest extends DashboardServiceTestSupport {

    @Autowired private SysRoleMapper sysRoleMapper;

    @Test
    @Transactional
    @DisplayName("3.1 Cost view: returns cost data from cost_summary")
    void testCostView_SingleProject() {
        SeedResult sr = seed("COST1");
        CostManagerDashboardVO vo = dashboardService.getCostManagerView(sr.projectId);

        assertNotNull(vo);
        assertEquals(sr.projectId.toString(), vo.getProjectId());
        assertNotNull(vo.getTargetCost());
        assertNotNull(vo.getDynamicCost());
        assertNotNull(vo.getCostDeviation());
        assertNotNull(vo.getActualCost());
        assertNotNull(vo.getExpectedProfit());
        assertTrue(vo.getOverBudgetAlerts().size() >= 1);
        assertEquals(sr.projectName, vo.getOverBudgetAlerts().get(0).getProjectName());
    }

    @Test
    @Transactional
    @DisplayName("3.1b Cost view: returns dashboard contract lists from real project data")
    void testCostView_ReturnsDashboardContractLists() {
        SeedResult sr = seed("COST_FULL");
        wfTaskMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getTenantId, TENANT_ID)
                        .eq(WfTask::getApproverId, USER_ADMIN))
                .stream()
                .filter(task -> {
                    WfInstance instance = wfInstanceMapper.selectById(task.getInstanceId());
                    return instance != null && sr.projectId.equals(instance.getProjectId());
                })
                .forEach(task -> {
                    task.setReceivedAt(LocalDateTime.now().minusDays(9));
                    wfTaskMapper.updateById(task);
                });
        CostManagerDashboardVO vo = dashboardService.getCostManagerView(sr.projectId);

        assertNotNull(vo);
        assertEquals(sr.projectId.toString(), vo.getProjectId());
        assertEquals("8000000.00", vo.getTargetCost());
        assertEquals("8200000.00", vo.getDynamicCost());

        assertNotNull(vo.getTrendPoints());
        assertEquals(2, vo.getTrendPoints().size(), "monthly trend should come from cost_summary dates");
        assertTrue(vo.getTrendPoints().get(0).getMonth().compareTo(vo.getTrendPoints().get(1).getMonth()) <= 0);
        assertEquals("7600000.00", vo.getTrendPoints().get(0).getDynamicCost());
        assertEquals("8200000.00", vo.getTrendPoints().get(1).getDynamicCost());

        assertNotNull(vo.getSubjectRankings());
        assertEquals(1, vo.getSubjectRankings().size());
        assertEquals("人工费", vo.getSubjectRankings().get(0).getCostSubjectName());
        assertEquals("1500000.00", vo.getSubjectRankings().get(0).getActualCost());

        assertNotNull(vo.getOverdueItems());
        assertEquals(1, vo.getOverdueItems().size());
        assertEquals("审批-COST_FULL", vo.getOverdueItems().get(0).getTitle());
        assertTrue(vo.getOverdueItems().get(0).getOverdueDays() >= 2);
        assertEquals("成本经理", vo.getOverdueItems().get(0).getOwnerName());

        assertNotNull(vo.getPendingPayments());
        assertEquals(1, vo.getPendingPayments().size());
        assertEquals("230000.00", vo.getPendingPayments().get(0).getPayAmount());
        assertEquals("PENDING_APPROVAL", vo.getPendingPayments().get(0).getPayStatus());
        assertEquals("Contract COST_FULL", vo.getPendingPayments().get(0).getContractName());

        assertNotNull(vo.getLedgerRows());
        CostManagerDashboardVO.LedgerRow costRow = vo.getLedgerRows().stream()
                .filter(row -> "cost".equals(row.getRowType()))
                .findFirst()
                .orElseThrow();
        assertEquals("人工费", costRow.getCostSubjectName());
        assertEquals("CONTRACT", costRow.getSourceType());
        assertNotNull(costRow.getSourceId());
        assertEquals("CT-COST_FULL", costRow.getContractCode());
        assertEquals("Contract COST_FULL", costRow.getContractName());
        assertEquals("正常", costRow.getStatus());
        assertTrue(vo.getLedgerRows().stream()
                .anyMatch(row -> "contract".equals(row.getRowType())
                        && "CONTRACT".equals(row.getSourceType())
                        && row.getSourceId() != null));
        assertTrue(vo.getLedgerRows().stream()
                .anyMatch(row -> "fund".equals(row.getRowType())
                        && "PAY_RECORD".equals(row.getSourceType())
                        && row.getSourceId() != null));
        assertEquals((long) vo.getLedgerRows().size(), vo.getLedgerTotal());
    }

    @Test
    @Transactional
    @DisplayName("3.1c Cost view: selected month uses cost snapshot up to that month")
    void testCostView_SelectedMonthUsesMonthlySnapshot() {
        SeedResult sr = seed("COST_MONTH");
        String lastMonth = LocalDate.now().minusMonths(1).withDayOfMonth(1).toString().substring(0, 7);

        CostManagerDashboardVO vo = dashboardService.getCostManagerView(sr.projectId, lastMonth);

        assertNotNull(vo);
        assertEquals("7600000.00", vo.getTargetCost());
        assertEquals("7600000.00", vo.getDynamicCost());
        assertEquals("3500000.00", vo.getActualCost());
        assertEquals(1, vo.getTrendPoints().size());
        assertEquals(lastMonth, vo.getTrendPoints().get(0).getMonth());
    }

    @Test
    @Transactional
    @DisplayName("3.1d Cost view: selected month keeps latest subject snapshot")
    void testCostView_SelectedMonthKeepsLatestSubjectSnapshot() {
        SeedResult sr = seed("COST_SUBJECT_MONTH");
        LocalDate lastMonthDate = LocalDate.now().minusMonths(1).withDayOfMonth(1);
        for (CostSummary summary : costSummaryMapper.selectList(null)) {
            if (sr.projectId.equals(summary.getProjectId()) && summary.getCostSubjectId() != null) {
                summary.setSummaryDate(lastMonthDate);
                costSummaryMapper.updateById(summary);
            }
        }

        CostManagerDashboardVO vo = dashboardService.getCostManagerView(sr.projectId, LocalDate.now().toString().substring(0, 7));

        assertEquals(1, vo.getSubjectRankings().size());
        assertEquals("人工费", vo.getSubjectRankings().get(0).getCostSubjectName());
        assertTrue(vo.getLedgerRows().stream().anyMatch(row -> "人工费".equals(row.getCostSubjectName())));
    }

    @Test
    @Transactional
    @DisplayName("3.1d2 Cost view: monthly trend uses latest project snapshot only")
    void testCostView_TrendDoesNotAccumulateDailySnapshots() {
        SeedResult sr = seed("COST_TREND_LATEST");
        LocalDate latestDate = LocalDate.now().withDayOfMonth(1).plusDays(2);
        CostSummary latest = new CostSummary();
        latest.setTenantId(TENANT_ID);
        latest.setProjectId(sr.projectId);
        latest.setSummaryDate(latestDate);
        latest.setTargetCost(new BigDecimal("8100000.00"));
        latest.setDynamicCost(new BigDecimal("8300000.00"));
        latest.setCostDeviation(new BigDecimal("200000.00"));
        costSummaryMapper.insert(latest);

        CostManagerDashboardVO vo = dashboardService.getCostManagerView(sr.projectId);

        String currentMonth = latestDate.toString().substring(0, 7);
        CostManagerDashboardVO.TrendPoint point = vo.getTrendPoints().stream()
                .filter(item -> currentMonth.equals(item.getMonth()))
                .findFirst()
                .orElseThrow();
        assertEquals("8100000.00", point.getTargetCost());
        assertEquals("8300000.00", point.getDynamicCost());
        assertEquals("200000.00", point.getCostDeviation());
    }

    @Test
    @Transactional
    @DisplayName("3.1d3 Cost view: historical overdue threshold is selected month end")
    void testCostView_HistoricalOverdueUsesSelectedMonthEnd() {
        SeedResult sr = seed("COST_OVERDUE_AS_OF");
        LocalDate historicalMonthEnd = LocalDate.now().minusMonths(1).withDayOfMonth(1)
                .withDayOfMonth(LocalDate.now().minusMonths(1).lengthOfMonth());
        wfTaskMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WfTask>()
                        .eq(WfTask::getTenantId, TENANT_ID))
                .stream()
                .filter(task -> {
                    WfInstance instance = wfInstanceMapper.selectById(task.getInstanceId());
                    return instance != null && sr.projectId.equals(instance.getProjectId());
                })
                .forEach(task -> {
                    task.setReceivedAt(historicalMonthEnd.minusDays(3).atTime(10, 0));
                    wfTaskMapper.updateById(task);
                });

        CostManagerDashboardVO vo = dashboardService.getCostManagerView(
                sr.projectId, historicalMonthEnd.toString().substring(0, 7));

        assertTrue(vo.getOverdueItems().isEmpty(),
                "month-end age below seven days must not become overdue because current time is later");
    }

    @Test
    @Transactional
    @DisplayName("3.1e Cost view: fallback rankings include variation cost items")
    void testCostView_FallbackRankingsIncludeVariationCostItems() {
        SeedResult sr = seed("COST_VAR_ITEM");
        costSummaryMapper.physicalDeleteByTenantAndProject(TENANT_ID, sr.projectId);

        CostSubject subject = costSubjectMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CostSubject>()
                        .eq(CostSubject::getTenantId, TENANT_ID)
                        .eq(CostSubject::getSubjectCode, "SUBJ-COST_VAR_ITEM"))
                .stream()
                .findFirst()
                .orElseThrow();

        CostItem variationCost = new CostItem();
        variationCost.setTenantId(TENANT_ID);
        variationCost.setProjectId(sr.projectId);
        variationCost.setCostSubjectId(subject.getId());
        variationCost.setCostType("VARIATION");
        variationCost.setAmount(new BigDecimal("30000.00"));
        variationCost.setTaxAmount(BigDecimal.ZERO);
        variationCost.setAmountWithoutTax(new BigDecimal("30000.00"));
        variationCost.setSourceType("VAR_ORDER");
        variationCost.setSourceId(1L);
        variationCost.setSourceItemId(1L);
        variationCost.setCostDate(LocalDate.now());
        variationCost.setCostStatus("CONFIRMED");
        variationCost.setGeneratedFlag(1);
        costItemMapper.insert(variationCost);

        CostManagerDashboardVO vo = dashboardService.getCostManagerView(sr.projectId);

        CostManagerDashboardVO.SubjectRanking ranking = vo.getSubjectRankings().stream()
                .filter(item -> subject.getId().toString().equals(item.getCostSubjectId()))
                .findFirst()
                .orElseThrow();
        assertEquals("人工费", ranking.getCostSubjectName());
        assertEquals("30000.00", ranking.getActualCost());
        assertTrue(vo.getLedgerRows().stream()
                .anyMatch(row -> subject.getId().toString().equals(row.getCostSubjectId())
                        && "30000.00".equals(row.getActualAmount())));
    }

    @Test
    @Transactional
    @DisplayName("3.2 Cost view: null projectId returns tenant-wide cost")
    void testCostView_AllProjects() {
        seed("COST2");
        CostManagerDashboardVO vo = dashboardService.getCostManagerView(null);

        assertNotNull(vo);
        assertNull(vo.getProjectId());
        assertEquals("全部项目", vo.getProjectName());
        assertNotNull(vo.getTargetCost());
        assertNotNull(vo.getDynamicCost());
        assertNotNull(vo.getTrendPoints());
        assertNotNull(vo.getSubjectRankings());
        assertNotNull(vo.getOverdueItems());
        assertNotNull(vo.getPendingPayments());
        assertNotNull(vo.getLedgerRows());
        assertTrue(vo.getLedgerTotal() >= 1L);
    }

    @Test
    @Transactional
    @DisplayName("3.2b Cost view and breakdown respect SELF project scope")
    void testCostView_RespectsProjectDataScope() {
        SeedResult visible = seed("COST_SCOPE_VISIBLE");
        SeedResult hidden = seed("COST_SCOPE_HIDDEN");
        long scopedUserId = 88_201L;

        PmProject visibleProject = projectMapper.selectById(visible.projectId);
        visibleProject.setCreatedBy(scopedUserId);
        visibleProject.setProjectManagerId(null);
        projectMapper.updateById(visibleProject);
        PmProject hiddenProject = projectMapper.selectById(hidden.projectId);
        hiddenProject.setCreatedBy(scopedUserId + 1);
        hiddenProject.setProjectManagerId(null);
        projectMapper.updateById(hiddenProject);

        String roleCode = "COST_DASH_SELF_" + System.nanoTime();
        SysRole role = new SysRole();
        role.setTenantId(TENANT_ID);
        role.setRoleCode(roleCode);
        role.setRoleName("Cost dashboard SELF scope");
        role.setRoleType("CUSTOM");
        role.setStatus("ENABLE");
        role.setDataScope("SELF");
        sysRoleMapper.insert(role);
        TestUserContext.setUser(TENANT_ID, scopedUserId, "cost-dashboard-self", List.of(roleCode));

        BusinessException viewDenied = assertThrows(BusinessException.class,
                () -> dashboardService.getCostManagerView(hidden.projectId));
        assertEquals("PROJECT_ACCESS_DENIED", viewDenied.getCode());
        BusinessException breakdownDenied = assertThrows(BusinessException.class,
                () -> dashboardService.getCostBreakdown(hidden.projectId));
        assertEquals("PROJECT_ACCESS_DENIED", breakdownDenied.getCode());

        CostManagerDashboardVO all = dashboardService.getCostManagerView(null);
        assertEquals("8000000.00", all.getTargetCost());
        assertTrue(all.getOverBudgetAlerts().stream()
                .allMatch(item -> visible.projectId.toString().equals(item.getProjectId())));
    }

    // ========================================================================
    // 4. Finance View
    // ========================================================================

    @Test
    @DisplayName("4.0 Cost service: does not retain finance aggregate helper")
    void testCostService_DoesNotRetainFinanceAggregateHelper() {
        assertFalse(Arrays.stream(DashboardCostService.class.getDeclaredMethods())
                .anyMatch(method -> "getFinanceViewAllProjects".equals(method.getName())));
    }

    @Test
    @Transactional
    @DisplayName("6.1 Cost breakdown: returns subject drill-down with level ≤ 2")
    void testCostBreakdown() {
        SeedResult sr = seed("BD1");
        CostBreakdownVO vo = dashboardService.getCostBreakdown(sr.projectId);

        assertNotNull(vo);
        assertEquals(sr.projectId.toString(), vo.getProjectId());
        assertNotNull(vo.getTargetCost());
        assertNotNull(vo.getDynamicCost());
        assertNotNull(vo.getExpectedProfit());
        assertNotNull(vo.getSubjectBreakdowns());
        assertTrue(vo.getSubjectBreakdowns().size() >= 1);

        for (CostBreakdownVO.SubjectBreakdown bd : vo.getSubjectBreakdowns()) {
            assertNotNull(bd.getLevel());
            assertTrue(bd.getLevel() <= 2, "Only level ≤ 2 subjects");
        }
    }

    @Test
    @Transactional
    @DisplayName("6.2 Cost breakdown: graceful empty summaries")
    void testCostBreakdown_EmptyGraceful() {
        SeedResult sr = seed("BD2");
        CostBreakdownVO vo = dashboardService.getCostBreakdown(sr.projectId);
        assertNotNull(vo);
        assertNotNull(vo.getSubjectBreakdowns());
    }

    @Test
    @Transactional
    @DisplayName("6.3 Cost breakdown: missing subject name falls back to empty")
    void testCostBreakdown_SubjectNameFallback() {
        SeedResult sr = seed("BD3");
        CostBreakdownVO vo = dashboardService.getCostBreakdown(sr.projectId);
        assertNotNull(vo);
        for (CostBreakdownVO.SubjectBreakdown bd : vo.getSubjectBreakdowns()) {
            assertNotNull(bd.getCostSubjectName(), "subjectName should not be null");
        }
    }

    @Test
    @Transactional
    @DisplayName("6.4 Cost breakdown: selected month keeps latest subject snapshot")
    void testCostBreakdown_SelectedMonthKeepsLatestSubjectSnapshot() {
        SeedResult sr = seed("BD_MONTH_LATEST");
        CostSummary currentSubject = costSummaryMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CostSummary>()
                                .eq(CostSummary::getTenantId, TENANT_ID)
                                .eq(CostSummary::getProjectId, sr.projectId)
                                .isNotNull(CostSummary::getCostSubjectId))
                .stream()
                .findFirst()
                .orElseThrow();
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        currentSubject.setSummaryDate(monthStart.plusDays(2));
        costSummaryMapper.updateById(currentSubject);

        CostSummary older = new CostSummary();
        older.setTenantId(TENANT_ID);
        older.setProjectId(sr.projectId);
        older.setSummaryDate(monthStart.plusDays(1));
        older.setCostSubjectId(currentSubject.getCostSubjectId());
        older.setTargetCost(new BigDecimal("999.00"));
        older.setContractLockedCost(new BigDecimal("999.00"));
        older.setActualCost(new BigDecimal("999.00"));
        older.setDynamicCost(new BigDecimal("999.00"));
        older.setCostDeviation(BigDecimal.ZERO);
        costSummaryMapper.insert(older);

        CostBreakdownVO vo = dashboardService.getCostBreakdown(
                sr.projectId, monthStart.toString().substring(0, 7));

        assertEquals(1, vo.getSubjectBreakdowns().size());
        assertEquals("3000000.00", vo.getSubjectBreakdowns().get(0).getTargetCost());
    }

    // ========================================================================
    // 7. Edges
    // ========================================================================

    @Test
    @Transactional
    @DisplayName("8.5 Cost view: invalid month is rejected")
    void testCostView_InvalidMonthThrows() {
        SeedResult sr = seed("COST_BAD_MONTH");
        BusinessException error = assertThrows(BusinessException.class,
                () -> dashboardService.getCostManagerView(sr.projectId, "bad-month"));
        assertEquals("INVALID_DASHBOARD_MONTH", error.getCode());
    }
}
