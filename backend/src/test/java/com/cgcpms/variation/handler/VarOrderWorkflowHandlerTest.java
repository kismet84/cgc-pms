package com.cgcpms.variation.handler;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.variation.entity.VarOrder;
import com.cgcpms.variation.mapper.VarOrderMapper;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.handler.WorkflowContext;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@DisplayName("VarOrderWorkflowHandler — approval lifecycle tests")
class VarOrderWorkflowHandlerTest {

    private static final long USER_ADMIN = 1L;
    private static final long TENANT_0 = 0L;

    @Autowired
    private VarOrderWorkflowHandler handler;

    @Autowired
    private VarOrderMapper varOrderMapper;

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
    @DisplayName("supportBusinessType -> VAR_ORDER")
    void testSupportBusinessType() {
        assertEquals("VAR_ORDER", handler.supportBusinessType());
    }

    @Test
    @DisplayName("isCritical -> true")
    void testIsCritical() {
        assertTrue(handler.isCritical(), "签证变更审批处理器应标记为关键");
    }

    @Test
    @Transactional
    @DisplayName("onApproved -> DRAFT→APPROVED (non-COST direction skips generateCost)")
    void testOnApproved_Success() {
        VarOrder order = new VarOrder();
        order.setProjectId(10001L);
        order.setContractId(30001L);
        order.setPartnerId(50001L);
        order.setVarCode("VO-HDLR-TEST-" + System.nanoTime());
        order.setVarName("测试签证变更-" + System.nanoTime());
        order.setDirection("REVENUE");
        order.setApprovalStatus("DRAFT");
        order.setTenantId(0L);
        varOrderMapper.insert(order);

        WfInstance instance = new WfInstance();
        instance.setBusinessId(order.getId());
        instance.setId(2400001L);
        WorkflowContext ctx = new WorkflowContext();
        ctx.setInstance(instance);

        handler.onApproved(ctx);

        VarOrder updated = varOrderMapper.selectById(order.getId());
        assertNotNull(updated, "签证变更应仍然存在");
        assertEquals("APPROVED", updated.getApprovalStatus(), "审批状态应变为 APPROVED");
    }

    @Test
    @Transactional
    @DisplayName("onApproved — null businessId -> IllegalStateException")
    void testOnApproved_NullBusinessIdGuard() {
        WfInstance instance = new WfInstance();
        instance.setId(2400002L);
        instance.setBusinessId(null);
        WorkflowContext ctx = new WorkflowContext();
        ctx.setInstance(instance);

        assertThrows(IllegalStateException.class, () -> handler.onApproved(ctx),
                "businessId 为 null 时应抛出 IllegalStateException");
    }

    @Test
    @Transactional
    @DisplayName("onRejected -> APPROVING→REJECTED")
    void testOnRejected() {
        VarOrder order = new VarOrder();
        order.setProjectId(10001L);
        order.setContractId(30001L);
        order.setPartnerId(50001L);
        order.setVarCode("VO-HDLR-REJ-" + System.nanoTime());
        order.setVarName("测试签证变更-" + System.nanoTime());
        order.setDirection("REVENUE");
        order.setApprovalStatus("APPROVING");
        order.setTenantId(0L);
        varOrderMapper.insert(order);

        WfInstance instance = new WfInstance();
        instance.setBusinessId(order.getId());
        WorkflowContext ctx = new WorkflowContext();
        ctx.setInstance(instance);

        handler.onRejected(ctx);

        VarOrder updated = varOrderMapper.selectById(order.getId());
        assertEquals("REJECTED", updated.getApprovalStatus(), "审批状态应变为 REJECTED");
    }

    @Test
    @Transactional
    @DisplayName("onWithdrawn -> APPROVING→DRAFT")
    void testOnWithdrawn() {
        VarOrder order = new VarOrder();
        order.setProjectId(10001L);
        order.setContractId(30001L);
        order.setPartnerId(50001L);
        order.setVarCode("VO-HDLR-WTH-" + System.nanoTime());
        order.setVarName("测试签证变更-" + System.nanoTime());
        order.setDirection("REVENUE");
        order.setApprovalStatus("APPROVING");
        order.setTenantId(0L);
        varOrderMapper.insert(order);

        WfInstance instance = new WfInstance();
        instance.setBusinessId(order.getId());
        WorkflowContext ctx = new WorkflowContext();
        ctx.setInstance(instance);

        handler.onWithdrawn(ctx);

        VarOrder updated = varOrderMapper.selectById(order.getId());
        assertEquals("DRAFT", updated.getApprovalStatus(), "撤回后审批状态应变为 DRAFT");
    }
}
