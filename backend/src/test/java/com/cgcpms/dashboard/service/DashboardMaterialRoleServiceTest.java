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
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@DisplayName("Dashboard purchase and production views")
class DashboardMaterialRoleServiceTest extends DashboardServiceTestSupport {

    @Autowired private SysRoleMapper sysRoleMapper;

    @Test
    @Transactional
    @DisplayName("3.2 Purchase manager view: aggregates existing purchase and receipt signals")
    void testPurchaseManagerView_MvpSignals() {
        SeedResult sr = seed("PUR_DASH");

        MatPurchaseOrder newestOrder = new MatPurchaseOrder();
        newestOrder.setTenantId(TENANT_ID);
        newestOrder.setProjectId(sr.projectId);
        newestOrder.setPartnerId(sr.partnerId);
        newestOrder.setOrderCode("PO-PUR_DASH-NEWEST");
        newestOrder.setOrderDate(LocalDate.now());
        newestOrder.setDeliveryDate(LocalDate.now().plusDays(7));
        newestOrder.setTotalAmount(new BigDecimal("140000.00"));
        newestOrder.setApprovalStatus("APPROVED");
        newestOrder.setOrderStatus("APPROVED");
        purchaseOrderMapper.insert(newestOrder);

        MatPurchaseOrderItem newestOrderItem = new MatPurchaseOrderItem();
        newestOrderItem.setTenantId(TENANT_ID);
        newestOrderItem.setProjectId(sr.projectId);
        newestOrderItem.setOrderId(newestOrder.getId());
        newestOrderItem.setMaterialId(sr.materialId);
        newestOrderItem.setMaterialName("止水钢板-PUR_DASH");
        newestOrderItem.setQuantity(new BigDecimal("10.0000"));
        newestOrderItem.setUnit("吨");
        newestOrderItem.setUnitPrice(new BigDecimal("14000.0000"));
        newestOrderItem.setAmount(new BigDecimal("140000.00"));
        purchaseOrderItemMapper.insert(newestOrderItem);

        MatPurchaseOrder olderLowerAmountOrder = new MatPurchaseOrder();
        olderLowerAmountOrder.setTenantId(TENANT_ID);
        olderLowerAmountOrder.setProjectId(sr.projectId);
        olderLowerAmountOrder.setPartnerId(sr.partnerId);
        olderLowerAmountOrder.setOrderCode("PO-PUR_DASH-OLD-LOW");
        olderLowerAmountOrder.setOrderDate(LocalDate.now().minusDays(8));
        olderLowerAmountOrder.setDeliveryDate(LocalDate.now().minusDays(5));
        olderLowerAmountOrder.setTotalAmount(new BigDecimal("110000.00"));
        olderLowerAmountOrder.setApprovalStatus("APPROVED");
        olderLowerAmountOrder.setOrderStatus("APPROVED");
        purchaseOrderMapper.insert(olderLowerAmountOrder);

        MatPurchaseOrder olderHigherAmountOrder = new MatPurchaseOrder();
        olderHigherAmountOrder.setTenantId(TENANT_ID);
        olderHigherAmountOrder.setProjectId(sr.projectId);
        olderHigherAmountOrder.setPartnerId(sr.partnerId);
        olderHigherAmountOrder.setOrderCode("PO-PUR_DASH-OLD-HIGH");
        olderHigherAmountOrder.setOrderDate(LocalDate.now().minusDays(8));
        olderHigherAmountOrder.setDeliveryDate(LocalDate.now().minusDays(5));
        olderHigherAmountOrder.setTotalAmount(new BigDecimal("130000.00"));
        olderHigherAmountOrder.setApprovalStatus("APPROVED");
        olderHigherAmountOrder.setOrderStatus("APPROVED");
        purchaseOrderMapper.insert(olderHigherAmountOrder);

        MatPurchaseOrderItem olderHigherAmountOrderItem = new MatPurchaseOrderItem();
        olderHigherAmountOrderItem.setTenantId(TENANT_ID);
        olderHigherAmountOrderItem.setProjectId(sr.projectId);
        olderHigherAmountOrderItem.setOrderId(olderHigherAmountOrder.getId());
        olderHigherAmountOrderItem.setMaterialId(sr.materialId);
        olderHigherAmountOrderItem.setMaterialName("高强螺栓-PUR_DASH");
        olderHigherAmountOrderItem.setQuantity(new BigDecimal("20.0000"));
        olderHigherAmountOrderItem.setUnit("套");
        olderHigherAmountOrderItem.setUnitPrice(new BigDecimal("6500.0000"));
        olderHigherAmountOrderItem.setAmount(new BigDecimal("130000.00"));
        purchaseOrderItemMapper.insert(olderHigherAmountOrderItem);

        MatReceipt olderLowerAmountReceipt = new MatReceipt();
        olderLowerAmountReceipt.setTenantId(TENANT_ID);
        olderLowerAmountReceipt.setProjectId(sr.projectId);
        olderLowerAmountReceipt.setPartnerId(sr.partnerId);
        olderLowerAmountReceipt.setReceiptCode("RC-PUR_DASH-OLD-LOW");
        olderLowerAmountReceipt.setReceiptDate(LocalDate.now().minusDays(3));
        olderLowerAmountReceipt.setWarehouseId(sr.warehouseId);
        olderLowerAmountReceipt.setTotalAmount(new BigDecimal("50000.00"));
        olderLowerAmountReceipt.setApprovalStatus("APPROVING");
        receiptMapper.insert(olderLowerAmountReceipt);

        MatReceipt olderHigherAmountReceipt = new MatReceipt();
        olderHigherAmountReceipt.setTenantId(TENANT_ID);
        olderHigherAmountReceipt.setProjectId(sr.projectId);
        olderHigherAmountReceipt.setPartnerId(sr.partnerId);
        olderHigherAmountReceipt.setReceiptCode("RC-PUR_DASH-OLD-HIGH");
        olderHigherAmountReceipt.setReceiptDate(LocalDate.now().minusDays(3));
        olderHigherAmountReceipt.setWarehouseId(sr.warehouseId);
        olderHigherAmountReceipt.setTotalAmount(new BigDecimal("90000.00"));
        olderHigherAmountReceipt.setApprovalStatus("APPROVING");
        receiptMapper.insert(olderHigherAmountReceipt);

        MatReceiptItem olderHigherAmountReceiptItem = new MatReceiptItem();
        olderHigherAmountReceiptItem.setTenantId(TENANT_ID);
        olderHigherAmountReceiptItem.setReceiptId(olderHigherAmountReceipt.getId());
        olderHigherAmountReceiptItem.setMaterialId(sr.materialId);
        olderHigherAmountReceiptItem.setActualQuantity(new BigDecimal("8.0000"));
        olderHigherAmountReceiptItem.setQualifiedQuantity(new BigDecimal("8.0000"));
        olderHigherAmountReceiptItem.setUnitPrice(new BigDecimal("11250.0000"));
        olderHigherAmountReceiptItem.setAmount(new BigDecimal("90000.00"));
        receiptItemMapper.insert(olderHigherAmountReceiptItem);

        PurchaseManagerDashboardVO vo = dashboardService.getPurchaseManagerView(sr.projectId);

        assertNotNull(vo);
        assertEquals(sr.projectId.toString(), vo.getProjectId());
        assertEquals(1L, vo.getPendingRequestCount());
        assertEquals(4L, vo.getActiveOrderCount());
        assertEquals(3L, vo.getOverdueDeliveryCount());
        assertEquals(3L, vo.getPendingReceiptCount());
        assertEquals(1L, vo.getLowStockItemCount());
        assertEquals("500000.00", vo.getTotalOrderAmount());

        assertNotNull(vo.getPurchaseOrders());
        assertFalse(vo.getPurchaseOrders().isEmpty());
        assertEquals("PO-PUR_DASH-NEWEST", vo.getPurchaseOrders().get(0).getCode());
        assertTrue(vo.getPurchaseOrders().stream()
                .allMatch(item -> "PURCHASE_ORDER".equals(item.getSourceType())));
        assertTrue(vo.getPurchaseOrders().stream()
                .noneMatch(item -> item.getCode() != null && item.getCode().startsWith("RC-")));

        DashboardBusinessItemVO request = vo.getRecentRequests().get(0);
        assertEquals("PR-PUR_DASH", request.getCode());
        assertEquals("钢筋-PUR_DASH", request.getTitle());
        assertEquals("钢筋-PUR_DASH", request.getItemSummary());
        assertEquals(sr.projectName, request.getProjectName());
        assertEquals("抄送用户1", request.getOwnerName());
        assertNull(request.getAmount(), "采购申请无真实金额字段时不返回假金额");

        DashboardBusinessItemVO overdueOrder = vo.getOverdueOrders().stream()
                .filter(i -> "PO-PUR_DASH-OLD-HIGH".equals(i.getCode()))
                .findFirst()
                .orElseThrow();
        assertEquals("PO-PUR_DASH-OLD-HIGH", overdueOrder.getCode());
        assertEquals("高强螺栓-PUR_DASH", overdueOrder.getTitle());
        assertEquals("高强螺栓-PUR_DASH", overdueOrder.getItemSummary());
        assertEquals("供应商-PUR_DASH", overdueOrder.getPartnerName());
        assertEquals(5L, overdueOrder.getOverdueDays());
        assertEquals("130000.00", overdueOrder.getAmount());

        DashboardBusinessItemVO pendingReceipt = vo.getPendingReceipts().stream()
                .filter(i -> "RC-PUR_DASH-OLD-HIGH".equals(i.getCode()))
                .findFirst()
                .orElseThrow();
        assertEquals("RC-PUR_DASH-OLD-HIGH", pendingReceipt.getCode());
        assertEquals("钢筋-PUR_DASH", pendingReceipt.getTitle());
        assertEquals("钢筋-PUR_DASH", pendingReceipt.getItemSummary());
        assertEquals("供应商-PUR_DASH", pendingReceipt.getPartnerName());
        assertEquals(3L, pendingReceipt.getPendingDays());
        assertEquals("90000.00", pendingReceipt.getAmount());
    }

    @Test
    @Transactional
    @DisplayName("3.2a Default demo project: purchase orders are limited, sorted, and include long summary")
    void testDefaultDemoProject_PurchaseOrdersOverflowDemoData() {
        setAdminContext();

        PurchaseManagerDashboardVO vo = dashboardService.getPurchaseManagerView(10001L);
        List<DashboardBusinessItemVO> purchaseOrders = vo.getPurchaseOrders();
        Long candidateCount = purchaseOrderMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MatPurchaseOrder>()
                        .eq(MatPurchaseOrder::getTenantId, TENANT_ID)
                        .eq(MatPurchaseOrder::getProjectId, 10001L)
                        .eq(MatPurchaseOrder::getDeletedFlag, 0));

        assertEquals(5, purchaseOrders.size());
        assertTrue(candidateCount > 5L);
        assertIterableEquals(List.of(
                "PO-DEMO-PUR-OVERFLOW-001",
                "PO-DEMO-PUR-OVERFLOW-002",
                "PO-DEMO-PUR-OVERFLOW-003",
                "PO-DEMO-PUR-OVERFLOW-004",
                "PO-DEMO-PUR-OVERFLOW-005"
        ), purchaseOrders.stream().map(DashboardBusinessItemVO::getCode).toList());
        assertTrue(purchaseOrders.stream().allMatch(i -> "PURCHASE_ORDER".equals(i.getSourceType())));
        assertTrue(purchaseOrders.stream().allMatch(i -> i.getCode() != null && i.getCode().startsWith("PO-")));
        assertTrue(purchaseOrders.stream().noneMatch(i -> i.getCode().startsWith("RC-")));
        assertTrue(purchaseOrders.stream().anyMatch(i -> i.getItemSummary() != null
                && i.getItemSummary().contains("超长摘要")
                && i.getItemSummary().length() > 50));
    }

    @Test
    @Transactional
    @DisplayName("3.2b Default demo project: V105 realistic purchase and production seed is readable")
    void testDefaultDemoProject_DashboardRealisticDemoDistribution() {
        setAdminContext();

        MdMaterial longSummaryMaterial = new MdMaterial();
        longSummaryMaterial.setTenantId(TENANT_ID);
        longSummaryMaterial.setMaterialCode("MAT-DEMO-LONG-SUMMARY");
        longSummaryMaterial.setMaterialName("超长摘要-驾驶舱测试补充采购申请摘要用于验证最近请求展示以及额外字符超过三十个");
        longSummaryMaterial.setUnit("批");
        longSummaryMaterial.setStatus("ENABLE");
        materialMapper.insert(longSummaryMaterial);

        MatPurchaseRequest longSummaryRequest = new MatPurchaseRequest();
        longSummaryRequest.setTenantId(TENANT_ID);
        longSummaryRequest.setProjectId(10001L);
        longSummaryRequest.setContractId(30001L);
        longSummaryRequest.setRequestCode("PR-DEMO-LONG-SUMMARY");
        longSummaryRequest.setApprovalStatus("APPROVING");
        longSummaryRequest.setStatus("DRAFT");
        longSummaryRequest.setCreatedBy(1L);
        purchaseRequestMapper.insert(longSummaryRequest);

        MatPurchaseRequestItem longSummaryItem = new MatPurchaseRequestItem();
        longSummaryItem.setTenantId(TENANT_ID);
        longSummaryItem.setRequestId(longSummaryRequest.getId());
        longSummaryItem.setMaterialId(longSummaryMaterial.getId());
        longSummaryItem.setQuantity(new BigDecimal("1.0000"));
        longSummaryItem.setUnit("批");
        longSummaryItem.setPlannedDate(LocalDate.now().plusDays(3));
        purchaseRequestItemMapper.insert(longSummaryItem);

        PurchaseManagerDashboardVO purchase = dashboardService.getPurchaseManagerView(10001L);
        ProductionManagerDashboardVO production = dashboardService.getProductionManagerView(10001L);

        assertEquals(5, purchase.getRecentRequests().size());
        assertEquals(5, purchase.getOverdueOrders().size());
        assertEquals(5, purchase.getPendingReceipts().size());
        assertTrue(purchase.getRecentRequests().stream().anyMatch(i -> "PR-DEMO-LONG-SUMMARY".equals(i.getCode())
                && i.getItemSummary() != null
                && i.getItemSummary().contains("超长摘要")
                && i.getItemSummary().length() > 30));

        List<MatPurchaseRequest> requests = purchaseRequestMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MatPurchaseRequest>()
                        .eq(MatPurchaseRequest::getTenantId, TENANT_ID)
                        .likeRight(MatPurchaseRequest::getRequestCode, "PR-DEMO-REAL-")
                        .eq(MatPurchaseRequest::getDeletedFlag, 0));
        assertEquals(6, requests.size());
        assertTrue(requests.stream().map(MatPurchaseRequest::getApprovalStatus).distinct().count() >= 3);

        Long overdueOrderCount = purchaseOrderMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MatPurchaseOrder>()
                        .eq(MatPurchaseOrder::getTenantId, TENANT_ID)
                        .likeRight(MatPurchaseOrder::getOrderCode, "PO-DEMO-REAL-OVD-")
                        .eq(MatPurchaseOrder::getDeletedFlag, 0));
        Long receiptCount = receiptMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MatReceipt>()
                        .eq(MatReceipt::getTenantId, TENANT_ID)
                        .likeRight(MatReceipt::getReceiptCode, "RC-DEMO-REAL-")
                        .eq(MatReceipt::getDeletedFlag, 0));
        assertEquals(5L, overdueOrderCount);
        assertEquals(6L, receiptCount);

        assertEquals(5, production.getRecentReceipts().size());
        assertEquals(5, production.getRecentRequisitions().size());
        assertEquals(5, production.getRecentSubMeasures().size());
        Long realisticDemoLowStockCount = stockMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MatStock>()
                        .between(MatStock::getId, 970000000000006101L, 970000000000006105L)
                        .le(MatStock::getAvailableQty, BigDecimal.ZERO));
        assertEquals(5L, realisticDemoLowStockCount, "V105 应提供 5 条固定低库存演示数据");
        assertNotNull(production.getLowStockItemCount(), "生产经理视图应返回低库存聚合字段");
        assertTrue(production.getRecentReceipts().stream().anyMatch(i -> i.getItemSummary() != null
                && i.getItemSummary().contains("施工部位")
                && i.getItemSummary().length() > 30));

        List<MatRequisition> requisitions = requisitionMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MatRequisition>()
                        .eq(MatRequisition::getTenantId, TENANT_ID)
                        .likeRight(MatRequisition::getRequisitionCode, "REQ-DEMO-REAL-")
                        .eq(MatRequisition::getDeletedFlag, 0));
        List<SubMeasure> measures = subMeasureMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SubMeasure>()
                        .eq(SubMeasure::getTenantId, TENANT_ID)
                        .likeRight(SubMeasure::getMeasureCode, "SM-DEMO-REAL-")
                        .eq(SubMeasure::getDeletedFlag, 0));
        assertEquals(5, requisitions.size());
        assertEquals(5, measures.size());
        assertTrue(requisitions.stream().map(MatRequisition::getApprovalStatus).distinct().count() >= 3);
        assertTrue(measures.stream().map(SubMeasure::getStatus).distinct().count() >= 3);
    }

    @Test
    @Transactional
    @DisplayName("3.3 Production manager MVP view: uses receipt, requisition, sub-measure and stock signals")
    void testProductionManagerView_MvpSignals() {
        SeedResult sr = seed("PROD_DASH");

        MatReceipt noSummaryReceipt = new MatReceipt();
        noSummaryReceipt.setTenantId(TENANT_ID);
        noSummaryReceipt.setProjectId(sr.projectId);
        noSummaryReceipt.setPartnerId(sr.partnerId);
        noSummaryReceipt.setReceiptCode("RC-PROD_DASH-NO-SUMMARY");
        noSummaryReceipt.setReceiptDate(LocalDate.now());
        noSummaryReceipt.setWarehouseId(sr.warehouseId);
        noSummaryReceipt.setReceiverId(sr.signalUserId);
        noSummaryReceipt.setQualityStatus("PENDING");
        noSummaryReceipt.setTotalAmount(new BigDecimal("1000.00"));
        noSummaryReceipt.setApprovalStatus("APPROVING");
        receiptMapper.insert(noSummaryReceipt);

        ProductionManagerDashboardVO vo = dashboardService.getProductionManagerView(sr.projectId);

        assertNotNull(vo);
        assertEquals(sr.projectId.toString(), vo.getProjectId());
        assertEquals(2L, vo.getReceiptCount());
        assertEquals(1L, vo.getRequisitionCount());
        assertEquals(1L, vo.getPendingStockOutCount());
        assertEquals(1L, vo.getSubMeasureCount());
        assertTrue(vo.getLowStockItemCount() >= 1L);
        assertEquals("80000.00", vo.getConfirmedMeasureAmount());

        DashboardBusinessItemVO receipt = vo.getRecentReceipts().stream()
                .filter(i -> "RC-PROD_DASH".equals(i.getCode()))
                .findFirst()
                .orElseThrow();
        assertEquals("RC-PROD_DASH", receipt.getCode());
        assertNotEquals("RC-PROD_DASH", receipt.getTitle());
        assertEquals("钢筋-PROD_DASH", receipt.getItemSummary());
        assertEquals("供应商-PROD_DASH", receipt.getPartnerName());
        assertNotNull(receipt.getOwnerName());
        assertTrue(receipt.getPendingDays() >= 1L);

        DashboardBusinessItemVO receiptWithoutSummary = vo.getRecentReceipts().stream()
                .filter(i -> "RC-PROD_DASH-NO-SUMMARY".equals(i.getCode()))
                .findFirst()
                .orElseThrow();
        assertNull(receiptWithoutSummary.getTitle());
        assertNull(receiptWithoutSummary.getItemSummary());

        DashboardBusinessItemVO requisition = vo.getRecentRequisitions().get(0);
        assertEquals("RQ-PROD_DASH", requisition.getCode());
        assertEquals("供应商-PROD_DASH", requisition.getPartnerName());
        assertNotNull(requisition.getOwnerName());
        assertEquals(0, new BigDecimal("50000.00").compareTo(new BigDecimal(requisition.getAmount())));
        assertNull(requisition.getItemSummary());

        DashboardBusinessItemVO subMeasure = vo.getRecentSubMeasures().get(0);
        assertEquals("SM-PROD_DASH", subMeasure.getCode());
        assertNull(subMeasure.getItemSummary());
        assertEquals("供应商-PROD_DASH", subMeasure.getPartnerName());
        assertEquals("80000.00", subMeasure.getAmount());

        assertTrue(vo.getRecentRequisitions().stream()
                .noneMatch(i -> "PENDING_STOCK_OUT".equals(i.getItemSummary()) || "STOCKED_OUT".equals(i.getItemSummary())));
        assertTrue(vo.getRecentSubMeasures().stream()
                .noneMatch(i -> "2026-06".equals(i.getItemSummary())));
    }

    @Test
    @Transactional
    @DisplayName("3.4 Default demo project: purchase and production dashboards are not blank")
    void testDefaultDemoProject_PurchaseAndProductionDashboardsNotBlank() {
        setAdminContext();

        PurchaseManagerDashboardVO purchase = dashboardService.getPurchaseManagerView(null);
        ProductionManagerDashboardVO production = dashboardService.getProductionManagerView(null);

        assertNotNull(purchase);
        assertEquals("全部项目", purchase.getProjectName());
        assertTrue(purchase.getPendingRequestCount() > 0L);
        assertTrue(purchase.getActiveOrderCount() > 0L);
        assertFalse(purchase.getRecentRequests().isEmpty());
        assertFalse(purchase.getPurchaseOrders().isEmpty());
        assertTrue(purchase.getPurchaseOrders().stream()
                .allMatch(item -> "PURCHASE_ORDER".equals(item.getSourceType())));
        assertTrue(purchase.getPurchaseOrders().stream()
                .noneMatch(item -> item.getCode() != null && item.getCode().startsWith("RC-")));
        assertFalse(purchase.getOverdueOrders().isEmpty());
        assertFalse(purchase.getPendingReceipts().isEmpty());

        assertNotNull(production);
        assertEquals("全部项目", production.getProjectName());
        assertTrue(production.getReceiptCount() > 0L);
        assertTrue(production.getRequisitionCount() > 0L);
        assertTrue(production.getPendingStockOutCount() > 0L);
        assertTrue(production.getSubMeasureCount() > 0L);
        assertFalse(production.getRecentReceipts().isEmpty());
        assertFalse(production.getRecentRequisitions().isEmpty());
        assertFalse(production.getRecentSubMeasures().isEmpty());
    }

    @Test
    @Transactional
    @DisplayName("8.2 Purchase view: accepts month parameter and filters requests/orders/receipts")
    void testPurchaseView_WithMonthParameter() {
        SeedResult sr = seed("PUR_MONTH");
        String currentMonth = LocalDate.now().toString().substring(0, 7);

        PurchaseManagerDashboardVO vo = dashboardService.getPurchaseManagerView(sr.projectId, currentMonth);

        assertNotNull(vo);
        assertTrue(vo.getPendingRequestCount() >= 1, "Should have requests in current month");

        // Test future month filters all
        String futureMonth = LocalDate.now().plusMonths(12).toString().substring(0, 7);
        PurchaseManagerDashboardVO futureVo = dashboardService.getPurchaseManagerView(sr.projectId, futureMonth);
        assertEquals(0L, futureVo.getPendingRequestCount(), "Future month should have 0 purchase requests");
        assertEquals(0L, futureVo.getActiveOrderCount(), "Future month should have 0 active orders");
        assertEquals("0", futureVo.getTotalOrderAmount(), "Future month should have 0 order amount");
        // lowStock is NOT month-filtered (current inventory state)
        assertEquals(vo.getLowStockItemCount(), futureVo.getLowStockItemCount(), "LowStock should not be month-filtered");
    }

    @Test
    @Transactional
    @DisplayName("8.2a Purchase view: invalid month returns data without 500")
    void testPurchaseView_InvalidMonthDoesNotThrow() {
        SeedResult sr = seed("PUR_BAD_MONTH");
        PurchaseManagerDashboardVO vo = dashboardService.getPurchaseManagerView(sr.projectId, "garbage");
        assertNotNull(vo);
        assertTrue(vo.getActiveOrderCount() >= 1, "Invalid month should be ignored");
    }

    @Test
    @Transactional
    @DisplayName("8.2b Purchase view: overdueOrders uses deliveryDate scope NOT orderDate pre-filter")
    void testPurchaseView_OverdueUsesDeliveryDateScope() {
        SeedResult sr = seed("PUR_OVERDUE_DLV");
        String currentMonth = LocalDate.now().toString().substring(0, 7);
        String lastMonth = LocalDate.now().minusMonths(1).toString().substring(0, 7);

        // Order with orderDate=lastMonth, deliveryDate=currentMonth (overdue)
        MatPurchaseOrder crossOrder = new MatPurchaseOrder();
        crossOrder.setTenantId(TENANT_ID);
        crossOrder.setProjectId(sr.projectId);
        crossOrder.setPartnerId(sr.partnerId);
        crossOrder.setOrderCode("PO-CROSS-DLV");
        crossOrder.setOrderDate(LocalDate.now().minusMonths(1));         // last month
        crossOrder.setDeliveryDate(LocalDate.now().minusDays(1));        // current month, overdue
        crossOrder.setTotalAmount(new BigDecimal("50000.00"));
        crossOrder.setApprovalStatus("APPROVED");
        crossOrder.setOrderStatus("APPROVED");
        purchaseOrderMapper.insert(crossOrder);

        MatPurchaseOrderItem crossOrderItem = new MatPurchaseOrderItem();
        crossOrderItem.setTenantId(TENANT_ID);
        crossOrderItem.setProjectId(sr.projectId);
        crossOrderItem.setOrderId(crossOrder.getId());
        crossOrderItem.setMaterialId(sr.materialId);
        crossOrderItem.setMaterialName("钢筋-CROSS-DLV");
        crossOrderItem.setQuantity(new BigDecimal("5.0000"));
        crossOrderItem.setUnit("吨");
        crossOrderItem.setUnitPrice(new BigDecimal("10000.0000"));
        crossOrderItem.setAmount(new BigDecimal("50000.00"));
        purchaseOrderItemMapper.insert(crossOrderItem);

        // Query by lastMonth: only cross-order matches by orderDate
        PurchaseManagerDashboardVO lastMonthVo = dashboardService.getPurchaseManagerView(sr.projectId, lastMonth);
        assertTrue(lastMonthVo.getActiveOrderCount() >= 1, "Last month should include cross order by orderDate");
        assertTrue(lastMonthVo.getPurchaseOrders().stream()
                .anyMatch(o -> "PO-CROSS-DLV".equals(o.getCode())),
                "Cross order should appear in purchaseOrders when filtered by lastMonth");

        // Query by currentMonth: orderDate does NOT match → NOT in purchaseOrders
        PurchaseManagerDashboardVO currentMonthVo = dashboardService.getPurchaseManagerView(sr.projectId, currentMonth);
        assertTrue(currentMonthVo.getPurchaseOrders().stream()
                .noneMatch(o -> "PO-CROSS-DLV".equals(o.getCode())),
                "Cross order should NOT appear in purchaseOrders (orderDate not in current month)");

        // CRITICAL: deliveryDate IS in current month → MUST appear in overdueOrders
        assertTrue(currentMonthVo.getOverdueOrders().stream()
                .anyMatch(o -> "PO-CROSS-DLV".equals(o.getCode())),
                "Cross order MUST appear in overdueOrders because deliveryDate is in current month");
        assertTrue(currentMonthVo.getOverdueDeliveryCount() >= 1,
                "overdueDeliveryCount should count orders with deliveryDate in selected month");
    }

    @Test
    @Transactional
    @DisplayName("8.5 Purchase view: exposes supplier delivery score from existing purchase orders")
    void testPurchaseView_SupplierScores() {
        SeedResult sr = seed("PUR_SUPPLIER_SCORE");

        PurchaseManagerDashboardVO vo = dashboardService.getPurchaseManagerView(sr.projectId);

        DashboardSupplierScoreVO score = vo.getSupplierScores().stream()
                .filter(i -> sr.partnerId.toString().equals(i.getPartnerId()))
                .findFirst()
                .orElseThrow();
        assertEquals("供应商-PUR_SUPPLIER_SCORE", score.getPartnerName());
        assertEquals(1L, score.getOrderCount());
        assertEquals(1L, score.getOverdueOrderCount());
        assertEquals("0.00", score.getOnTimeDeliveryRate());
        assertEquals("0", score.getPerformanceScore());
    }

    @Test
    @Transactional
    @DisplayName("8.5a Purchase view: supplier scores keep project, overdue, empty and stable-order boundaries")
    void testPurchaseView_SupplierScoreBoundaries() {
        SeedResult sr = seed("PUR_SUPPLIER_BOUNDARY");
        SeedResult otherProject = seed("PUR_SUPPLIER_OTHER_PROJECT");

        MdPartner first = new MdPartner();
        first.setId(4_294_967_327L);
        first.setTenantId(TENANT_ID);
        first.setPartnerCode("PT-PUR-SCORE-FIRST");
        first.setPartnerName("同名供应商");
        first.setPartnerType("SUPPLIER");
        first.setStatus("ENABLE");
        partnerMapper.insert(first);

        MdPartner second = new MdPartner();
        second.setId(4_294_967_328L);
        second.setTenantId(TENANT_ID);
        second.setPartnerCode("PT-PUR-SCORE-SECOND");
        second.setPartnerName("同名供应商");
        second.setPartnerType("SUPPLIER");
        second.setStatus("ENABLE");
        partnerMapper.insert(second);

        MdPartner noOrders = new MdPartner();
        noOrders.setTenantId(TENANT_ID);
        noOrders.setPartnerCode("PT-PUR-SCORE-NO-ORDERS");
        noOrders.setPartnerName("零订单供应商");
        noOrders.setPartnerType("SUPPLIER");
        noOrders.setStatus("ENABLE");
        partnerMapper.insert(noOrders);

        LocalDate cohortDeliveryDate = LocalDate.now().withDayOfMonth(1).minusDays(1);

        MatPurchaseOrder completedLate = new MatPurchaseOrder();
        completedLate.setTenantId(TENANT_ID);
        completedLate.setProjectId(sr.projectId);
        completedLate.setPartnerId(first.getId());
        completedLate.setOrderCode("PO-PUR-SCORE-COMPLETED");
        completedLate.setOrderDate(cohortDeliveryDate.minusMonths(1));
        completedLate.setDeliveryDate(cohortDeliveryDate);
        completedLate.setApprovalStatus("APPROVED");
        completedLate.setOrderStatus("COMPLETED");
        purchaseOrderMapper.insert(completedLate);

        MatPurchaseOrderItem completedLateItem = new MatPurchaseOrderItem();
        completedLateItem.setTenantId(TENANT_ID);
        completedLateItem.setProjectId(sr.projectId);
        completedLateItem.setOrderId(completedLate.getId());
        completedLateItem.setMaterialId(sr.materialId);
        completedLateItem.setQuantity(new BigDecimal("10.0000"));
        purchaseOrderItemMapper.insert(completedLateItem);

        MatReceipt completedLateReceipt = new MatReceipt();
        completedLateReceipt.setTenantId(TENANT_ID);
        completedLateReceipt.setProjectId(sr.projectId);
        completedLateReceipt.setOrderId(completedLate.getId());
        completedLateReceipt.setReceiptCode("RC-PUR-SCORE-COMPLETED");
        completedLateReceipt.setReceiptDate(cohortDeliveryDate.plusDays(1));
        completedLateReceipt.setApprovalStatus("APPROVED");
        receiptMapper.insert(completedLateReceipt);

        MatReceiptItem completedLateReceiptItem = new MatReceiptItem();
        completedLateReceiptItem.setTenantId(TENANT_ID);
        completedLateReceiptItem.setReceiptId(completedLateReceipt.getId());
        completedLateReceiptItem.setOrderItemId(completedLateItem.getId());
        completedLateReceiptItem.setActualQuantity(new BigDecimal("6.0000"));
        completedLateReceiptItem.setQualifiedQuantity(new BigDecimal("6.0000"));
        receiptItemMapper.insert(completedLateReceiptItem);

        MatReceipt earlyApprovedReceipt = new MatReceipt();
        earlyApprovedReceipt.setTenantId(TENANT_ID);
        earlyApprovedReceipt.setProjectId(sr.projectId);
        earlyApprovedReceipt.setOrderId(completedLate.getId());
        earlyApprovedReceipt.setReceiptCode("RC-PUR-SCORE-EARLY-PARTIAL");
        earlyApprovedReceipt.setReceiptDate(cohortDeliveryDate.minusDays(1));
        earlyApprovedReceipt.setApprovalStatus("APPROVED");
        receiptMapper.insert(earlyApprovedReceipt);
        MatReceiptItem earlyApprovedItem = new MatReceiptItem();
        earlyApprovedItem.setTenantId(TENANT_ID);
        earlyApprovedItem.setReceiptId(earlyApprovedReceipt.getId());
        earlyApprovedItem.setOrderItemId(completedLateItem.getId());
        earlyApprovedItem.setActualQuantity(new BigDecimal("4.0000"));
        earlyApprovedItem.setQualifiedQuantity(new BigDecimal("4.0000"));
        receiptItemMapper.insert(earlyApprovedItem);

        MatReceipt ignoredDraftReceipt = new MatReceipt();
        ignoredDraftReceipt.setTenantId(TENANT_ID);
        ignoredDraftReceipt.setProjectId(sr.projectId);
        ignoredDraftReceipt.setOrderId(completedLate.getId());
        ignoredDraftReceipt.setReceiptCode("RC-PUR-SCORE-DRAFT");
        ignoredDraftReceipt.setReceiptDate(cohortDeliveryDate.minusDays(2));
        ignoredDraftReceipt.setApprovalStatus("DRAFT");
        receiptMapper.insert(ignoredDraftReceipt);
        MatReceiptItem ignoredDraftItem = new MatReceiptItem();
        ignoredDraftItem.setTenantId(TENANT_ID);
        ignoredDraftItem.setReceiptId(ignoredDraftReceipt.getId());
        ignoredDraftItem.setOrderItemId(completedLateItem.getId());
        ignoredDraftItem.setActualQuantity(new BigDecimal("10.0000"));
        ignoredDraftItem.setQualifiedQuantity(new BigDecimal("10.0000"));
        receiptItemMapper.insert(ignoredDraftItem);

        MatPurchaseOrder firstOnTime = new MatPurchaseOrder();
        firstOnTime.setTenantId(TENANT_ID);
        firstOnTime.setProjectId(sr.projectId);
        firstOnTime.setPartnerId(first.getId());
        firstOnTime.setOrderCode("PO-PUR-SCORE-FIRST");
        firstOnTime.setOrderDate(LocalDate.now());
        firstOnTime.setDeliveryDate(LocalDate.now().plusDays(1));
        firstOnTime.setApprovalStatus("APPROVED");
        firstOnTime.setOrderStatus("APPROVED");
        purchaseOrderMapper.insert(firstOnTime);

        MatPurchaseOrder secondOnTime = new MatPurchaseOrder();
        secondOnTime.setTenantId(TENANT_ID);
        secondOnTime.setProjectId(sr.projectId);
        secondOnTime.setPartnerId(second.getId());
        secondOnTime.setOrderCode("PO-PUR-SCORE-SECOND");
        secondOnTime.setOrderDate(LocalDate.now());
        secondOnTime.setDeliveryDate(LocalDate.now().plusDays(1));
        secondOnTime.setApprovalStatus("APPROVED");
        secondOnTime.setOrderStatus("APPROVED");
        purchaseOrderMapper.insert(secondOnTime);

        MatPurchaseOrder partial = new MatPurchaseOrder();
        partial.setTenantId(TENANT_ID);
        partial.setProjectId(sr.projectId);
        partial.setPartnerId(first.getId());
        partial.setOrderCode("PO-PUR-SCORE-PARTIAL");
        partial.setOrderDate(LocalDate.now());
        partial.setDeliveryDate(cohortDeliveryDate);
        partial.setApprovalStatus("APPROVED");
        partial.setOrderStatus("APPROVED");
        purchaseOrderMapper.insert(partial);

        MatPurchaseOrderItem partialItem = new MatPurchaseOrderItem();
        partialItem.setTenantId(TENANT_ID);
        partialItem.setProjectId(sr.projectId);
        partialItem.setOrderId(partial.getId());
        partialItem.setMaterialId(sr.materialId);
        partialItem.setQuantity(new BigDecimal("10.0000"));
        purchaseOrderItemMapper.insert(partialItem);

        MatReceipt partialReceipt = new MatReceipt();
        partialReceipt.setTenantId(TENANT_ID);
        partialReceipt.setProjectId(sr.projectId);
        partialReceipt.setOrderId(partial.getId());
        partialReceipt.setReceiptCode("RC-PUR-SCORE-PARTIAL");
        partialReceipt.setReceiptDate(cohortDeliveryDate.plusDays(1));
        partialReceipt.setApprovalStatus("APPROVED");
        receiptMapper.insert(partialReceipt);

        MatReceiptItem partialReceiptItem = new MatReceiptItem();
        partialReceiptItem.setTenantId(TENANT_ID);
        partialReceiptItem.setReceiptId(partialReceipt.getId());
        partialReceiptItem.setOrderItemId(partialItem.getId());
        partialReceiptItem.setActualQuantity(new BigDecimal("5.0000"));
        partialReceiptItem.setQualifiedQuantity(new BigDecimal("5.0000"));
        receiptItemMapper.insert(partialReceiptItem);

        MatPurchaseOrder onTime = new MatPurchaseOrder();
        onTime.setTenantId(TENANT_ID);
        onTime.setProjectId(sr.projectId);
        onTime.setPartnerId(first.getId());
        onTime.setOrderCode("PO-PUR-SCORE-ON-TIME");
        onTime.setOrderDate(cohortDeliveryDate.minusMonths(1));
        onTime.setDeliveryDate(cohortDeliveryDate);
        onTime.setApprovalStatus("APPROVED");
        onTime.setOrderStatus("COMPLETED");
        purchaseOrderMapper.insert(onTime);

        MatPurchaseOrderItem onTimeItem = new MatPurchaseOrderItem();
        onTimeItem.setTenantId(TENANT_ID);
        onTimeItem.setProjectId(sr.projectId);
        onTimeItem.setOrderId(onTime.getId());
        onTimeItem.setMaterialId(sr.materialId);
        onTimeItem.setQuantity(new BigDecimal("10.0000"));
        purchaseOrderItemMapper.insert(onTimeItem);

        MatReceipt onTimeReceipt = new MatReceipt();
        onTimeReceipt.setTenantId(TENANT_ID);
        onTimeReceipt.setProjectId(sr.projectId);
        onTimeReceipt.setOrderId(onTime.getId());
        onTimeReceipt.setReceiptCode("RC-PUR-SCORE-ON-TIME");
        onTimeReceipt.setReceiptDate(cohortDeliveryDate.minusDays(1));
        onTimeReceipt.setApprovalStatus("APPROVED");
        receiptMapper.insert(onTimeReceipt);

        MatReceiptItem onTimeReceiptItem = new MatReceiptItem();
        onTimeReceiptItem.setTenantId(TENANT_ID);
        onTimeReceiptItem.setReceiptId(onTimeReceipt.getId());
        onTimeReceiptItem.setOrderItemId(onTimeItem.getId());
        onTimeReceiptItem.setActualQuantity(new BigDecimal("10.0000"));
        onTimeReceiptItem.setQualifiedQuantity(new BigDecimal("10.0000"));
        receiptItemMapper.insert(onTimeReceiptItem);

        MatPurchaseOrder emptySupplier = new MatPurchaseOrder();
        emptySupplier.setTenantId(TENANT_ID);
        emptySupplier.setProjectId(sr.projectId);
        emptySupplier.setOrderCode("PO-PUR-SCORE-NO-SUPPLIER");
        emptySupplier.setOrderDate(LocalDate.now());
        emptySupplier.setDeliveryDate(LocalDate.now().minusDays(1));
        emptySupplier.setApprovalStatus("APPROVED");
        emptySupplier.setOrderStatus("APPROVED");
        purchaseOrderMapper.insert(emptySupplier);

        PurchaseManagerDashboardVO vo = dashboardService.getPurchaseManagerView(
                sr.projectId, YearMonth.from(cohortDeliveryDate).toString());
        List<DashboardSupplierScoreVO> scores = vo.getSupplierScores();

        assertTrue(scores.stream().noneMatch(i -> otherProject.partnerId.toString().equals(i.getPartnerId())),
                "Other project supplier must not leak into scores");
        assertTrue(scores.stream().noneMatch(i -> noOrders.getId().toString().equals(i.getPartnerId())),
                "Supplier without orders must not create a zero-denominator score");
        assertTrue(scores.stream().noneMatch(i -> i.getPartnerId() == null),
                "Order without supplier must not create a synthetic score");

        DashboardSupplierScoreVO completedLateScore = scores.stream()
                .filter(i -> first.getId().toString().equals(i.getPartnerId()))
                .findFirst()
                .orElseThrow();
        assertEquals(3L, completedLateScore.getOrderCount(),
                "Delivery cohort must use deliveryDate instead of orderDate");
        assertEquals(1L, completedLateScore.getLateCompletedCount(),
                "Completed after the planned delivery date must be classified as late completed");
        assertEquals(1L, completedLateScore.getOverdueIncompleteCount(),
                "Partially received order must be classified as overdue incomplete");
        assertEquals(2L, completedLateScore.getOverdueOrderCount(),
                "Legacy non-on-time count must remain the sum of both late categories");
        assertEquals("33.33", completedLateScore.getOnTimeDeliveryRate());
        assertTrue(scores.stream().noneMatch(i -> second.getId().toString().equals(i.getPartnerId())),
                "Future incomplete orders must not enter the score denominator");
    }

    @Test
    @Transactional
    @DisplayName("8.5b Purchase view: project data scope blocks hidden project aggregates")
    void testPurchaseView_RespectsProjectDataScope() {
        SeedResult visible = seed("PUR_SCOPE_VISIBLE");
        SeedResult hidden = seed("PUR_SCOPE_HIDDEN");
        long scopedUserId = 88_001L;

        PmProject visibleProject = projectMapper.selectById(visible.projectId);
        visibleProject.setCreatedBy(scopedUserId);
        visibleProject.setProjectManagerId(null);
        projectMapper.updateById(visibleProject);
        PmProject hiddenProject = projectMapper.selectById(hidden.projectId);
        hiddenProject.setCreatedBy(scopedUserId + 1);
        hiddenProject.setProjectManagerId(null);
        projectMapper.updateById(hiddenProject);

        String roleCode = "DASH_SELF_" + System.nanoTime();
        SysRole role = new SysRole();
        role.setTenantId(TENANT_ID);
        role.setRoleCode(roleCode);
        role.setRoleName("Dashboard SELF scope");
        role.setRoleType("CUSTOM");
        role.setStatus("ENABLE");
        role.setDataScope("SELF");
        sysRoleMapper.insert(role);
        TestUserContext.setUser(TENANT_ID, scopedUserId, "dashboard-self", List.of(roleCode));

        BusinessException denied = assertThrows(BusinessException.class,
                () -> dashboardService.getPurchaseManagerView(hidden.projectId));
        assertEquals("PROJECT_ACCESS_DENIED", denied.getCode());

        PurchaseManagerDashboardVO allVisible = dashboardService.getPurchaseManagerView(null);
        assertTrue(allVisible.getSupplierScores().stream()
                .anyMatch(i -> visible.partnerId.toString().equals(i.getPartnerId())),
                "SELF scope must keep visible project aggregates");
        assertTrue(allVisible.getSupplierScores().stream()
                .noneMatch(i -> hidden.partnerId.toString().equals(i.getPartnerId())),
                "Tenant-wide dashboard must filter projects outside SELF scope");
    }

    @Test
    @Transactional
    @DisplayName("8.3 Production view: accepts month parameter and filters receipts/requisitions/measures")
    void testProductionView_WithMonthParameter() {
        SeedResult sr = seed("PROD_MONTH");
        String currentMonth = LocalDate.now().toString().substring(0, 7);

        ProductionManagerDashboardVO vo = dashboardService.getProductionManagerView(sr.projectId, currentMonth);

        assertNotNull(vo);
        assertTrue(vo.getReceiptCount() >= 1, "Should have receipts in current month");

        String futureMonth = LocalDate.now().plusMonths(12).toString().substring(0, 7);
        ProductionManagerDashboardVO futureVo = dashboardService.getProductionManagerView(sr.projectId, futureMonth);
        assertEquals(0L, futureVo.getReceiptCount(), "Future month should have 0 receipts");
        assertEquals(0L, futureVo.getRequisitionCount(), "Future month should have 0 requisitions");
        assertEquals(0L, futureVo.getSubMeasureCount(), "Future month should have 0 measures");
        assertEquals("0", futureVo.getConfirmedMeasureAmount(), "Future month should have 0 confirmed amount");
        // lowStock is NOT month-filtered
        assertEquals(vo.getLowStockItemCount(), futureVo.getLowStockItemCount(), "LowStock should not be month-filtered");
    }

    @Test
    @Transactional
    @DisplayName("8.3a Production view: invalid month returns data without 500")
    void testProductionView_InvalidMonthDoesNotThrow() {
        SeedResult sr = seed("PROD_BAD_MONTH");
        ProductionManagerDashboardVO vo = dashboardService.getProductionManagerView(sr.projectId, "invalid-month");
        assertNotNull(vo);
        assertTrue(vo.getReceiptCount() >= 1, "Invalid month should be ignored");
    }
}
