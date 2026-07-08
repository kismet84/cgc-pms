package com.cgcpms.payment.handler;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.mapper.PayApplicationMapper;
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
@DisplayName("PayRequestWorkflowHandler — approval lifecycle tests")
class PayRequestWorkflowHandlerTest {

    private static final long USER_ADMIN = 1L;
    private static final long TENANT_0 = 0L;

    @Autowired
    private PayRequestWorkflowHandler handler;

    @Autowired
    private PayApplicationMapper payApplicationMapper;

    @BeforeEach
    void setupContext() {
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN).add("username", "admin")
                .add("tenantId", TENANT_0).add("roleCodes", List.of("ADMIN")).build());
    }

    @AfterEach
    void clearContext() { UserContext.clear(); }

    @Test
    @DisplayName("supportBusinessType -> PAY_REQUEST")
    void testSupportBusinessType() {
        assertEquals("PAY_REQUEST", handler.supportBusinessType());
    }

    @Test
    @DisplayName("isCritical -> true")
    void testIsCritical() {
        assertTrue(handler.isCritical());
    }

    @Test
    @Transactional
    @DisplayName("onApproved -> 状态变更 + 金额校验")
    void testOnApproved() throws Exception {
        PayApplication app = new PayApplication();
        app.setProjectId(10001L);
        app.setContractId(30001L);
        app.setPartnerId(20002L);
        app.setPayType("MATERIAL");
        app.setApplyAmount(new BigDecimal("10000.00"));
        app.setApplyCode("PAY-HDLR-" + System.nanoTime());
        app.setApprovalStatus("APPROVING");
        app.setPayStatus("PENDING");
        app.setTenantId(0L);
        payApplicationMapper.insert(app);

        WfInstance instance = new WfInstance();
        instance.setBusinessId(app.getId());
        WorkflowContext ctx = new WorkflowContext();
        ctx.setInstance(instance);

        handler.onApproved(ctx);

        PayApplication updated = payApplicationMapper.selectById(app.getId());
        assertNotNull(updated);
        assertEquals("APPROVED", updated.getApprovalStatus());
        assertEquals("APPROVED", updated.getPayStatus(), "审批通过后付款状态应进入已批未付");
        assertEquals(0, app.getApplyAmount().compareTo(updated.getApprovedAmount()),
                "审批通过金额应同步为申请金额，供后续财务回写核对");
    }

    @Test
    @Transactional
    @DisplayName("onApproved — null businessId -> IllegalStateException")
    void testOnApproved_NullBusinessId() {
        WfInstance instance = new WfInstance();
        instance.setId(9001L);
        WorkflowContext ctx = new WorkflowContext();
        ctx.setInstance(instance);

        assertThrows(IllegalStateException.class, () -> handler.onApproved(ctx));
    }

    @Test
    @Transactional
    @DisplayName("onRejected -> status = REJECTED")
    void testOnRejected() {
        PayApplication app = new PayApplication();
        app.setProjectId(10001L);
        app.setContractId(30001L);
        app.setPartnerId(20002L);
        app.setPayType("MATERIAL");
        app.setApplyAmount(new BigDecimal("5000.00"));
        app.setApplyCode("PAY-HDLR-REJ-" + System.nanoTime());
        app.setApprovalStatus("APPROVING");
        app.setPayStatus("PENDING");
        app.setTenantId(0L);
        payApplicationMapper.insert(app);

        WfInstance instance = new WfInstance();
        instance.setBusinessId(app.getId());
        WorkflowContext ctx = new WorkflowContext();
        ctx.setInstance(instance);

        handler.onRejected(ctx);

        PayApplication updated = payApplicationMapper.selectById(app.getId());
        assertEquals("REJECTED", updated.getApprovalStatus());
        assertEquals("PENDING", updated.getPayStatus(), "审批驳回不应误置为已批未付或已付款");
    }

    @Test
    @Transactional
    @DisplayName("onWithdrawn -> status = DRAFT")
    void testOnWithdrawn() {
        PayApplication app = new PayApplication();
        app.setProjectId(10001L);
        app.setContractId(30001L);
        app.setPartnerId(20002L);
        app.setPayType("MATERIAL");
        app.setApplyAmount(new BigDecimal("5000.00"));
        app.setApplyCode("PAY-HDLR-REJ-" + System.nanoTime());
        app.setApprovalStatus("APPROVING");
        app.setPayStatus("PENDING");
        app.setTenantId(0L);
        payApplicationMapper.insert(app);

        WfInstance instance = new WfInstance();
        instance.setBusinessId(app.getId());
        WorkflowContext ctx = new WorkflowContext();
        ctx.setInstance(instance);

        handler.onWithdrawn(ctx);

        PayApplication updated = payApplicationMapper.selectById(app.getId());
        assertEquals("DRAFT", updated.getApprovalStatus());
    }
}
