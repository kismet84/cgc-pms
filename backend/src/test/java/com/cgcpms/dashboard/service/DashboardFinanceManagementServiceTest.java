package com.cgcpms.dashboard.service;

import com.cgcpms.alert.entity.AlertLog;
import com.cgcpms.alert.mapper.AlertLogMapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.TestUserContext;
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
@DisplayName("Dashboard finance and management views")
class DashboardFinanceManagementServiceTest extends DashboardServiceTestSupport {

    @Autowired private SysRoleMapper sysRoleMapper;

    @Test
    @Transactional
    @DisplayName("4.1 Finance view: single project payment analysis")
    void testFinanceView_SingleProject() {
        SeedResult sr = seed("FIN1");
        FinanceDashboardVO vo = dashboardService.getFinanceView(sr.projectId);

        assertNotNull(vo);
        assertEquals(sr.projectId.toString(), vo.getProjectId());
        assertNotNull(vo.getPendingPaymentAmount());
        assertNotNull(vo.getPendingPaymentCount());
        assertNotNull(vo.getApprovedUnpaidAmount());
        assertNotNull(vo.getOverRatioAmount());
        assertNotNull(vo.getWarrantyExpiringAmount());
        assertNotNull(vo.getTrendPoints());
        assertNotNull(vo.getPendingPayments());
    }

    @Test
    @Transactional
    @DisplayName("4.2 Finance view: null projectId returns tenant-wide finance")
    void testFinanceView_AllProjects() {
        seed("FIN2");
        FinanceDashboardVO vo = dashboardService.getFinanceView(null);

        assertNotNull(vo);
        assertNull(vo.getProjectId());
        assertEquals("全部项目", vo.getProjectName());
        assertNotNull(vo.getPendingPaymentAmount());
        assertNotNull(vo.getTrendPoints());
    }

    // ========================================================================
    // 5. Management View
    // ========================================================================

    @Test
    @Transactional
    @DisplayName("5.1 Management view: returns project rankings and aggregates")
    void testManagementView() {
        SeedResult sr = seed("MGMT1");
        ManagementDashboardVO vo = dashboardService.getManagementView();

        assertNotNull(vo);
        assertTrue(vo.getActiveProjectCount() >= 1);
        assertNotNull(vo.getTotalContractAmount());
        assertNotNull(vo.getTotalDynamicCost());
        assertNotNull(vo.getTotalExpectedProfit());
        assertNotNull(vo.getTotalPaidAmount());
        assertNotNull(vo.getProjectRankings());
        assertTrue(vo.getProjectRankings().size() >= 1);
        assertNotNull(vo.getMetricSources());
        assertEquals(vo.getProjectRankings().size(), vo.getMetricSources().size());
        assertTrue(vo.getMetricSources().stream()
                        .anyMatch(source -> sr.projectId.toString().equals(source.getSourceId())
                                && "PROJECT_SUMMARY".equals(source.getSourceType())),
                "经营总览指标应能下钻到项目汇总来源");

        // Rankings sorted by expectedProfit DESC
        List<DashboardProjectSummaryVO> rankings = vo.getProjectRankings();
        for (int i = 1; i < rankings.size(); i++) {
            BigDecimal prev = new BigDecimal(
                    rankings.get(i - 1).getExpectedProfit() != null
                            ? rankings.get(i - 1).getExpectedProfit() : "0");
            BigDecimal curr = new BigDecimal(
                    rankings.get(i).getExpectedProfit() != null
                            ? rankings.get(i).getExpectedProfit() : "0");
            assertTrue(prev.compareTo(curr) >= 0,
                    "Rankings should be sorted by expectedProfit DESC");
        }

        assertNotNull(vo.getOverdueItems());
        assertNotNull(vo.getMajorRisks());
        assertTrue(vo.getMajorRisks().size() >= 1);
        assertTrue(vo.getTotalPendingTaskCount() >= 1);
    }

    @Test
    @Transactional
    @DisplayName("5.2 Management view: graceful with existing projects")
    void testManagementView_Graceful() {
        seed("MGMT2");
        ManagementDashboardVO vo = dashboardService.getManagementView();
        assertNotNull(vo);
        assertNotNull(vo.getProjectRankings());
        assertNotNull(vo.getTotalContractAmount());
    }

    @Test
    @Transactional
    @DisplayName("5.3 Management view: rankings, tasks and risks respect SELF project scope")
    void testManagementView_RespectsProjectDataScope() {
        SeedResult visible = seed("MGMT_SELF_VISIBLE");
        SeedResult hidden = seed("MGMT_SELF_HIDDEN");
        long scopedUserId = 88_201L;
        String roleCode = applySelfScope(visible.projectId, hidden.projectId, scopedUserId);

        ManagementDashboardVO vo = dashboardService.getManagementView();
        assertEquals(1L, vo.getActiveProjectCount());
        assertEquals(List.of(visible.projectId.toString()), vo.getProjectRankings().stream()
                .map(DashboardProjectSummaryVO::getProjectId).toList());
        DashboardProjectSummaryVO visibleRanking = vo.getProjectRankings().get(0);
        assertEquals(visibleRanking.getContractIncome(), vo.getTotalContractAmount());
        assertEquals(visibleRanking.getDynamicCost(), vo.getTotalDynamicCost());
        assertEquals(visibleRanking.getExpectedProfit(), vo.getTotalExpectedProfit());
        assertEquals(visibleRanking.getPaidAmount(), vo.getTotalPaidAmount());
        assertEquals(1L, vo.getTotalPendingTaskCount());
        assertEquals(1L, vo.getTotalRiskCount());

        TestUserContext.setUser(TENANT_ID, scopedUserId + 99, "management-self-empty", List.of(roleCode));
        ManagementDashboardVO empty = dashboardService.getManagementView();
        assertEquals(0L, empty.getActiveProjectCount());
        assertEquals("0", empty.getTotalContractAmount());
        assertEquals("0", empty.getTotalDynamicCost());
        assertEquals("0", empty.getTotalExpectedProfit());
        assertEquals("0", empty.getTotalPaidAmount());
        assertEquals(0L, empty.getTotalPendingTaskCount());
        assertEquals(0L, empty.getTotalRiskCount());
        assertTrue(empty.getProjectRankings().isEmpty());
        assertTrue(empty.getMetricSources().isEmpty());
        assertTrue(empty.getOverdueItems().isEmpty());
        assertTrue(empty.getMajorRisks().isEmpty());
    }

    private String applySelfScope(Long visibleProjectId, Long hiddenProjectId, long scopedUserId) {
        PmProject visible = projectMapper.selectById(visibleProjectId);
        visible.setCreatedBy(scopedUserId);
        visible.setProjectManagerId(null);
        projectMapper.updateById(visible);
        PmProject hidden = projectMapper.selectById(hiddenProjectId);
        hidden.setCreatedBy(scopedUserId + 1);
        hidden.setProjectManagerId(null);
        projectMapper.updateById(hidden);

        String roleCode = "MGMT_DASH_SELF_" + System.nanoTime();
        SysRole role = new SysRole();
        role.setTenantId(TENANT_ID);
        role.setRoleCode(roleCode);
        role.setRoleName("Management dashboard SELF scope");
        role.setRoleType("CUSTOM");
        role.setStatus("ENABLE");
        role.setDataScope("SELF");
        sysRoleMapper.insert(role);
        TestUserContext.setUser(TENANT_ID, scopedUserId, "management-dashboard-self", List.of(roleCode));
        return roleCode;
    }

    // ========================================================================
    // 6. Cost Breakdown
    // ========================================================================
}
