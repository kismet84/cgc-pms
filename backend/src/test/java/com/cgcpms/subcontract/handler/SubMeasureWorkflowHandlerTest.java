package com.cgcpms.subcontract.handler;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.subcontract.entity.SubMeasure;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
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
@DisplayName("SubMeasureWorkflowHandler — approval lifecycle tests")
class SubMeasureWorkflowHandlerTest {

    private static final long USER_ADMIN = 1L;
    private static final long TENANT_0 = 0L;

    @Autowired
    private SubMeasureWorkflowHandler handler;

    @Autowired
    private SubMeasureMapper subMeasureMapper;

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
    @DisplayName("supportBusinessType -> SUB_MEASURE")
    void testSupportBusinessType() {
        assertEquals("SUB_MEASURE", handler.supportBusinessType());
    }

    @Test
    @DisplayName("isCritical -> true")
    void testIsCritical() {
        assertTrue(handler.isCritical(), "分包计量审批处理器应标记为关键");
    }

    @Test
    @Transactional
    @DisplayName("onApproved -> DRAFT→APPROVED")
    void testOnApproved_Success() {
        SubMeasure measure = new SubMeasure();
        measure.setProjectId(10001L);
        measure.setContractId(30001L);
        measure.setPartnerId(50001L);
        measure.setMeasureCode("SM-HDLR-TEST-" + System.nanoTime());
        measure.setApprovalStatus("DRAFT");
        measure.setStatus("DRAFT");
        measure.setTenantId(0L);
        subMeasureMapper.insert(measure);

        WfInstance instance = new WfInstance();
        instance.setBusinessId(measure.getId());
        instance.setId(2100001L);
        WorkflowContext ctx = new WorkflowContext();
        ctx.setInstance(instance);

        handler.onApproved(ctx);

        SubMeasure updated = subMeasureMapper.selectById(measure.getId());
        assertNotNull(updated, "分包计量应仍然存在");
        assertEquals("APPROVED", updated.getApprovalStatus(), "审批状态应变为 APPROVED");
    }

    @Test
    @Transactional
    @DisplayName("onApproved — null businessId -> IllegalStateException")
    void testOnApproved_NullBusinessIdGuard() {
        WfInstance instance = new WfInstance();
        instance.setId(2100002L);
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
        SubMeasure measure = new SubMeasure();
        measure.setProjectId(10001L);
        measure.setContractId(30001L);
        measure.setPartnerId(50001L);
        measure.setMeasureCode("SM-HDLR-REJ-" + System.nanoTime());
        measure.setApprovalStatus("APPROVING");
        measure.setStatus("APPROVING");
        measure.setTenantId(0L);
        subMeasureMapper.insert(measure);

        WfInstance instance = new WfInstance();
        instance.setBusinessId(measure.getId());
        WorkflowContext ctx = new WorkflowContext();
        ctx.setInstance(instance);

        handler.onRejected(ctx);

        SubMeasure updated = subMeasureMapper.selectById(measure.getId());
        assertEquals("REJECTED", updated.getApprovalStatus(), "审批状态应变为 REJECTED");
        assertEquals("REJECTED", updated.getStatus(), "业务状态应同步变为 REJECTED");
    }

    @Test
    @Transactional
    @DisplayName("onWithdrawn -> APPROVING→DRAFT")
    void testOnWithdrawn() {
        SubMeasure measure = new SubMeasure();
        measure.setProjectId(10001L);
        measure.setContractId(30001L);
        measure.setPartnerId(50001L);
        measure.setMeasureCode("SM-HDLR-WTH-" + System.nanoTime());
        measure.setApprovalStatus("APPROVING");
        measure.setStatus("APPROVING");
        measure.setTenantId(0L);
        subMeasureMapper.insert(measure);

        WfInstance instance = new WfInstance();
        instance.setBusinessId(measure.getId());
        WorkflowContext ctx = new WorkflowContext();
        ctx.setInstance(instance);

        handler.onWithdrawn(ctx);

        SubMeasure updated = subMeasureMapper.selectById(measure.getId());
        assertEquals("DRAFT", updated.getApprovalStatus(), "撤回后审批状态应变为 DRAFT");
        assertEquals("DRAFT", updated.getStatus(), "撤回后业务状态应同步恢复为 DRAFT");
    }
}
