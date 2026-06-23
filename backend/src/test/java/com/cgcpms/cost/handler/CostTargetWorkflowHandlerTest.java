package com.cgcpms.cost.handler;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.entity.CostTarget;
import com.cgcpms.cost.mapper.CostTargetMapper;
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
@DisplayName("CostTargetWorkflowHandler — approval lifecycle tests")
class CostTargetWorkflowHandlerTest {

    private static final long USER_ADMIN = 1L;
    private static final long TENANT_0 = 0L;

    @Autowired
    private CostTargetWorkflowHandler handler;

    @Autowired
    private CostTargetMapper costTargetMapper;

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
    @DisplayName("supportBusinessType -> COST_TARGET")
    void testSupportBusinessType() {
        assertEquals("COST_TARGET", handler.supportBusinessType());
    }

    @Test
    @DisplayName("isCritical -> true")
    void testIsCritical() {
        assertTrue(handler.isCritical(), "目标成本审批处理器应标记为关键");
    }

    @Test
    @Transactional
    @DisplayName("onApproved -> DRAFT→APPROVED, activates version")
    void testOnApproved_Success() {
        CostTarget target = new CostTarget();
        target.setProjectId(10001L);
        target.setVersionNo("V1.0");
        target.setVersionName("CT-HDLR-TEST-" + System.nanoTime());
        target.setApprovalStatus("DRAFT");
        target.setStatus("DRAFT");
        target.setTenantId(0L);
        costTargetMapper.insert(target);

        WfInstance instance = new WfInstance();
        instance.setBusinessId(target.getId());
        instance.setId(2200001L);
        WorkflowContext ctx = new WorkflowContext();
        ctx.setInstance(instance);

        handler.onApproved(ctx);

        CostTarget updated = costTargetMapper.selectById(target.getId());
        assertNotNull(updated, "目标成本应仍然存在");
        assertEquals("APPROVED", updated.getApprovalStatus(), "审批状态应变为 APPROVED");
    }

    @Test
    @Transactional
    @DisplayName("onApproved — null businessId -> IllegalStateException")
    void testOnApproved_NullBusinessIdGuard() {
        WfInstance instance = new WfInstance();
        instance.setId(2200002L);
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
        CostTarget target = new CostTarget();
        target.setProjectId(10001L);
        target.setVersionNo("V1.0");
        target.setVersionName("CT-HDLR-REJ-" + System.nanoTime());
        target.setApprovalStatus("APPROVING");
        target.setStatus("APPROVING");
        target.setTenantId(0L);
        costTargetMapper.insert(target);

        WfInstance instance = new WfInstance();
        instance.setBusinessId(target.getId());
        WorkflowContext ctx = new WorkflowContext();
        ctx.setInstance(instance);

        handler.onRejected(ctx);

        CostTarget updated = costTargetMapper.selectById(target.getId());
        assertEquals("REJECTED", updated.getApprovalStatus(), "审批状态应变为 REJECTED");
    }

    @Test
    @Transactional
    @DisplayName("onWithdrawn -> APPROVING→DRAFT")
    void testOnWithdrawn() {
        CostTarget target = new CostTarget();
        target.setProjectId(10001L);
        target.setVersionNo("V1.0");
        target.setVersionName("CT-HDLR-WTH-" + System.nanoTime());
        target.setApprovalStatus("APPROVING");
        target.setStatus("APPROVING");
        target.setTenantId(0L);
        costTargetMapper.insert(target);

        WfInstance instance = new WfInstance();
        instance.setBusinessId(target.getId());
        WorkflowContext ctx = new WorkflowContext();
        ctx.setInstance(instance);

        handler.onWithdrawn(ctx);

        CostTarget updated = costTargetMapper.selectById(target.getId());
        assertEquals("DRAFT", updated.getApprovalStatus(), "撤回后审批状态应变为 DRAFT");
    }
}
