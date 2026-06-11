package com.cgcpms.contract;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.contract.constant.ContractStatusConstants;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.handler.ContractWorkflowHandler;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.service.CostGenerationService;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.handler.WorkflowContext;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("local")
class ContractApprovalRollbackTest {

    private static final long USER_ADMIN = 1L;

    /** Demo data: APPROVED contract CT-2026-001 (id=30001) */
    private static final long APPROVED_CONTRACT_ID = 30001L;

    @Autowired
    private ContractWorkflowHandler contractHandler;

    /** Mock out cost generation so we can force a failure */
    @MockBean
    private CostGenerationService costGenerationService;

    @Autowired
    private CtContractMapper contractMapper;

    @BeforeEach
    void setupContext() {
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 0L)
                .build());
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    // ═══════════════════════════════════════════════════════════
    // 场景: 回调异常传播 — 成本生成失败触发事务回滚
    // ═══════════════════════════════════════════════════════════
    @Test
    @Transactional
    @DisplayName("场景: 成本生成失败时 isCritical=true 保证异常传播，触发事务回滚")
    void testCallbackExceptionPropagatesOnCostFailure() {
        // 1. Record original contract state before the handler runs
        CtContract original = contractMapper.selectById(APPROVED_CONTRACT_ID);
        assertNotNull(original, "合同 30001 应存在");
        String originalApprovalStatus = original.getApprovalStatus();
        String originalContractStatus = original.getContractStatus();

        // 2. Build a WorkflowContext that points to this contract
        WfInstance instance = new WfInstance();
        instance.setId(9990001L);
        instance.setBusinessId(APPROVED_CONTRACT_ID);
        instance.setBusinessType(ContractStatusConstants.BUSINESS_TYPE_CONTRACT_APPROVAL);

        WorkflowContext ctx = new WorkflowContext();
        ctx.setInstance(instance);
        ctx.setActionType("APPROVE");
        ctx.setOperatorName("admin");

        // 3. Mock cost generation to throw RuntimeException
        doThrow(new RuntimeException("Simulated cost generation failure"))
                .when(costGenerationService).generateLockedCost(APPROVED_CONTRACT_ID);

        // 4. onApproved should throw RuntimeException (isCritical=true behavior)
        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            contractHandler.onApproved(ctx);
        }, "isCritical=true: 成本生成失败应向上传播异常");
        assertEquals("Simulated cost generation failure", ex.getMessage());

        // 5. Verify contract status was NOT updated (transaction rolled back)
        // After exception, re-read should show original state
        CtContract after = contractMapper.selectById(APPROVED_CONTRACT_ID);
        assertEquals(originalApprovalStatus, after.getApprovalStatus(),
                "异常触发回滚后审批状态不应变更");
        assertEquals(originalContractStatus, after.getContractStatus(),
                "异常触发回滚后合同状态不应变更");

        System.out.println("✅ 回调回滚场景通过: 异常传播成功, "
                + "原状态=" + originalApprovalStatus
                + ", 异常后状态=" + after.getApprovalStatus());
    }
}
