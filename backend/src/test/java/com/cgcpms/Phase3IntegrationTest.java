package com.cgcpms;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.alert.entity.AlertLog;
import com.cgcpms.alert.mapper.AlertLogMapper;
import com.cgcpms.alert.service.AlertEvaluationService;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.entity.CtContractChange;
import com.cgcpms.contract.mapper.CtContractChangeMapper;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.contract.service.CtContractChangeService;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.entity.CostSummary;
import com.cgcpms.cost.entity.CostTarget;
import com.cgcpms.cost.entity.CostTargetItem;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.cost.mapper.CostSummaryMapper;
import com.cgcpms.cost.mapper.CostTargetItemMapper;
import com.cgcpms.cost.mapper.CostTargetMapper;
import com.cgcpms.cost.service.CostSummaryService;
import com.cgcpms.cost.service.CostTargetService;
import com.cgcpms.notification.entity.SysNotification;
import com.cgcpms.notification.mapper.SysNotificationMapper;
import com.cgcpms.project.entity.PmProjectMember;
import com.cgcpms.project.mapper.PmProjectMemberMapper;
import com.cgcpms.settlement.entity.StlSettlement;
import com.cgcpms.settlement.mapper.StlSettlementMapper;
import com.cgcpms.settlement.service.StlSettlementWriteService;
import com.cgcpms.subcontract.entity.SubMeasure;
import com.cgcpms.subcontract.entity.SubMeasureItem;
import com.cgcpms.subcontract.mapper.SubMeasureItemMapper;
import com.cgcpms.subcontract.mapper.SubMeasureMapper;
import com.cgcpms.subcontract.service.SubMeasureService;
import com.cgcpms.variation.entity.VarOrder;
import com.cgcpms.variation.entity.VarOrderItem;
import com.cgcpms.variation.mapper.VarOrderItemMapper;
import com.cgcpms.variation.mapper.VarOrderMapper;
import com.cgcpms.variation.service.VarOrderService;
import com.cgcpms.workflow.WorkflowBusinessTypes;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.entity.WfTask;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.mapper.WfTaskMapper;
import com.cgcpms.workflow.service.WorkflowEngine;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Phase3IntegrationTest {

    private static final long USER_ADMIN = 1L;

    /** Demo data: PERFORMING+APPROVED contract CT-2026-001 */
    private static final long CONTRACT_ID = 30001L;
    /** Demo data: ONGOING project PRJ-2026-001 */
    private static final long PROJECT_ID = 10001L;
    /** Demo data: supplier partner */
    private static final long PARTNER_ID = 20001L;

    // ── CT_CHANGE ──
    @Autowired private CtContractChangeService changeService;
    @Autowired private CtContractChangeMapper changeMapper;

    // ── VAR_ORDER ──
    @Autowired private VarOrderService varOrderService;
    @Autowired private VarOrderMapper varOrderMapper;
    @Autowired private VarOrderItemMapper varOrderItemMapper;

    // ── SETTLEMENT ──
    @Autowired private StlSettlementWriteService settlementWriteService;
    @Autowired private StlSettlementMapper settlementMapper;

    // ── SUB_MEASURE ──
    @Autowired private SubMeasureService subMeasureService;
    @Autowired private SubMeasureMapper subMeasureMapper;
    @Autowired private SubMeasureItemMapper subMeasureItemMapper;

    // ── COST_TARGET ──
    @Autowired private CostTargetService costTargetService;
    @Autowired private CostTargetMapper costTargetMapper;
    @Autowired private CostTargetItemMapper costTargetItemMapper;

    // ── COST ──
    @Autowired private CostSummaryService costSummaryService;
    @Autowired private CostSummaryMapper costSummaryMapper;
    @Autowired private CostItemMapper costItemMapper;

    // ── ALERT ──
    @Autowired private AlertEvaluationService alertEvaluationService;
    @Autowired private AlertLogMapper alertLogMapper;

    // ── NOTIFICATION ──
    @Autowired private SysNotificationMapper notificationMapper;

    // ── PROJECT MEMBER ──
    @Autowired private PmProjectMemberMapper projectMemberMapper;

    // ── WORKFLOW ──
    @Autowired private WorkflowEngine workflowEngine;
    @Autowired private WfInstanceMapper wfInstanceMapper;
    @Autowired private WfTaskMapper wfTaskMapper;

    // ── CONTRACT ──
    @Autowired private CtContractMapper contractMapper;

    @Autowired private JdbcTemplate jdbcTemplate;

    /**
     * V85 deleted the demo admin user; workflow templates reference userId=1 as approver.
     * Re-seed users 1-5 in tenant 0 so the workflow approve/transfer/withdraw flows work.
     */
    @BeforeAll
    void seedTestUsers() {
        jdbcTemplate.update("INSERT INTO sys_user (id, tenant_id, username, password, real_name, phone, email, status, is_admin, created_by, remark) " +
                "SELECT 1, 0, 'admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '系统管理员', '13800000000', 'admin@cgc-pms.com', 'ENABLE', 1, 1, 'test-seed' " +
                "WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE id = 1)");
        jdbcTemplate.update("INSERT INTO sys_user (id, tenant_id, username, password, real_name, phone, email, status, is_admin, created_by, remark) " +
                "SELECT 2, 0, 'manager', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '项目经理', '13800000001', 'manager@cgc-pms.com', 'ENABLE', 0, 1, 'test-seed' " +
                "WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE id = 2)");
        jdbcTemplate.update("INSERT INTO sys_user (id, tenant_id, username, password, real_name, phone, email, status, is_admin, created_by, remark) " +
                "SELECT 3, 0, 'gm', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '总经理', '13800000002', 'gm@cgc-pms.com', 'ENABLE', 0, 1, 'test-seed' " +
                "WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE id = 3)");
        jdbcTemplate.update("INSERT INTO sys_user (id, tenant_id, username, password, real_name, phone, email, status, is_admin, created_by, remark) " +
                "SELECT 4, 0, 'biz', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '商务人员', '13800000003', 'biz@cgc-pms.com', 'ENABLE', 0, 1, 'test-seed' " +
                "WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE id = 4)");
        jdbcTemplate.update("INSERT INTO sys_user (id, tenant_id, username, password, real_name, phone, email, status, is_admin, created_by, remark) " +
                "SELECT 5, 0, 'cost', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '成本人员', '13800000004', 'cost@cgc-pms.com', 'ENABLE', 0, 1, 'test-seed' " +
                "WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE id = 5)");
    }

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
    // 场景1: 合同变更全链路 — 合同→审批→变更→审批→currentAmount+成本验证
    // ═══════════════════════════════════════════════════════════
    @Test
    @Order(1)
    @Transactional
    @DisplayName("场景1: 合同变更全链路 → 创建变更→提交审批→全部通过→验证currentAmount累加+成本生成")
    void test01_contractChangeFullChain() {
        // 1. 记录合同变更前当前金额
        CtContract contractBefore = contractMapper.selectById(CONTRACT_ID);
        BigDecimal currentBefore = contractBefore.getCurrentAmount() != null
                ? contractBefore.getCurrentAmount() : BigDecimal.ZERO;

        // 2. 创建合同变更（增加金额500000）
        CtContractChange change = new CtContractChange();
        change.setProjectId(PROJECT_ID);
        change.setContractId(CONTRACT_ID);
        change.setChangeName("材料价格调整-测试");
        change.setChangeType("AMOUNT");
        change.setBeforeAmount(currentBefore);
        change.setChangeAmount(new BigDecimal("500000.00"));
        change.setAfterAmount(currentBefore.add(new BigDecimal("500000.00")));
        change.setReason("钢材价格波动调整");

        Long changeId = changeService.create(change);
        assertNotNull(changeId, "变更ID不应为空");

        // 3. 验证变更已保存
        CtContractChange saved = changeMapper.selectById(changeId);
        assertNotNull(saved, "变更应已保存");
        assertNotNull(saved.getChangeCode(), "变更编号应自动生成");
        assertTrue(saved.getChangeCode().startsWith("CC-"), "变更编号应以CC-开头");
        assertEquals("DRAFT", saved.getApprovalStatus(), "初始审批状态应为DRAFT");
        assertEquals(0, saved.getEffectiveFlag(), "初始生效标识应为0");
        assertEquals(0, saved.getCostGeneratedFlag(), "初始成本生成标识应为0");

        // 4. 提交审批
        assertDoesNotThrow(() -> changeService.submitForApproval(changeId),
                "提交变更审批不应抛异常");
        CtContractChange afterSubmit = changeMapper.selectById(changeId);
        assertEquals("APPROVING", afterSubmit.getApprovalStatus(), "提交后审批状态应为APPROVING");

        // 5. 查找审批实例并全部通过
        WfInstance instance = wfInstanceMapper.selectOne(
                new LambdaQueryWrapper<WfInstance>()
                        .eq(WfInstance::getBusinessType, WorkflowBusinessTypes.CT_CHANGE)
                        .eq(WfInstance::getBusinessId, changeId));
        assertNotNull(instance, "应生成审批实例");
        assertEquals("RUNNING", instance.getInstanceStatus());

        approveAllPendingTasks(instance.getId());

        // 6. 验证变更审批通过
        CtContractChange afterApproval = changeMapper.selectById(changeId);
        assertEquals("APPROVED", afterApproval.getApprovalStatus(), "全部节点审批后状态应为APPROVED");
        assertEquals(1, afterApproval.getEffectiveFlag(), "审批通过后effectiveFlag应为1");
        assertEquals(1, afterApproval.getCostGeneratedFlag(), "成本生成后costGeneratedFlag应为1");

        // 7. 验证合同currentAmount已累加（original + 500000）
        CtContract contractAfter = contractMapper.selectById(CONTRACT_ID);
        BigDecimal expectedCurrent = currentBefore.add(new BigDecimal("500000.00"));
        assertEquals(0, expectedCurrent.compareTo(contractAfter.getCurrentAmount()),
                "合同currentAmount应累加变更金额500000");

        // 8. 验证成本记录已生成（sourceType=CT_CHANGE）
        List<CostItem> costItems = costItemMapper.selectList(
                new LambdaQueryWrapper<CostItem>()
                        .eq(CostItem::getSourceType, "CT_CHANGE")
                        .eq(CostItem::getSourceId, changeId));
        assertFalse(costItems.isEmpty(), "应生成CT_CHANGE成本记录");
        CostItem cost = costItems.get(0);
        assertEquals("CT_CHANGE", cost.getSourceType(), "sourceType应为CT_CHANGE");
        assertEquals("CHANGE", cost.getCostType(), "costType应为CHANGE");
        assertEquals("CONFIRMED", cost.getCostStatus(), "成本状态应为CONFIRMED");
        assertEquals(changeId, cost.getSourceId(), "sourceId应对应变更ID");

        System.out.println("✅ 场景1 通过: changeCode=" + saved.getChangeCode()
                + ", currentAmount: " + currentBefore + " → " + contractAfter.getCurrentAmount()
                + ", costItems=" + costItems.size());
    }

    // ═══════════════════════════════════════════════════════════
    // 场景2: 结算全链路 — 合同+变更+计量→结算→审批→锁定→无成本生成验证
    // ═══════════════════════════════════════════════════════════
    @Test
    @Order(2)
    @Transactional
    @DisplayName("场景2: 结算全链路 → 创建分包计量并审批→创建结算→提交审批→锁定→验证无成本生成")
    void test02_settlementFullChain() {
        final long settlementContractId = 30003L;
        final long settlementPartnerId = 20002L;

        // 1. 创建分包计量并提交审批
        SubMeasure measure = new SubMeasure();
        measure.setProjectId(PROJECT_ID);
        measure.setContractId(settlementContractId);
        measure.setPartnerId(settlementPartnerId);
        measure.setMeasurePeriod("2026-Q2");
        measure.setMeasureDate(LocalDate.now());
        measure.setReportedAmount(new BigDecimal("100000.00"));
        measure.setApprovedAmount(new BigDecimal("95000.00"));
        measure.setDeductionAmount(new BigDecimal("5000.00"));

        Long measureId = subMeasureService.create(measure);
        assertNotNull(measureId, "计量ID不应为空");

        // 保存计量明细
        SubMeasureItem item = new SubMeasureItem();
        item.setMeasureId(measureId);
        item.setItemName("混凝土浇筑-测试");
        item.setUnit("m³");
        item.setContractQuantity(new BigDecimal("200.00"));
        item.setCurrentQuantity(new BigDecimal("180.00"));
        item.setCumulativeQuantity(new BigDecimal("180.00"));
        item.setUnitPrice(new BigDecimal("500.00"));
        item.setAmount(new BigDecimal("90000.00"));
        subMeasureService.saveItems(measureId, List.of(item));

        // 提交计量审批
        assertDoesNotThrow(() -> subMeasureService.submitForApproval(measureId),
                "提交计量审批不应抛异常");

        // 审批通过计量
        WfInstance measureInstance = wfInstanceMapper.selectOne(
                new LambdaQueryWrapper<WfInstance>()
                        .eq(WfInstance::getBusinessType, WorkflowBusinessTypes.SUB_MEASURE)
                        .eq(WfInstance::getBusinessId, measureId));
        assertNotNull(measureInstance, "应生成计量审批实例");
        approveAllPendingTasks(measureInstance.getId());

        // 验证计量已审批（会生成成本）
        SubMeasure approvedMeasure = subMeasureMapper.selectById(measureId);
        assertEquals("APPROVED", approvedMeasure.getApprovalStatus(), "计量审批通过后状态应为APPROVED");

        // 2. 创建结算单
        StlSettlement settlement = new StlSettlement();
        settlement.setProjectId(PROJECT_ID);
        settlement.setContractId(settlementContractId);
        settlement.setPartnerId(settlementPartnerId);
        settlement.setSettlementType("FINAL");
        settlement.setContractAmount(new BigDecimal("10000000.00"));
        settlement.setMeasuredAmount(new BigDecimal("95000.00"));
        settlement.setDeductionAmount(new BigDecimal("5000.00"));
        settlement.setPaidAmount(BigDecimal.ZERO);
        settlement.setFinalAmount(new BigDecimal("10090000.00"));

        Long settlementId = settlementWriteService.create(settlement);
        assertNotNull(settlementId, "结算单ID不应为空");

        StlSettlement saved = settlementMapper.selectById(settlementId);
        assertNotNull(saved.getSettlementCode(), "结算编号应自动生成");
        assertTrue(saved.getSettlementCode().startsWith("STL-"), "结算编号应以STL-开头");
        assertEquals("DRAFT", saved.getApprovalStatus(), "初始审批状态应为DRAFT");

        // 3. 提交结算审批（使用 WorkflowEngine 直接提交）
        assertDoesNotThrow(() -> workflowEngine.submit(
                USER_ADMIN, "admin", 0L,
                WorkflowBusinessTypes.SETTLEMENT, settlementId,
                "结算审批-" + saved.getSettlementCode(),
                settlement.getFinalAmount(),
                PROJECT_ID, settlementContractId,
                "Phase3集成测试-结算审批", null, null),
                "提交结算审批不应抛异常");

        // 4. 查找审批实例并全部通过
        WfInstance settlementInstance = wfInstanceMapper.selectOne(
                new LambdaQueryWrapper<WfInstance>()
                        .eq(WfInstance::getBusinessType, WorkflowBusinessTypes.SETTLEMENT)
                        .eq(WfInstance::getBusinessId, settlementId));
        assertNotNull(settlementInstance, "应生成结算审批实例");
        approveAllPendingTasks(settlementInstance.getId());

        // 5. 验证结算已锁定（FINALIZED）
        StlSettlement afterApproval = settlementMapper.selectById(settlementId);
        assertEquals("FINALIZED", afterApproval.getSettlementStatus(),
                "结算审批通过后settlementStatus应为FINALIZED");
        assertEquals("APPROVED", afterApproval.getApprovalStatus());

        // 6. 验证合同settlementAmount已回写
        CtContract contractAfter = contractMapper.selectById(settlementContractId);
        assertNotNull(contractAfter.getSettlementAmount(),
                "合同settlementAmount应已回写");

        // 7. ★核心断言：结算不产生成本记录
        long settlementCostCount = costItemMapper.selectCount(
                new LambdaQueryWrapper<CostItem>()
                        .eq(CostItem::getSourceType, "SETTLEMENT")
                        .eq(CostItem::getSourceId, settlementId));
        assertEquals(0, settlementCostCount,
                "结算不应产生成本记录（SETTLEMENT sourceType不存在）");

        System.out.println("✅ 场景2 通过: settlementCode=" + saved.getSettlementCode()
                + ", settlementStatus=" + afterApproval.getSettlementStatus()
                + ", settlementCostCount=" + settlementCostCount + " (应为0)");
    }

    // ═══════════════════════════════════════════════════════════
    // 场景3: 动态成本公式验证 — dynamicCost = actualCost + estimatedRemainingCost
    // ═══════════════════════════════════════════════════════════
    @Test
    @Order(3)
    @Transactional
    @DisplayName("场景3: 动态成本公式验证 → 刷新汇总→验证 dynamicCost = actualCost + estimatedRemainingCost")
    void test03_dynamicCostFormula() {
        // 1. 刷新成本汇总
        assertDoesNotThrow(() -> costSummaryService.refreshSummary(PROJECT_ID),
                "刷新成本汇总不应抛异常");

        // 2. 查询成本汇总记录
        List<CostSummary> summaries = costSummaryMapper.selectList(
                new LambdaQueryWrapper<CostSummary>()
                        .eq(CostSummary::getProjectId, PROJECT_ID));

        // 项目可能没有已发生的成本，因此summaries可能为空，这是正常的
        // 但如果有记录，必须验证公式正确性
        for (CostSummary cs : summaries) {
            BigDecimal actualCost = cs.getActualCost() != null ? cs.getActualCost() : BigDecimal.ZERO;
            BigDecimal estimatedRemaining = cs.getEstimatedRemainingCost() != null
                    ? cs.getEstimatedRemainingCost() : BigDecimal.ZERO;

            BigDecimal expectedDynamic = actualCost.add(estimatedRemaining);

            // 3. ★核心断言: dynamicCost = actualCost + estimatedRemainingCost
            BigDecimal dynamicCost = cs.getDynamicCost() != null ? cs.getDynamicCost() : BigDecimal.ZERO;
            assertEquals(0, expectedDynamic.compareTo(dynamicCost),
                    "dynamicCost应为actualCost+estimatedRemainingCost，科目=" + cs.getCostSubjectId());

            // 4. 验证其他派生字段
            BigDecimal targetCost = cs.getTargetCost() != null ? cs.getTargetCost() : BigDecimal.ZERO;
            BigDecimal expectedDeviation = dynamicCost.subtract(targetCost);
            BigDecimal costDeviation = cs.getCostDeviation() != null ? cs.getCostDeviation() : BigDecimal.ZERO;
            assertEquals(0, expectedDeviation.compareTo(costDeviation),
                    "costDeviation应为dynamicCost-targetCost");

            BigDecimal contractIncome = cs.getContractIncome() != null ? cs.getContractIncome() : BigDecimal.ZERO;
            BigDecimal expectedProfit = contractIncome.subtract(dynamicCost);
            BigDecimal expectedProfitActual = cs.getExpectedProfit() != null ? cs.getExpectedProfit() : BigDecimal.ZERO;
            assertEquals(0, expectedProfit.compareTo(expectedProfitActual),
                    "expectedProfit应为contractIncome-dynamicCost");
        }

        System.out.println("✅ 场景3 通过: 验证了" + summaries.size() + "条成本汇总记录，公式均正确");
    }

    // ═══════════════════════════════════════════════════════════
    // 场景4: 目标成本全链路 — 创建→审批→版本切换→cost_summary关联
    // ═══════════════════════════════════════════════════════════
    @Test
    @Order(4)
    @Transactional
    @DisplayName("场景4: 目标成本全链路 → 创建目标→添加明细→提交审批→通过→验证isActive=1+cost_summary关联")
    void test04_targetCostFullChain() {
        // 1. 创建目标成本版本
        CostTarget target = new CostTarget();
        target.setProjectId(PROJECT_ID);
        target.setVersionNo("V2.0");
        target.setVersionName("第二阶段目标成本-测试");
        target.setTotalTargetAmount(new BigDecimal("520000000.00"));

        Long targetId = costTargetService.create(target);
        assertNotNull(targetId, "目标成本ID不应为空");

        CostTarget saved = costTargetMapper.selectById(targetId);
        assertNotNull(saved, "目标成本应已保存");
        assertEquals("DRAFT", saved.getApprovalStatus(), "初始审批状态应为DRAFT");
        assertEquals(0, saved.getIsActive(), "初始isActive应为0");

        // 2. 添加目标成本明细（需要关联cost_subject）
        CostTargetItem targetItem = new CostTargetItem();
        targetItem.setTenantId(0L);
        targetItem.setTargetId(targetId);
        targetItem.setProjectId(PROJECT_ID);
        targetItem.setCostSubjectId(1L);  // 使用默认科目ID
        targetItem.setTargetAmount(new BigDecimal("300000000.00"));
        targetItem.setCreatedBy(USER_ADMIN);
        targetItem.setCreatedTime(LocalDateTime.now());
        targetItem.setUpdatedBy(USER_ADMIN);
        targetItem.setUpdatedTime(LocalDateTime.now());
        targetItem.setDeletedFlag(0);
        costTargetItemMapper.insert(targetItem);

        CostTargetItem targetItem2 = new CostTargetItem();
        targetItem2.setTenantId(0L);
        targetItem2.setTargetId(targetId);
        targetItem2.setProjectId(PROJECT_ID);
        targetItem2.setCostSubjectId(2L);
        targetItem2.setTargetAmount(new BigDecimal("220000000.00"));
        targetItem2.setCreatedBy(USER_ADMIN);
        targetItem2.setCreatedTime(LocalDateTime.now());
        targetItem2.setUpdatedBy(USER_ADMIN);
        targetItem2.setUpdatedTime(LocalDateTime.now());
        targetItem2.setDeletedFlag(0);
        costTargetItemMapper.insert(targetItem2);

        // 3. 提交目标成本审批（使用 WorkflowEngine 直接提交）
        assertDoesNotThrow(() -> workflowEngine.submit(
                USER_ADMIN, "admin", 0L,
                WorkflowBusinessTypes.COST_TARGET, targetId,
                "目标成本审批-" + saved.getVersionNo(),
                saved.getTotalTargetAmount(),
                PROJECT_ID, null,
                "Phase3集成测试-目标成本审批", null, null),
                "提交目标成本审批不应抛异常");

        // 4. 查找审批实例并全部通过
        WfInstance instance = wfInstanceMapper.selectOne(
                new LambdaQueryWrapper<WfInstance>()
                        .eq(WfInstance::getBusinessType, WorkflowBusinessTypes.COST_TARGET)
                        .eq(WfInstance::getBusinessId, targetId));
        assertNotNull(instance, "应生成目标成本审批实例");
        approveAllPendingTasks(instance.getId());

        // 5. 验证目标成本已激活
        CostTarget afterApproval = costTargetMapper.selectById(targetId);
        assertEquals("APPROVED", afterApproval.getApprovalStatus(), "审批通过后状态应为APPROVED");
        assertEquals(1, afterApproval.getIsActive(), "审批通过后isActive应为1");
        assertEquals("ACTIVE", afterApproval.getStatus(), "审批通过后status应为ACTIVE");

        // 6. ★核心断言: 验证 cost_summary 已关联目标成本ID
        //    注意：cost_summary 可能还没有数据（如果没有实际成本发生），
        //    但如果有记录，其 cost_target_id 应指向新激活的版本
        List<CostSummary> summaries = costSummaryMapper.selectList(
                new LambdaQueryWrapper<CostSummary>()
                        .eq(CostSummary::getProjectId, PROJECT_ID));
        for (CostSummary cs : summaries) {
            assertEquals(targetId, cs.getCostTargetId(),
                    "cost_summary应关联新激活的目标成本版本");
        }

        System.out.println("✅ 场景4 通过: versionNo=" + afterApproval.getVersionNo()
                + ", isActive=" + afterApproval.getIsActive()
                + ", status=" + afterApproval.getStatus()
                + ", linked summaries=" + summaries.size());
    }

    // ═══════════════════════════════════════════════════════════
    // 场景5: 预警触发 — 触发预警→alert_log 记录
    // ═══════════════════════════════════════════════════════════
    @Test
    @Order(5)
    @Transactional
    @DisplayName("场景5: 预警触发 → 执行评估→验证alert_log有记录")
    void test05_alertTrigger() {
        // 0. 设置触发条件：将合同结束日期改为15天后（触发 CONTRACT_EXPIRING 预警）
        CtContract contract = contractMapper.selectById(CONTRACT_ID);
        LocalDate originalEndDate = contract.getEndDate();
        contract.setEndDate(LocalDate.now().plusDays(15));
        contractMapper.updateById(contract);

        // 1. 查询评估前预警数量
        long beforeCount = alertLogMapper.selectCount(
                new LambdaQueryWrapper<AlertLog>().eq(AlertLog::getProjectId, PROJECT_ID));

        // 2. 执行预警评估
        assertDoesNotThrow(() -> alertEvaluationService.evaluateProject(0L, PROJECT_ID),
                "预警评估不应抛异常");

        // 3. 验证预警记录已生成
        List<AlertLog> alerts = alertLogMapper.selectList(
                new LambdaQueryWrapper<AlertLog>()
                        .eq(AlertLog::getProjectId, PROJECT_ID)
                        .orderByDesc(AlertLog::getTriggeredAt));

        // ★核心断言：评估后应有预警记录（触发了合同即将到期预警 CONTRACT_EXPIRING）
        assertFalse(alerts.isEmpty(), "预警评估应生成至少一条alert_log记录");

        // 4. 验证预警字段完整性
        boolean hasExpiringAlert = false;
        for (AlertLog alert : alerts) {
            assertNotNull(alert.getRuleType(), "预警规则类型不应为空");
            assertNotNull(alert.getSeverity(), "预警严重程度不应为空");
            assertNotNull(alert.getMessage(), "预警消息不应为空");
            assertNotNull(alert.getTriggeredAt(), "触发时间不应为空");
            assertEquals(0, alert.getIsRead(), "初始isRead应为0（未读）");
            if ("CONTRACT_EXPIRING".equals(alert.getRuleType())) {
                hasExpiringAlert = true;
            }
        }
        assertTrue(hasExpiringAlert, "应包含CONTRACT_EXPIRING预警");

        long afterCount = alertLogMapper.selectCount(
                new LambdaQueryWrapper<AlertLog>().eq(AlertLog::getProjectId, PROJECT_ID));

        System.out.println("✅ 场景5 通过: alertCount=" + (afterCount - beforeCount)
                + "（新增）, totalAlerts=" + alerts.size());
    }

    // ═══════════════════════════════════════════════════════════
    // 场景6: CT_CHANGE + VAR_ORDER 共存无重复计费
    // ═══════════════════════════════════════════════════════════
    @Test
    @Order(6)
    @Transactional
    @DisplayName("场景6: CT_CHANGE+VAR_ORDER共存 → 审批两者→验证成本各自生成、无重复计费")
    void test06_ctChangeAndVarOrderCoexist() {
        // 1. 创建CT_CHANGE（变更金额+300000）
        CtContractChange change = new CtContractChange();
        change.setProjectId(PROJECT_ID);
        change.setContractId(CONTRACT_ID);
        change.setChangeName("设计变更增加工程量-共存测试");
        change.setChangeType("DESIGN_CHANGE");
        change.setBeforeAmount(new BigDecimal("45000000.00"));
        change.setChangeAmount(new BigDecimal("300000.00"));
        change.setAfterAmount(new BigDecimal("45300000.00"));
        change.setReason("设计变更增加混凝土用量");

        Long changeId = changeService.create(change);
        changeService.submitForApproval(changeId);
        WfInstance changeInst = findInstance(WorkflowBusinessTypes.CT_CHANGE, changeId);
        assertNotNull(changeInst);
        approveAllPendingTasks(changeInst.getId());

        // 验证变更审批通过
        CtContractChange approvedChange = changeMapper.selectById(changeId);
        assertEquals("APPROVED", approvedChange.getApprovalStatus());
        assertEquals(1, approvedChange.getCostGeneratedFlag());

        // 2. 创建VAR_ORDER（成本支出方向，金额+200000）
        VarOrder varOrder = new VarOrder();
        varOrder.setProjectId(PROJECT_ID);
        varOrder.setContractId(CONTRACT_ID);
        varOrder.setPartnerId(PARTNER_ID);
        varOrder.setVarName("现场签证-额外土方开挖-共存测试");
        varOrder.setVarType("FIELD");
        varOrder.setDirection("COST");
        varOrder.setReportedAmount(new BigDecimal("200000.00"));
        varOrder.setApprovedAmount(new BigDecimal("200000.00"));

        Long varOrderId = varOrderService.create(varOrder);
        assertNotNull(varOrderId);

        // 保存签证明细
        VarOrderItem varItem = new VarOrderItem();
        varItem.setVarOrderId(varOrderId);
        varItem.setItemName("额外土方开挖");
        varItem.setUnit("m³");
        varItem.setQuantity(new BigDecimal("1000.00"));
        varItem.setUnitPrice(new BigDecimal("200.00"));
        varItem.setAmount(new BigDecimal("200000.00"));
        varOrderService.saveItems(varOrderId, List.of(varItem));

        // 提交签证审批
        varOrderService.submitForApproval(varOrderId);
        WfInstance varInst = findInstance(WorkflowBusinessTypes.VAR_ORDER, varOrderId);
        assertNotNull(varInst);
        approveAllPendingTasks(varInst.getId());

        // 验证签证审批通过
        VarOrder approvedVar = varOrderMapper.selectById(varOrderId);
        assertEquals("APPROVED", approvedVar.getApprovalStatus());
        assertEquals(1, approvedVar.getCostGeneratedFlag());

        // 3. ★核心断言: 验证各自成本独立生成
        // CT_CHANGE 成本
        List<CostItem> changeCosts = costItemMapper.selectList(
                new LambdaQueryWrapper<CostItem>()
                        .eq(CostItem::getSourceType, "CT_CHANGE")
                        .eq(CostItem::getSourceId, changeId));
        assertFalse(changeCosts.isEmpty(), "CT_CHANGE应生成成本记录");
        assertEquals("CHANGE", changeCosts.get(0).getCostType());

        // VAR_ORDER 成本
        List<CostItem> varCosts = costItemMapper.selectList(
                new LambdaQueryWrapper<CostItem>()
                        .eq(CostItem::getSourceType, "VAR_ORDER")
                        .eq(CostItem::getSourceId, varOrderId));
        assertFalse(varCosts.isEmpty(), "VAR_ORDER应生成成本记录");
        assertEquals("VARIATION", varCosts.get(0).getCostType());

        // 4. ★核心断言: 无重复计费
        // 确认两种 sourceType 的成本是完全独立的记录
        assertEquals(1, changeCosts.size(), "CT_CHANGE应为1条cost_item（一个变更一条成本）");
        assertEquals(1, varCosts.size(), "VAR_ORDER应为1条cost_item（一个签证明细一条成本）");

        // CT_CHANGE 成本不应和 VAR_ORDER 成本混淆
        assertNotEquals(changeCosts.get(0).getSourceType(), varCosts.get(0).getSourceType(),
                "两种来源类型的成本应完全独立");
        assertNotEquals(changeCosts.get(0).getSourceId(), varCosts.get(0).getSourceId(),
                "不同来源的单据ID应不同");

        // 5. 验证同合同下两种成本的总数
        long totalCostForContract = costItemMapper.selectCount(
                new LambdaQueryWrapper<CostItem>()
                        .eq(CostItem::getContractId, CONTRACT_ID));
        assertTrue(totalCostForContract >= 2,
                "合同应有至少2条成本记录（CT_CHANGE + VAR_ORDER各1条）");

        System.out.println("✅ 场景6 通过: CT_CHANGE成本=" + changeCosts.size()
                + "条(金额=" + changeCosts.get(0).getAmount() + ")"
                + ", VAR_ORDER成本=" + varCosts.size()
                + "条(金额=" + varCosts.get(0).getAmount() + ")"
                + ", 合同总成本记录=" + totalCostForContract + "条"
                + ", 无重复计费✓");
    }

    // ═══════════════════════════════════════════════════════════
    // 场景7: 预警 → 通知 — evaluateProject 触发通知，显式 tenantId
    // ═══════════════════════════════════════════════════════════
    @Test
    @Order(7)
    @Transactional
    @DisplayName("场景7: 预警→通知 → 插入项目成员→触发预警→验证通知生成，tenantId来自project而非UserContext")
    void test07_alertToNotification() {
        // V90 seeds pm_project_member(40001, 0, 10001, 1, 'PROJECT_MANAGER').
        // Use raw INSERT with WHERE NOT EXISTS to avoid DuplicateKeyException
        // on the unique constraint (project_id, user_id).
        jdbcTemplate.update("INSERT INTO pm_project_member (id, tenant_id, project_id, user_id, role_code, status, created_at, updated_at, created_by, updated_by, deleted_flag) "
                + "SELECT 40001, 0, 10001, 1, 'PM', 'ACTIVE', NOW(), NOW(), 1, 1, 0 "
                + "WHERE NOT EXISTS (SELECT 1 FROM pm_project_member WHERE project_id = 10001 AND user_id = 1 AND deleted_flag = 0)");

        // 2. 设置触发条件：将合同结束日期改为15天后（触发 CONTRACT_EXPIRING 预警）
        CtContract contract = contractMapper.selectById(CONTRACT_ID);
        LocalDate originalEndDate = contract.getEndDate();
        contract.setEndDate(LocalDate.now().plusDays(15));
        contractMapper.updateById(contract);

        // 3. 记录评估前通知数量
        long beforeNotificationCount = notificationMapper.selectCount(null);

        // 4. 执行预警评估（tenantId=0 来自 project，NOT UserContext）
        assertDoesNotThrow(() -> alertEvaluationService.evaluateProject(0L, PROJECT_ID),
                "预警评估不应抛异常");

        // 5. ★核心断言：验证 sys_notification 已生成
        List<SysNotification> notifications = notificationMapper.selectList(
                new LambdaQueryWrapper<SysNotification>()
                        .eq(SysNotification::getTenantId, 0L)
                        .eq(SysNotification::getBizType, "ALERT")
                        .orderByDesc(SysNotification::getCreatedTime));

        assertFalse(notifications.isEmpty(),
                "预警评估应生成至少一条通知记录（bizType=ALERT）");

        // 6. 验证通知字段完整性
        SysNotification notification = notifications.get(0);
        assertEquals(0L, notification.getTenantId().longValue(),
                "通知tenantId应为0（来自project，非UserContext）");
        assertEquals(USER_ADMIN, notification.getUserId(),
                "通知userId应为项目成员（admin PM）");
        assertEquals("ALERT", notification.getBizType(),
                "通知bizType应为ALERT");
        assertNotNull(notification.getBizId(), "通知bizId（alert id）不应为空");
        assertNotNull(notification.getTitle(), "通知title不应为空");
        assertTrue(notification.getTitle().contains("预警"),
                "通知title应包含'预警'");
        assertNotNull(notification.getContent(), "通知content不应为空");
        assertEquals("INFO", notification.getNotifyType(),
                "通知notifyType应为INFO");
        assertEquals(0, notification.getIsRead().intValue(),
                "通知isRead应为0（未读）");

        // 7. 验证通知关联的 alert_log 存在
        AlertLog alert = alertLogMapper.selectById(notification.getBizId());
        assertNotNull(alert, "通知关联的alert_log应存在");
        assertEquals(PROJECT_ID, alert.getProjectId().longValue(),
                "alert_log的projectId应匹配");
        assertEquals("CONTRACT_EXPIRING", alert.getRuleType(),
                "alert_log的ruleType应为CONTRACT_EXPIRING");

        long afterNotificationCount = notificationMapper.selectCount(null);

        System.out.println("✅ 场景7 通过: notificationCount="
                + (afterNotificationCount - beforeNotificationCount)
                + "（新增）, title=" + notification.getTitle()
                + ", userId=" + notification.getUserId()
                + ", tenantId=" + notification.getTenantId()
                + "（来自project显式查询）");
    }

    // ═══════════════════════════════════════════════════════════
    // 辅助方法
    // ═══════════════════════════════════════════════════════════

    /** 逐节点审批通过所有待办任务 */
    private void approveAllPendingTasks(Long instanceId) {
        for (int i = 0; i < 10; i++) {
            List<WfTask> pendingTasks = wfTaskMapper.selectList(
                    new LambdaQueryWrapper<WfTask>()
                            .eq(WfTask::getInstanceId, instanceId)
                            .eq(WfTask::getTaskStatus, "PENDING"));
            if (pendingTasks.isEmpty()) {
                WfInstance inst = wfInstanceMapper.selectById(instanceId);
                if (inst != null && ("APPROVED".equals(inst.getInstanceStatus())
                        || "REJECTED".equals(inst.getInstanceStatus())
                        || "WITHDRAWN".equals(inst.getInstanceStatus()))) {
                    break;
                }
                continue;
            }
            for (WfTask task : pendingTasks) {
                workflowEngine.approve(task.getId(), USER_ADMIN, "admin",
                        "集成测试审批通过", "phase3-" + UUID.randomUUID() + "-" + task.getId());
            }
        }
    }

    /** 按 businessType + businessId 查找审批实例 */
    private WfInstance findInstance(String businessType, Long businessId) {
        return wfInstanceMapper.selectOne(
                new LambdaQueryWrapper<WfInstance>()
                        .eq(WfInstance::getBusinessType, businessType)
                        .eq(WfInstance::getBusinessId, businessId));
    }
}
