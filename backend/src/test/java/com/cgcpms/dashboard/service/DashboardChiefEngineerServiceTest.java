package com.cgcpms.dashboard.service;

import com.cgcpms.alert.entity.AlertLog;
import com.cgcpms.alert.mapper.AlertLogMapper;
import com.cgcpms.auth.context.UserContext;
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
@DisplayName("Dashboard chief engineer view")
class DashboardChiefEngineerServiceTest extends DashboardServiceTestSupport {

    @Test
    @Transactional
    @DisplayName("3.5 Chief engineer view: maps owner and overdue days from tech item")
    void testChiefEngineerView_TechItemOwnerAndOverdueDays() {
        SeedResult sr = seed("CHIEF_DASH");

        TechItem item = new TechItem();
        item.setTenantId(TENANT_ID);
        item.setProjectId(sr.projectId);
        item.setItemType("TECH_ISSUE");
        item.setItemCode("TECH-CHIEF_DASH");
        item.setItemTitle("重大技术问题-CHIEF_DASH");
        item.setItemLevel("MAJOR");
        item.setItemStatus("OPEN");
        item.setDiscoveredAt(LocalDateTime.now().minusDays(5));
        item.setDueDate(LocalDateTime.now().minusDays(2));
        item.setResponsibleUserId(sr.signalUserId);
        techItemMapper.insert(item);

        ChiefEngineerDashboardVO vo = dashboardService.getChiefEngineerView(sr.projectId);

        DashboardBusinessItemVO issue = vo.getOpenIssues().stream()
                .filter(i -> "TECH-CHIEF_DASH".equals(i.getCode()))
                .findFirst()
                .orElseThrow();
        assertEquals("重大技术问题-CHIEF_DASH", issue.getTitle());
        assertEquals("MAJOR", issue.getAmount());
        assertNotNull(issue.getOwnerName());
        assertEquals(2L, issue.getOverdueDays());
    }

    @Test
    @Transactional
    @DisplayName("3.5a Chief engineer view: future due date does not emit overdueDays 0")
    void testChiefEngineerView_FutureDueDateHasNoOverdueDays() {
        SeedResult sr = seed("CHIEF_FUTURE");

        TechItem item = new TechItem();
        item.setTenantId(TENANT_ID);
        item.setProjectId(sr.projectId);
        item.setItemType("TECH_ISSUE");
        item.setItemCode("TECH-CHIEF_FUTURE");
        item.setItemTitle("未来到期技术问题-CHIEF_FUTURE");
        item.setItemLevel("MAJOR");
        item.setItemStatus("OPEN");
        item.setDiscoveredAt(LocalDateTime.now().minusDays(1));
        item.setDueDate(LocalDateTime.now().plusDays(2));
        item.setResponsibleUserId(sr.signalUserId);
        techItemMapper.insert(item);

        ChiefEngineerDashboardVO vo = dashboardService.getChiefEngineerView(sr.projectId);

        DashboardBusinessItemVO issue = vo.getOpenIssues().stream()
                .filter(i -> "TECH-CHIEF_FUTURE".equals(i.getCode()))
                .findFirst()
                .orElseThrow();
        assertNull(issue.getOverdueDays());
        assertTrue(vo.getOverdueItems().stream().noneMatch(i -> "TECH-CHIEF_FUTURE".equals(i.getCode())));
    }

    @Test
    @Transactional
    @DisplayName("3.5b Chief engineer view: yesterday due item is overdue by calendar day")
    void testChiefEngineerView_YesterdayDueDateHasPositiveOverdueDays() {
        SeedResult sr = seed("CHIEF_YESTERDAY");

        TechItem item = new TechItem();
        item.setTenantId(TENANT_ID);
        item.setProjectId(sr.projectId);
        item.setItemType("TECH_ISSUE");
        item.setItemCode("TECH-CHIEF_YESTERDAY");
        item.setItemTitle("昨日到期技术问题-CHIEF_YESTERDAY");
        item.setItemLevel("MAJOR");
        item.setItemStatus("OPEN");
        item.setDiscoveredAt(LocalDateTime.now().minusDays(2));
        item.setDueDate(LocalDate.now().minusDays(1).atTime(23, 59));
        item.setResponsibleUserId(sr.signalUserId);
        techItemMapper.insert(item);

        ChiefEngineerDashboardVO vo = dashboardService.getChiefEngineerView(sr.projectId);

        DashboardBusinessItemVO overdue = vo.getOverdueItems().stream()
                .filter(i -> "TECH-CHIEF_YESTERDAY".equals(i.getCode()))
                .findFirst()
                .orElseThrow();
        assertTrue(overdue.getOverdueDays() > 0L);
        assertEquals(1L, overdue.getOverdueDays());
    }

    @Test
    @Transactional
    @DisplayName("3.5c Chief engineer view: today due item is open but not overdue")
    void testChiefEngineerView_TodayDueDateIsNotOverdue() {
        SeedResult sr = seed("CHIEF_TODAY");

        TechItem item = new TechItem();
        item.setTenantId(TENANT_ID);
        item.setProjectId(sr.projectId);
        item.setItemType("TECH_ISSUE");
        item.setItemCode("TECH-CHIEF_TODAY");
        item.setItemTitle("今日到期技术问题-CHIEF_TODAY");
        item.setItemLevel("MAJOR");
        item.setItemStatus("OPEN");
        item.setDiscoveredAt(LocalDateTime.now().minusDays(1));
        item.setDueDate(LocalDate.now().atTime(0, 1));
        item.setResponsibleUserId(sr.signalUserId);
        techItemMapper.insert(item);

        ChiefEngineerDashboardVO vo = dashboardService.getChiefEngineerView(sr.projectId);

        DashboardBusinessItemVO issue = vo.getOpenIssues().stream()
                .filter(i -> "TECH-CHIEF_TODAY".equals(i.getCode()))
                .findFirst()
                .orElseThrow();
        assertNull(issue.getOverdueDays());
        assertTrue(vo.getOverdueItems().stream().noneMatch(i -> "TECH-CHIEF_TODAY".equals(i.getCode())));
    }

    @Test
    @Transactional
    @DisplayName("3.6 Default demo project: chief engineer dashboard has a today-due item")
    void testDefaultDemoProject_ChiefEngineerDashboardHasTodayDueItem() {
        setAdminContext();

        PmProject defaultProject = projectMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PmProject>()
                                .eq(PmProject::getTenantId, TENANT_ID)
                                .eq(PmProject::getDeletedFlag, 0)
                                .in(PmProject::getId, java.util.List.of(2071032241708793858L, 10001L)))
                .stream()
                .sorted(java.util.Comparator
                        .comparing((PmProject p) -> p.getId().equals(2071032241708793858L) ? 0 : 1)
                        .thenComparing(PmProject::getId))
                .findFirst()
                .orElseGet(() -> projectMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PmProject>()
                                        .eq(PmProject::getTenantId, TENANT_ID)
                                        .eq(PmProject::getStatus, "ACTIVE")
                                        .eq(PmProject::getDeletedFlag, 0)
                                        .orderByAsc(PmProject::getId))
                        .stream()
                        .findFirst()
                        .orElseThrow());

        TechItem demoItem = techItemMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TechItem>()
                        .eq(TechItem::getTenantId, TENANT_ID)
                        .eq(TechItem::getItemCode, "TECH-DEMO-105"));
        boolean exists = demoItem != null;
        if (!exists) {
            demoItem = new TechItem();
        }
        demoItem.setTenantId(TENANT_ID);
        demoItem.setProjectId(defaultProject.getId());
        demoItem.setItemType("TECH_ISSUE");
        demoItem.setItemCode("TECH-DEMO-105");
        demoItem.setItemTitle("今日到期技术事项");
        demoItem.setItemLevel("MAJOR");
        demoItem.setItemStatus("OPEN");
        demoItem.setDiscoveredAt(LocalDateTime.now().minusDays(1));
        demoItem.setDueDate(LocalDate.now().atStartOfDay());
        if (exists) {
            techItemMapper.updateById(demoItem);
        } else {
            techItemMapper.insert(demoItem);
        }

        ChiefEngineerDashboardVO vo = dashboardService.getChiefEngineerView(defaultProject.getId());

        DashboardBusinessItemVO todayDueItem = vo.getOpenIssues().stream()
                .filter(i -> "TECH-DEMO-105".equals(i.getCode()))
                .findFirst()
                .orElseThrow();

        assertNotNull(vo);
        assertEquals(defaultProject.getId().toString(), vo.getProjectId());
        assertTrue(vo.getOpenIssueCount() > 0L);
        assertEquals("TECH-DEMO-105", todayDueItem.getCode());
        assertTrue(todayDueItem.getDate().startsWith(LocalDate.now().toString()));
        assertNull(todayDueItem.getOverdueDays());
        assertTrue(vo.getOverdueItems().stream().noneMatch(i -> "TECH-DEMO-105".equals(i.getCode())));
    }

    @Test
    @Transactional
    @DisplayName("8.4 Chief engineer view: accepts month parameter and filters by dueDate/discoveredAt")
    void testChiefEngineerView_WithMonthParameter() {
        SeedResult sr = seed("CHIEF_MONTH");
        String currentMonth = LocalDate.now().toString().substring(0, 7);

        ChiefEngineerDashboardVO vo = dashboardService.getChiefEngineerView(sr.projectId, currentMonth);

        assertNotNull(vo);

        String futureMonth = LocalDate.now().plusMonths(12).toString().substring(0, 7);
        ChiefEngineerDashboardVO futureVo = dashboardService.getChiefEngineerView(sr.projectId, futureMonth);
        // Future month should have 0 items (no tech items seeded with future dates)
        assertEquals(0L, futureVo.getOpenIssueCount(), "Future month should have 0 tech items");
        assertEquals(0L, futureVo.getPendingReviewCount(), "Future month should have 0 reviews");
        assertEquals(0L, futureVo.getOverdueCount(), "Future month should have 0 overdue items");
    }

    @Test
    @Transactional
    @DisplayName("8.4a Chief engineer view: invalid month returns data without 500")
    void testChiefEngineerView_InvalidMonthDoesNotThrow() {
        SeedResult sr = seed("CHIEF_BAD_MONTH");
        ChiefEngineerDashboardVO vo = dashboardService.getChiefEngineerView(sr.projectId, "not-valid");
        assertNotNull(vo);
    }
}
