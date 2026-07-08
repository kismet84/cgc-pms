package com.cgcpms.variation.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.variation.entity.VarOrder;
import com.cgcpms.variation.entity.VarOrderItem;
import com.cgcpms.variation.mapper.VarOrderItemMapper;
import com.cgcpms.variation.mapper.VarOrderMapper;
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
@DisplayName("VarOrderWorkflowHandler — approval lifecycle tests")
class VarOrderWorkflowHandlerTest {

    private static final long USER_ADMIN = 1L;
    private static final long TENANT_0 = 0L;

    @Autowired
    private VarOrderWorkflowHandler handler;

    @Autowired
    private VarOrderMapper varOrderMapper;

    @Autowired
    private VarOrderItemMapper varOrderItemMapper;

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
    @DisplayName("ISSUE-004-008: COST方向签证审批生成来源一致成本且重复回调不重复累计")
    void testOnApproved_CostVariationGeneratesCostItemsOnce() {
        VarOrder order = new VarOrder();
        order.setProjectId(10001L);
        order.setContractId(30001L);
        order.setPartnerId(50001L);
        order.setVarCode("VO-ISSUE-004-008-" + System.nanoTime());
        order.setVarName("ISSUE-004-008 成本签证");
        order.setVarType("DESIGN_CHANGE");
        order.setDirection("COST");
        order.setReportedAmount(new BigDecimal("12000.00"));
        order.setApprovalStatus("DRAFT");
        order.setCostGeneratedFlag(0);
        order.setTenantId(TENANT_0);
        varOrderMapper.insert(order);

        VarOrderItem item1 = item(order.getId(), "签证材料调整", "7000.00", 90001L);
        VarOrderItem item2 = item(order.getId(), "签证人工调整", "5000.00", 90002L);
        varOrderItemMapper.insert(item1);
        varOrderItemMapper.insert(item2);

        WorkflowContext ctx = contextFor(order.getId(), 2400003L);

        handler.onApproved(ctx);
        handler.onApproved(ctx);

        VarOrder updated = varOrderMapper.selectById(order.getId());
        assertEquals("APPROVED", updated.getApprovalStatus());
        assertEquals(1, updated.getCostGeneratedFlag());

        var costs = costItemMapper.selectList(new LambdaQueryWrapper<CostItem>()
                .eq(CostItem::getTenantId, TENANT_0)
                .eq(CostItem::getSourceType, "VAR_ORDER")
                .eq(CostItem::getSourceId, order.getId())
                .orderByAsc(CostItem::getAmount));
        assertEquals(2, costs.size(), "重复审批回调不应重复生成签证成本项");
        assertMoneyEquals("12000.00", costs.stream()
                .map(CostItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        assertTrue(costs.stream().allMatch(cost -> "VARIATION".equals(cost.getCostType())));
        assertTrue(costs.stream().allMatch(cost -> "CONFIRMED".equals(cost.getCostStatus())));
        assertTrue(costs.stream().allMatch(cost -> cost.getSourceItemId() != null));
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

    private VarOrderItem item(Long orderId, String itemName, String amount, Long costSubjectId) {
        VarOrderItem item = new VarOrderItem();
        item.setTenantId(TENANT_0);
        item.setVarOrderId(orderId);
        item.setItemName(itemName);
        item.setUnit("项");
        item.setQuantity(BigDecimal.ONE);
        item.setUnitPrice(new BigDecimal(amount));
        item.setAmount(new BigDecimal(amount));
        item.setCostSubjectId(costSubjectId);
        return item;
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
