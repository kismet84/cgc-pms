package com.cgcpms.purchase.handler;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.purchase.entity.MatPurchaseOrder;
import com.cgcpms.purchase.mapper.MatPurchaseOrderMapper;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.handler.WorkflowContext;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@DisplayName("PurchaseOrderWorkflowHandler — approval lifecycle tests")
class PurchaseOrderWorkflowHandlerTest {

    private static final long USER_ADMIN = 1L;
    private static final long TENANT_0 = 0L;

    @Autowired
    private PurchaseOrderWorkflowHandler handler;

    @Autowired
    private MatPurchaseOrderMapper orderMapper;

    @BeforeEach
    void setupContext() {
        UserContext.set(Jwts.claims().add("userId", USER_ADMIN).add("username", "admin")
                .add("tenantId", TENANT_0).add("roleCodes", List.of("ADMIN")).build());
    }
    @AfterEach
    void clearContext() { UserContext.clear(); }

    @Test
    @DisplayName("supportBusinessType -> PURCHASE_ORDER")
    void testSupportBusinessType() {
        assertEquals("PURCHASE_ORDER", handler.supportBusinessType());
    }

    @Test
    @DisplayName("isCritical -> true")
    void testIsCritical() {
        assertTrue(handler.isCritical());
    }

    @Test
    @Transactional
    @DisplayName("onApproved -> order status = APPROVED")
    void testOnApproved() {
        MatPurchaseOrder order = new MatPurchaseOrder();
        order.setProjectId(10001L);
        order.setOrderCode("PO-HDLR-" + System.nanoTime());
        order.setOrderType("PURCHASE");
        order.setApprovalStatus("APPROVING");
        order.setOrderStatus("APPROVING");
        order.setTenantId(0L);
        orderMapper.insert(order);

        WfInstance instance = new WfInstance();
        instance.setBusinessId(order.getId());
        WorkflowContext ctx = new WorkflowContext();
        ctx.setInstance(instance);

        handler.onApproved(ctx);

        MatPurchaseOrder updated = orderMapper.selectById(order.getId());
        assertEquals("APPROVED", updated.getApprovalStatus());
    }

    @Test
    @Transactional
    @DisplayName("onApproved — null businessId -> IllegalStateException")
    void testOnApproved_NullBusinessId() {
        WfInstance instance = new WfInstance();
        instance.setId(9002L);
        WorkflowContext ctx = new WorkflowContext();
        ctx.setInstance(instance);
        assertThrows(IllegalStateException.class, () -> handler.onApproved(ctx));
    }

    @Test
    @Transactional
    @DisplayName("onRejected -> status = REJECTED")
    void testOnRejected() {
        MatPurchaseOrder order = new MatPurchaseOrder();
        order.setProjectId(10001L);
        order.setOrderCode("PO-HDLR-REJ-" + System.nanoTime());
        order.setOrderType("PURCHASE");
        order.setApprovalStatus("APPROVING");
        order.setTenantId(0L);
        orderMapper.insert(order);

        WfInstance instance = new WfInstance();
        instance.setBusinessId(order.getId());
        WorkflowContext ctx = new WorkflowContext();
        ctx.setInstance(instance);

        handler.onRejected(ctx);
        assertEquals("REJECTED", orderMapper.selectById(order.getId()).getApprovalStatus());
    }

    @Test
    @Transactional
    @DisplayName("onWithdrawn -> DRAFT")
    void testOnWithdrawn() {
        MatPurchaseOrder order = new MatPurchaseOrder();
        order.setProjectId(10001L);
        order.setOrderCode("PO-HDLR-WTH-" + System.nanoTime());
        order.setOrderType("PURCHASE");
        order.setApprovalStatus("APPROVING");
        order.setTenantId(0L);
        orderMapper.insert(order);

        WfInstance instance = new WfInstance();
        instance.setBusinessId(order.getId());
        WorkflowContext ctx = new WorkflowContext();
        ctx.setInstance(instance);

        handler.onWithdrawn(ctx);
        assertEquals("DRAFT", orderMapper.selectById(order.getId()).getApprovalStatus());
    }
}
