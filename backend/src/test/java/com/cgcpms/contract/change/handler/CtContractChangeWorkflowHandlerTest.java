package com.cgcpms.contract.change.handler;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.contract.entity.CtContractChange;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractChangeMapper;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.mapper.CostItemMapper;
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

    @Autowired
    private CtContractMapper contractMapper;

    @Autowired
    private CostItemMapper costItemMapper;

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
    @DisplayName("ISSUE-004-008: 合同变更审批只增量一次并生成来源一致的成本调整")
    void testOnApproved_ContractChangeAdjustsCostOnce() {
        CtContract contract = contractMapper.selectById(30001L);
        assertNotNull(contract, "测试合同应存在");
        contract.setCurrentAmount(new BigDecimal("640000.00"));
        contractMapper.updateById(contract);

        CtContractChange change = new CtContractChange();
        change.setProjectId(10001L);
        change.setContractId(30001L);
        change.setChangeCode("CC-ISSUE-004-008-" + System.nanoTime());
        change.setChangeName("ISSUE-004-008 合同变更");
        change.setChangeType("AMOUNT_INCREASE");
        change.setBeforeAmount(new BigDecimal("640000.00"));
        change.setChangeAmount(new BigDecimal("15000.00"));
        change.setAfterAmount(new BigDecimal("655000.00"));
        change.setApprovalStatus("DRAFT");
        change.setEffectiveFlag(0);
        change.setCostGeneratedFlag(0);
        change.setTenantId(TENANT_0);
        changeMapper.insert(change);

        WorkflowContext ctx = contextFor(change.getId(), 2500003L);

        handler.onApproved(ctx);
        handler.onApproved(ctx);

        CtContractChange updated = changeMapper.selectById(change.getId());
        assertEquals("APPROVED", updated.getApprovalStatus());
        assertEquals(1, updated.getEffectiveFlag());
        assertEquals(1, updated.getCostGeneratedFlag());

        CtContract updatedContract = contractMapper.selectById(30001L);
        assertMoneyEquals("655000.00", updatedContract.getCurrentAmount());

        var costs = costItemMapper.selectList(new LambdaQueryWrapper<CostItem>()
                .eq(CostItem::getTenantId, TENANT_0)
                .eq(CostItem::getSourceType, "CT_CHANGE")
                .eq(CostItem::getSourceId, change.getId()));
        assertEquals(1, costs.size(), "重复审批回调不应重复生成合同变更成本项");
        CostItem cost = costs.get(0);
        assertEquals(30001L, cost.getContractId());
        assertEquals(10001L, cost.getProjectId());
        assertEquals("CHANGE", cost.getCostType());
        assertEquals("CONFIRMED", cost.getCostStatus());
        assertMoneyEquals("15000.00", cost.getAmount());
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

    private WorkflowContext contextFor(Long businessId, Long instanceId) {
        WfInstance instance = new WfInstance();
        instance.setBusinessId(businessId);
        instance.setId(instanceId);
        WorkflowContext ctx = new WorkflowContext();
        ctx.setInstance(instance);
        return ctx;
    }

    private void assertMoneyEquals(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
