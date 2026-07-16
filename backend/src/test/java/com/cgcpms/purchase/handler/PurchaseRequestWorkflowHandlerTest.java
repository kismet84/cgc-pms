package com.cgcpms.purchase.handler;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.purchase.entity.MatPurchaseRequest;
import com.cgcpms.purchase.entity.MatPurchaseRequestItem;
import com.cgcpms.purchase.entity.MatPurchaseOrder;
import com.cgcpms.purchase.entity.MatPurchaseOrderItem;
import com.cgcpms.purchase.mapper.MatPurchaseOrderItemMapper;
import com.cgcpms.purchase.mapper.MatPurchaseOrderMapper;
import com.cgcpms.purchase.mapper.MatPurchaseRequestItemMapper;
import com.cgcpms.purchase.mapper.MatPurchaseRequestMapper;
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
@DisplayName("PurchaseRequestWorkflowHandler — approval lifecycle tests")
class PurchaseRequestWorkflowHandlerTest {

    private static final long USER_ADMIN = 1L;
    private static final long TENANT_0 = 0L;

    @Autowired
    private PurchaseRequestWorkflowHandler handler;

    @Autowired
    private MatPurchaseRequestMapper requestMapper;

    @Autowired
    private MatPurchaseOrderMapper orderMapper;

    @Autowired
    private MatPurchaseOrderItemMapper orderItemMapper;

    @Autowired
    private MatPurchaseRequestItemMapper requestItemMapper;

    @BeforeEach
    void setupContext() {
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", TENANT_0)
                .add("roleCodes", List.of("ADMIN"))
                .build());
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    @DisplayName("supportBusinessType -> PURCHASE_REQUEST")
    void testSupportBusinessType() {
        assertEquals("PURCHASE_REQUEST", handler.supportBusinessType());
    }

    @Test
    @DisplayName("isCritical -> true")
    void testIsCritical() {
        assertTrue(handler.isCritical(), "采购申请审批处理器应标记为关键");
    }

    @Test
    @Transactional
    @DisplayName("onApproved -> 批准需求并转换为待补充商业条件的草稿采购订单")
    void testOnApproved_Success() {
        MatPurchaseRequest req = new MatPurchaseRequest();
        req.setProjectId(10001L);
        req.setContractId(30001L);
        req.setRequestCode("PR-HDLR-TEST-" + System.nanoTime());
        req.setApprovalStatus("DRAFT");
        req.setStatus("DRAFT");
        req.setTenantId(0L);
        requestMapper.insert(req);

        MatPurchaseRequestItem item = new MatPurchaseRequestItem();
        item.setTenantId(0L);
        item.setRequestId(req.getId());
        item.setMaterialId(1L);
        item.setQuantity(new BigDecimal("5.00"));
        item.setUnit("m");
        requestItemMapper.insert(item);

        WfInstance instance = new WfInstance();
        instance.setBusinessId(req.getId());
        instance.setId(2000001L);
        WorkflowContext ctx = new WorkflowContext();
        ctx.setInstance(instance);

        handler.onApproved(ctx);

        MatPurchaseRequest updated = requestMapper.selectById(req.getId());
        assertNotNull(updated, "采购申请应仍然存在");
        assertEquals("APPROVED", updated.getApprovalStatus(), "审批状态应变为 APPROVED");
        assertEquals("CONVERTED", updated.getStatus(), "业务状态应变为 CONVERTED");
        MatPurchaseOrder order = orderMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MatPurchaseOrder>()
                        .eq(MatPurchaseOrder::getRequestId, req.getId()));
        assertNotNull(order);
        assertEquals("DRAFT", order.getApprovalStatus(), "采购申请审批不得替代订单审批");
        assertEquals("DRAFT", order.getOrderStatus());
        MatPurchaseOrderItem convertedItem = orderItemMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MatPurchaseOrderItem>()
                        .eq(MatPurchaseOrderItem::getOrderId, order.getId()));
        assertNotNull(convertedItem);
        assertEquals(item.getId(), convertedItem.getRequestItemId());
    }

    @Test
    @Transactional
    @DisplayName("onApproved — business entity not found -> IllegalStateException")
    void testOnApproved_NullBusinessId() {
        WfInstance instance = new WfInstance();
        instance.setBusinessId(99999999L);
        instance.setId(2000002L);
        WorkflowContext ctx = new WorkflowContext();
        ctx.setInstance(instance);

        assertThrows(BusinessException.class, () -> handler.onApproved(ctx),
                "审批不存在的采购申请应抛出 BusinessException");
    }

    @Test
    @Transactional
    @DisplayName("onApproved — null businessId -> IllegalStateException")
    void testOnApproved_NullBusinessIdGuard() {
        WfInstance instance = new WfInstance();
        instance.setId(2000003L);
        instance.setBusinessId(null);
        WorkflowContext ctx = new WorkflowContext();
        ctx.setInstance(instance);

        assertThrows(IllegalStateException.class, () -> handler.onApproved(ctx),
                "businessId 为 null 时应抛出 IllegalStateException");
    }

    @Test
    @Transactional
    @DisplayName("onRejected -> DRAFT→REJECTED")
    void testOnRejected() {
        MatPurchaseRequest req = new MatPurchaseRequest();
        req.setProjectId(10001L);
        req.setContractId(30001L);
        req.setRequestCode("PR-HDLR-REJ-" + System.nanoTime());
        req.setApprovalStatus("APPROVING");
        req.setStatus("APPROVING");
        req.setTenantId(0L);
        requestMapper.insert(req);

        WfInstance instance = new WfInstance();
        instance.setBusinessId(req.getId());
        WorkflowContext ctx = new WorkflowContext();
        ctx.setInstance(instance);

        handler.onRejected(ctx);

        MatPurchaseRequest updated = requestMapper.selectById(req.getId());
        assertEquals("REJECTED", updated.getApprovalStatus(), "审批状态应变为 REJECTED");
    }

    @Test
    @Transactional
    @DisplayName("onWithdrawn -> APPROVING→DRAFT")
    void testOnWithdrawn() {
        MatPurchaseRequest req = new MatPurchaseRequest();
        req.setProjectId(10001L);
        req.setContractId(30001L);
        req.setRequestCode("PR-HDLR-WTH-" + System.nanoTime());
        req.setApprovalStatus("APPROVING");
        req.setStatus("APPROVING");
        req.setTenantId(0L);
        requestMapper.insert(req);

        WfInstance instance = new WfInstance();
        instance.setBusinessId(req.getId());
        WorkflowContext ctx = new WorkflowContext();
        ctx.setInstance(instance);

        handler.onWithdrawn(ctx);

        MatPurchaseRequest updated = requestMapper.selectById(req.getId());
        assertEquals("DRAFT", updated.getApprovalStatus(), "撤回后审批状态应变为 DRAFT");
    }
}
