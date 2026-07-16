package com.cgcpms.receipt.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.inventory.entity.MatStockTxn;
import com.cgcpms.inventory.mapper.MatStockTxnMapper;
import com.cgcpms.purchase.entity.MatPurchaseOrder;
import com.cgcpms.purchase.entity.MatPurchaseOrderItem;
import com.cgcpms.purchase.entity.MatPurchaseRequest;
import com.cgcpms.purchase.entity.MatPurchaseRequestItem;
import com.cgcpms.purchase.mapper.MatPurchaseOrderItemMapper;
import com.cgcpms.purchase.mapper.MatPurchaseOrderMapper;
import com.cgcpms.purchase.mapper.MatPurchaseRequestItemMapper;
import com.cgcpms.purchase.mapper.MatPurchaseRequestMapper;
import com.cgcpms.procurement.service.ProcurementTraceService;
import com.cgcpms.receipt.entity.MatReceipt;
import com.cgcpms.receipt.entity.MatReceiptItem;
import com.cgcpms.receipt.mapper.MatReceiptItemMapper;
import com.cgcpms.receipt.mapper.MatReceiptMapper;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.handler.WorkflowContext;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@DisplayName("MaterialReceiptWorkflowHandler — approval lifecycle tests")
class MaterialReceiptWorkflowHandlerTest {

    private static final long USER_ADMIN = 1L;
    private static final long TENANT_0 = 0L;

    @Autowired
    private MaterialReceiptWorkflowHandler handler;

    @Autowired
    private MatReceiptMapper receiptMapper;

    @Autowired private MatReceiptItemMapper receiptItemMapper;
    @Autowired private MatPurchaseOrderMapper orderMapper;
    @Autowired private MatPurchaseOrderItemMapper orderItemMapper;
    @Autowired private MatPurchaseRequestMapper requestMapper;
    @Autowired private MatPurchaseRequestItemMapper requestItemMapper;
    @Autowired private MatStockTxnMapper stockTxnMapper;
    @Autowired private ProcurementTraceService traceService;
    @Autowired private CostItemMapper costItemMapper;

    @BeforeEach void setupContext() {
        UserContext.set(Jwts.claims().add("userId", USER_ADMIN).add("username", "admin")
                .add("tenantId", TENANT_0).add("roleCodes", List.of("ADMIN")).build());
    }
    @AfterEach void clearContext() { UserContext.clear(); }

    @Test @DisplayName("supportBusinessType -> MATERIAL_RECEIPT")
    void testSupportBusinessType() { assertEquals("MATERIAL_RECEIPT", handler.supportBusinessType()); }
    @Test @DisplayName("isCritical -> true")
    void testIsCritical() { assertTrue(handler.isCritical()); }

    @Test @Transactional @DisplayName("onApproved -> 审批通过时确认订单验收量并幂等入库")
    void testOnApproved() {
        MatPurchaseRequest request = new MatPurchaseRequest();
        request.setTenantId(TENANT_0);
        request.setProjectId(10001L);
        request.setContractId(30001L);
        request.setRequestCode("PR-RC-HDLR-" + System.nanoTime());
        request.setApprovalStatus("APPROVED");
        request.setStatus("CONVERTED");
        requestMapper.insert(request);

        MatPurchaseRequestItem requestItem = new MatPurchaseRequestItem();
        requestItem.setTenantId(TENANT_0);
        requestItem.setRequestId(request.getId());
        requestItem.setMaterialId(1L);
        requestItem.setQuantity(new BigDecimal("10.0000"));
        requestItem.setUnit("吨");
        requestItemMapper.insert(requestItem);

        MatPurchaseOrder order = new MatPurchaseOrder();
        order.setTenantId(TENANT_0);
        order.setProjectId(10001L);
        order.setRequestId(request.getId());
        order.setContractId(30001L);
        order.setPartnerId(20002L);
        order.setOrderCode("PO-RC-HDLR-" + System.nanoTime());
        order.setOrderType("PURCHASE");
        order.setOrderDate(LocalDate.now());
        order.setDeliveryDate(LocalDate.now().plusDays(7));
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setApprovalStatus("APPROVED");
        order.setOrderStatus("APPROVED");
        orderMapper.insert(order);

        MatPurchaseOrderItem orderItem = new MatPurchaseOrderItem();
        orderItem.setTenantId(TENANT_0);
        orderItem.setOrderId(order.getId());
        orderItem.setRequestItemId(requestItem.getId());
        orderItem.setProjectId(10001L);
        orderItem.setMaterialId(1L);
        orderItem.setQuantity(new BigDecimal("10.0000"));
        orderItem.setUnitPrice(new BigDecimal("10.0000"));
        orderItem.setAmount(new BigDecimal("100.00"));
        orderItem.setReceivedQuantity(BigDecimal.ZERO);
        orderItem.setVersion(0);
        orderItemMapper.insert(orderItem);

        MatReceipt receipt = new MatReceipt();
        receipt.setProjectId(10001L);
        receipt.setOrderId(order.getId());
        receipt.setContractId(30001L);
        receipt.setPartnerId(20002L);
        receipt.setWarehouseId(1L);
        receipt.setReceiptDate(LocalDate.now());
        receipt.setQualityStatus("PENDING");
        receipt.setTotalAmount(new BigDecimal("20.00"));
        receipt.setReceiptCode("RC-HDLR-" + System.nanoTime());
        receipt.setApprovalStatus("APPROVING");
        receipt.setTenantId(0L);
        receiptMapper.insert(receipt);

        MatReceiptItem receiptItem = new MatReceiptItem();
        receiptItem.setTenantId(TENANT_0);
        receiptItem.setReceiptId(receipt.getId());
        receiptItem.setOrderItemId(orderItem.getId());
        receiptItem.setMaterialId(1L);
        receiptItem.setActualQuantity(new BigDecimal("2.0000"));
        receiptItem.setQualifiedQuantity(new BigDecimal("2.0000"));
        receiptItem.setUnitPrice(new BigDecimal("10.0000"));
        receiptItem.setAmount(new BigDecimal("20.00"));
        receiptItemMapper.insert(receiptItem);

        WfInstance instance = new WfInstance();
        instance.setBusinessId(receipt.getId());
        WorkflowContext ctx = new WorkflowContext();
        ctx.setInstance(instance);

        handler.onApproved(ctx);

        MatReceipt updated = receiptMapper.selectById(receipt.getId());
        assertNotNull(updated);
        assertEquals("APPROVED", updated.getApprovalStatus());
        assertEquals(0, new BigDecimal("2.0000")
                .compareTo(orderItemMapper.selectById(orderItem.getId()).getReceivedQuantity()));
        MatStockTxn stockTxn = stockTxnMapper.selectOne(new LambdaQueryWrapper<MatStockTxn>()
                .eq(MatStockTxn::getSourceType, "MAT_RECEIPT")
                .eq(MatStockTxn::getSourceId, receipt.getId())
                .eq(MatStockTxn::getSourceLineId, receiptItem.getId()));
        assertNotNull(stockTxn);
        assertEquals(0, new BigDecimal("10.000000").compareTo(stockTxn.getUnitCost()));
        assertEquals(0, new BigDecimal("20.00").compareTo(stockTxn.getAmount()));
        var trace = traceService.byStockTransaction(stockTxn.getId());
        assertEquals(request.getId(), trace.getPurchaseRequest().getId());
        assertEquals(order.getId(), trace.getPurchaseOrder().getId());
        assertEquals(receipt.getId(), trace.getReceipt().getId());
        assertTrue(trace.getCosts().isEmpty(), "库存材料验收入库只形成库存价值，不提前确认项目成本");

        handler.onApproved(ctx);
        assertEquals(0, new BigDecimal("2.0000")
                .compareTo(orderItemMapper.selectById(orderItem.getId()).getReceivedQuantity()));
        assertEquals(1L, stockTxnMapper.selectCount(new LambdaQueryWrapper<MatStockTxn>()
                .eq(MatStockTxn::getSourceType, "MAT_RECEIPT")
                .eq(MatStockTxn::getSourceId, receipt.getId())
                .eq(MatStockTxn::getSourceLineId, receiptItem.getId())));
    }

    @Test @Transactional @DisplayName("onApproved — null businessId")
    void testOnApproved_NullBusinessId() {
        WfInstance instance = new WfInstance(); instance.setId(9101L);
        WorkflowContext ctx = new WorkflowContext(); ctx.setInstance(instance);
        assertThrows(Exception.class, () -> handler.onApproved(ctx));
    }

    @Test @Transactional @DisplayName("直耗验收 -> 不入普通库存并直接确认项目材料成本")
    void testDirectConsumptionReceiptCreatesCostWithoutStock() {
        MatPurchaseOrder order = new MatPurchaseOrder();
        order.setTenantId(TENANT_0);
        order.setProjectId(10001L);
        order.setContractId(30001L);
        order.setPartnerId(20002L);
        order.setOrderCode("PO-DIRECT-" + System.nanoTime());
        order.setOrderType("PURCHASE");
        order.setOrderDate(LocalDate.now());
        order.setDeliveryDate(LocalDate.now().plusDays(1));
        order.setTotalAmount(new BigDecimal("50.00"));
        order.setApprovalStatus("APPROVED");
        order.setOrderStatus("APPROVED");
        orderMapper.insert(order);

        MatPurchaseOrderItem orderItem = new MatPurchaseOrderItem();
        orderItem.setTenantId(TENANT_0);
        orderItem.setOrderId(order.getId());
        orderItem.setProjectId(10001L);
        orderItem.setMaterialId(1L);
        orderItem.setQuantity(new BigDecimal("5.0000"));
        orderItem.setUnitPrice(new BigDecimal("10.0000"));
        orderItem.setAmount(new BigDecimal("50.00"));
        orderItem.setReceivedQuantity(BigDecimal.ZERO);
        orderItem.setVersion(0);
        orderItemMapper.insert(orderItem);

        MatReceipt receipt = new MatReceipt();
        receipt.setTenantId(TENANT_0);
        receipt.setProjectId(10001L);
        receipt.setOrderId(order.getId());
        receipt.setContractId(30001L);
        receipt.setPartnerId(20002L);
        receipt.setReceiptCode("RC-DIRECT-" + System.nanoTime());
        receipt.setReceiptDate(LocalDate.now());
        receipt.setReceiptMode("DIRECT_CONSUMPTION");
        receipt.setQualityStatus("QUALIFIED");
        receipt.setTotalAmount(new BigDecimal("50.00"));
        receipt.setApprovalStatus("APPROVING");
        receiptMapper.insert(receipt);

        MatReceiptItem item = new MatReceiptItem();
        item.setTenantId(TENANT_0);
        item.setReceiptId(receipt.getId());
        item.setOrderItemId(orderItem.getId());
        item.setMaterialId(1L);
        item.setActualQuantity(new BigDecimal("5.0000"));
        item.setQualifiedQuantity(new BigDecimal("5.0000"));
        item.setUnitPrice(new BigDecimal("10.0000"));
        item.setAmount(new BigDecimal("50.00"));
        item.setUseLocation("一层基础垫层");
        receiptItemMapper.insert(item);

        WfInstance instance = new WfInstance();
        instance.setBusinessId(receipt.getId());
        WorkflowContext ctx = new WorkflowContext();
        ctx.setInstance(instance);
        handler.onApproved(ctx);

        assertEquals(0L, stockTxnMapper.selectCount(new LambdaQueryWrapper<MatStockTxn>()
                .eq(MatStockTxn::getSourceType, "MAT_RECEIPT")
                .eq(MatStockTxn::getSourceId, receipt.getId())));
        CostItem cost = costItemMapper.selectOne(new LambdaQueryWrapper<CostItem>()
                .eq(CostItem::getSourceType, "MAT_RECEIPT")
                .eq(CostItem::getSourceId, receipt.getId()));
        assertNotNull(cost);
        assertEquals(0, new BigDecimal("50.00").compareTo(cost.getAmount()));
    }

    @Test @Transactional @DisplayName("onRejected -> status = REJECTED")
    void testOnRejected() {
        MatReceipt receipt = new MatReceipt();
        receipt.setProjectId(10001L); receipt.setContractId(30001L); receipt.setPartnerId(20002L);
        receipt.setWarehouseId(1L); receipt.setReceiptDate(LocalDate.now());
        receipt.setQualityStatus("PENDING"); receipt.setTotalAmount(new BigDecimal("5000.00"));
        receipt.setReceiptCode("RC-HDLR-" + System.nanoTime());
        receipt.setApprovalStatus("APPROVING"); receipt.setTenantId(0L);
        receiptMapper.insert(receipt);

        WfInstance instance = new WfInstance(); instance.setBusinessId(receipt.getId());
        WorkflowContext ctx = new WorkflowContext(); ctx.setInstance(instance);

        handler.onRejected(ctx);
        assertEquals("REJECTED", receiptMapper.selectById(receipt.getId()).getApprovalStatus());
    }

    @Test @Transactional @DisplayName("onWithdrawn -> DRAFT")
    void testOnWithdrawn() {
        MatReceipt receipt = new MatReceipt();
        receipt.setProjectId(10001L); receipt.setContractId(30001L); receipt.setPartnerId(20002L);
        receipt.setWarehouseId(1L); receipt.setReceiptDate(LocalDate.now());
        receipt.setQualityStatus("PENDING"); receipt.setTotalAmount(new BigDecimal("5000.00"));
        receipt.setReceiptCode("RC-HDLR-" + System.nanoTime());
        receipt.setApprovalStatus("APPROVING"); receipt.setTenantId(0L);
        receiptMapper.insert(receipt);

        WfInstance instance = new WfInstance(); instance.setBusinessId(receipt.getId());
        WorkflowContext ctx = new WorkflowContext(); ctx.setInstance(instance);

        handler.onWithdrawn(ctx);
        assertEquals("DRAFT", receiptMapper.selectById(receipt.getId()).getApprovalStatus());
    }
}
