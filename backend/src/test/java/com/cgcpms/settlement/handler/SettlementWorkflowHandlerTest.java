package com.cgcpms.settlement.handler;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.settlement.entity.StlSettlement;
import com.cgcpms.settlement.mapper.StlSettlementMapper;
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
@DisplayName("SettlementWorkflowHandler — approval lifecycle tests")
class SettlementWorkflowHandlerTest {

    private static final long USER_ADMIN = 1L;
    private static final long TENANT_0 = 0L;

    @Autowired
    private SettlementWorkflowHandler handler;

    @Autowired
    private StlSettlementMapper stlSettlementMapper;

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
    @DisplayName("supportBusinessType -> SETTLEMENT")
    void testSupportBusinessType() {
        assertEquals("SETTLEMENT", handler.supportBusinessType());
    }

    @Test
    @DisplayName("isCritical -> true")
    void testIsCritical() {
        assertTrue(handler.isCritical(), "结算审批处理器应标记为关键");
    }

    @Test
    @Transactional
    @DisplayName("onApproved -> DRAFT→APPROVED + FINALIZED")
    void testOnApproved_Success() {
        StlSettlement settlement = new StlSettlement();
        settlement.setProjectId(10001L);
        settlement.setSettlementCode("STL-HDLR-TEST-" + System.nanoTime());
        settlement.setApprovalStatus("APPROVING");
        settlement.setSettlementStatus("DRAFT");
        settlement.setTenantId(0L);
        stlSettlementMapper.insert(settlement);

        WfInstance instance = new WfInstance();
        instance.setBusinessId(settlement.getId());
        instance.setId(2300001L);
        WorkflowContext ctx = new WorkflowContext();
        ctx.setInstance(instance);

        handler.onApproved(ctx);

        StlSettlement updated = stlSettlementMapper.selectById(settlement.getId());
        assertNotNull(updated, "结算单应仍然存在");
        assertEquals("APPROVED", updated.getApprovalStatus(), "审批状态应变为 APPROVED");
        assertEquals("FINALIZED", updated.getSettlementStatus(), "结算状态应变为 FINALIZED");
    }

    @Test
    @Transactional
    @DisplayName("onApproved — null businessId -> IllegalStateException")
    void testOnApproved_NullBusinessIdGuard() {
        WfInstance instance = new WfInstance();
        instance.setId(2300002L);
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
        StlSettlement settlement = new StlSettlement();
        settlement.setProjectId(10001L);
        settlement.setSettlementCode("STL-HDLR-REJ-" + System.nanoTime());
        settlement.setApprovalStatus("APPROVING");
        settlement.setSettlementStatus("DRAFT");
        settlement.setTenantId(0L);
        stlSettlementMapper.insert(settlement);

        WfInstance instance = new WfInstance();
        instance.setBusinessId(settlement.getId());
        WorkflowContext ctx = new WorkflowContext();
        ctx.setInstance(instance);

        handler.onRejected(ctx);

        StlSettlement updated = stlSettlementMapper.selectById(settlement.getId());
        assertEquals("REJECTED", updated.getApprovalStatus(), "审批状态应变为 REJECTED");
    }

    @Test
    @Transactional
    @DisplayName("onWithdrawn -> APPROVING→DRAFT")
    void testOnWithdrawn() {
        StlSettlement settlement = new StlSettlement();
        settlement.setProjectId(10001L);
        settlement.setSettlementCode("STL-HDLR-WTH-" + System.nanoTime());
        settlement.setApprovalStatus("APPROVING");
        settlement.setSettlementStatus("DRAFT");
        settlement.setTenantId(0L);
        stlSettlementMapper.insert(settlement);

        WfInstance instance = new WfInstance();
        instance.setBusinessId(settlement.getId());
        WorkflowContext ctx = new WorkflowContext();
        ctx.setInstance(instance);

        handler.onWithdrawn(ctx);

        StlSettlement updated = stlSettlementMapper.selectById(settlement.getId());
        assertEquals("DRAFT", updated.getApprovalStatus(), "撤回后审批状态应变为 DRAFT");
    }
}
