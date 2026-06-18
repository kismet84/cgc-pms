package com.cgcpms.contract;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.contract.constant.ContractStatusConstants;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.handler.ContractWorkflowHandler;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.service.CostGenerationService;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
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

import java.math.BigDecimal;
import java.time.LocalDate;

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

    @Autowired
    private PmProjectMapper projectMapper;

    @Autowired
    private MdPartnerMapper partnerMapper;

    @BeforeEach
    void setupContext() {
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 0L)
                .build());
        seedApprovedContract();
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    private void seedApprovedContract() {
        if (projectMapper.selectById(10001L) == null) {
            PmProject project = new PmProject();
            project.setId(10001L);
            project.setProjectCode("PRJ-ROLLBACK-001");
            project.setProjectName("回滚测试项目");
            project.setProjectType("CONSTRUCTION");
            project.setContractAmount(new BigDecimal("5000000.00"));
            project.setTargetCost(new BigDecimal("4200000.00"));
            project.setStatus("RUNNING");
            project.setApprovalStatus("APPROVED");
            projectMapper.insert(project);
        }

        if (partnerMapper.selectById(20001L) == null) {
            MdPartner partyA = new MdPartner();
            partyA.setId(20001L);
            partyA.setPartnerCode("PT-ROLLBACK-A");
            partyA.setPartnerName("回滚测试甲方");
            partyA.setPartnerType("PARTY_A");
            partyA.setBlacklistFlag(0);
            partyA.setStatus("ENABLE");
            partnerMapper.insert(partyA);
        }

        if (partnerMapper.selectById(20002L) == null) {
            MdPartner partyB = new MdPartner();
            partyB.setId(20002L);
            partyB.setPartnerCode("PT-ROLLBACK-B");
            partyB.setPartnerName("回滚测试乙方");
            partyB.setPartnerType("PARTY_B");
            partyB.setBlacklistFlag(0);
            partyB.setStatus("ENABLE");
            partnerMapper.insert(partyB);
        }

        if (contractMapper.selectById(APPROVED_CONTRACT_ID) != null) return;
        CtContract contract = new CtContract();
        contract.setId(APPROVED_CONTRACT_ID);
        contract.setProjectId(10001L);
        contract.setContractCode("CT-ROLLBACK-APPROVED");
        contract.setContractName("回滚测试合同");
        contract.setContractType("SUB");
        contract.setPartyAId(20001L);
        contract.setPartyBId(20002L);
        contract.setContractAmount(new BigDecimal("640000.00"));
        contract.setCurrentAmount(new BigDecimal("640000.00"));
        contract.setPaidAmount(BigDecimal.ZERO);
        contract.setTaxRate(new BigDecimal("13.00"));
        contract.setTaxAmount(new BigDecimal("73628.32"));
        contract.setAmountWithoutTax(new BigDecimal("566371.68"));
        contract.setSignedDate(LocalDate.now());
        contract.setPaymentMethod("银行转账");
        contract.setSettlementMethod("按进度结算");
        contract.setContractStatus(ContractStatusConstants.STATUS_PERFORMING);
        contract.setApprovalStatus(ContractStatusConstants.APPROVAL_APPROVED);
        contract.setCostGeneratedFlag(0);
        contractMapper.insert(contract);
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
