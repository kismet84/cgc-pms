package com.cgcpms.contract.change.handler;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContractChange;
import com.cgcpms.contract.mapper.CtContractChangeMapper;
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
@DisplayName("CtContractChangeWorkflowHandler — approval lifecycle tests")
class CtContractChangeWorkflowHandlerTest {

    private static final long USER_ADMIN = 1L;
    private static final long TENANT_0 = 0L;

    @Autowired
    private CtContractChangeWorkflowHandler handler;

    @Autowired
    private CtContractChangeMapper changeMapper;

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
    @DisplayName("supportBusinessType -> CT_CHANGE")
    void testSupportBusinessType() {
        assertEquals("CT_CHANGE", handler.supportBusinessType());
    }

    @Test
    @DisplayName("isCritical -> true")
    void testIsCritical() {
        assertTrue(handler.isCritical(), "合同变更审批处理器应标记为关键");
    }

    @Test
    @Transactional
    @DisplayName("onApproved -> DRAFT→APPROVED, set effectiveFlag")
    void testOnApproved_Success() {
        CtContractChange change = new CtContractChange();
        change.setProjectId(10001L);
        change.setContractId(30001L);
        change.setChangeCode("CC-HDLR-TEST-" + System.nanoTime());
        change.setChangeName("test contract change");
        change.setChangeType("AMOUNT");
        change.setApprovalStatus("DRAFT");
        change.setTenantId(0L);
        changeMapper.insert(change);

        WfInstance instance = new WfInstance();
        instance.setBusinessId(change.getId());
        instance.setId(2500001L);
        WorkflowContext ctx = new WorkflowContext();
        ctx.setInstance(instance);

        handler.onApproved(ctx);

        CtContractChange updated = changeMapper.selectById(change.getId());
        assertNotNull(updated, "合同变更应仍然存在");
        assertEquals("APPROVED", updated.getApprovalStatus(), "审批状态应变为 APPROVED");
    }

    @Test
    @Transactional
    @DisplayName("onApproved — null businessId -> IllegalStateException")
    void testOnApproved_NullBusinessIdGuard() {
        WfInstance instance = new WfInstance();
        instance.setId(2500002L);
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
        CtContractChange change = new CtContractChange();
        change.setProjectId(10001L);
        change.setContractId(30001L);
        change.setChangeCode("CC-HDLR-REJ-" + System.nanoTime());
        change.setChangeName("test contract change");
        change.setChangeType("AMOUNT");
        change.setApprovalStatus("APPROVING");
        change.setTenantId(0L);
        changeMapper.insert(change);

        WfInstance instance = new WfInstance();
        instance.setBusinessId(change.getId());
        WorkflowContext ctx = new WorkflowContext();
        ctx.setInstance(instance);

        handler.onRejected(ctx);

        CtContractChange updated = changeMapper.selectById(change.getId());
        assertEquals("REJECTED", updated.getApprovalStatus(), "审批状态应变为 REJECTED");
    }

    @Test
    @Transactional
    @DisplayName("onWithdrawn -> APPROVING→DRAFT")
    void testOnWithdrawn() {
        CtContractChange change = new CtContractChange();
        change.setProjectId(10001L);
        change.setContractId(30001L);
        change.setChangeCode("CC-HDLR-WTH-" + System.nanoTime());
        change.setChangeName("test contract change");
        change.setChangeType("AMOUNT");
        change.setApprovalStatus("APPROVING");
        change.setTenantId(0L);
        changeMapper.insert(change);

        WfInstance instance = new WfInstance();
        instance.setBusinessId(change.getId());
        WorkflowContext ctx = new WorkflowContext();
        ctx.setInstance(instance);

        handler.onWithdrawn(ctx);

        CtContractChange updated = changeMapper.selectById(change.getId());
        assertEquals("DRAFT", updated.getApprovalStatus(), "撤回后审批状态应变为 DRAFT");
    }
}
