package com.cgcpms;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.cost.service.CostGenerationService;
import com.cgcpms.cost.service.CostLedgerService;
import com.cgcpms.cost.service.CostSummaryService;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.entity.PayApplicationBasis;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayApplicationBasisMapper;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.payment.service.PayApplicationService;
import com.cgcpms.payment.service.PayRecordService;
import com.cgcpms.purchase.entity.MatPurchaseOrder;
import com.cgcpms.purchase.entity.MatPurchaseOrderItem;
import com.cgcpms.purchase.mapper.MatPurchaseOrderItemMapper;
import com.cgcpms.purchase.mapper.MatPurchaseOrderMapper;
import com.cgcpms.purchase.service.MatPurchaseOrderService;
import com.cgcpms.receipt.entity.MatReceipt;
import com.cgcpms.receipt.entity.MatReceiptItem;
import com.cgcpms.receipt.mapper.MatReceiptItemMapper;
import com.cgcpms.receipt.mapper.MatReceiptMapper;
import com.cgcpms.receipt.service.MatReceiptService;
import com.cgcpms.system.entity.SysUser;
import com.cgcpms.system.mapper.SysUserMapper;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.entity.WfTask;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import com.cgcpms.workflow.mapper.WfTaskMapper;
import com.cgcpms.workflow.service.WorkflowEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Phase2FullChainIntegrationTest {

    private static final long USER_ADMIN = 1L;

    /** Demo data: PERFORMING contract CT-2026-001 */
    private static final long CONTRACT_ID = 30001L;
    /** Demo data: ONGOING project PRJ-2026-001 */
    private static final long PROJECT_ID = 10001L;
    /** Demo data: supplier partner */
    private static final long PARTNER_ID = 20001L;

    @Autowired private MatPurchaseOrderService purchaseOrderService;
    @Autowired private MatReceiptService receiptService;
    @Autowired private PayApplicationService payApplicationService;
    @Autowired private PayRecordService payRecordService;
    @Autowired private CostGenerationService costGenerationService;
    @Autowired private CostLedgerService costLedgerService;
    @Autowired private CostSummaryService costSummaryService;
    @Autowired private WorkflowEngine workflowEngine;

    @Autowired private MatPurchaseOrderMapper purchaseOrderMapper;
    @Autowired private MatPurchaseOrderItemMapper purchaseOrderItemMapper;
    @Autowired private MatReceiptMapper receiptMapper;
    @Autowired private MatReceiptItemMapper receiptItemMapper;
    @Autowired private PayApplicationMapper payApplicationMapper;
    @Autowired private PayApplicationBasisMapper payApplicationBasisMapper;
    @Autowired private PayRecordMapper payRecordMapper;
    @Autowired private CostItemMapper costItemMapper;
    @Autowired private CtContractMapper contractMapper;
    @Autowired private SysUserMapper sysUserMapper;
    @Autowired private WfInstanceMapper wfInstanceMapper;
    @Autowired private WfTaskMapper wfTaskMapper;

    @BeforeEach
    void setupContext() {
        seedAdminUser();
        TestUserContext.setAdmin(TestUserContext.TENANT_0, USER_ADMIN);
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    // ═══════════════════════════════════════════════════════════
    // 场景1: 采购订单 CRUD
    // ═══════════════════════════════════════════════════════════
    @Test
    @Order(1)
    @Transactional
    @DisplayName("场景1: 创建采购订单 → 保存明细 → 验证已保存")
    void test01_purchaseOrderCRUD() {
        // 1. 创建采购订单
        MatPurchaseOrder order = new MatPurchaseOrder();
        order.setProjectId(PROJECT_ID);
        order.setContractId(CONTRACT_ID);
        order.setPartnerId(PARTNER_ID);
        order.setOrderType("NORMAL");
        order.setOrderDate(LocalDate.now());
        order.setDeliveryDate(LocalDate.now().plusDays(30));
        order.setRemark("Phase2全链路集成测试-采购订单");

        Long orderId = purchaseOrderService.create(order);
        assertNotNull(orderId, "采购订单ID不应为空");

        // 2. 验证订单已保存（自动生成编号）
        MatPurchaseOrder saved = purchaseOrderMapper.selectById(orderId);
        assertNotNull(saved, "采购订单应已保存");
        assertNotNull(saved.getOrderCode(), "订单编号应自动生成");
        assertTrue(saved.getOrderCode().startsWith("PO-"), "订单编号应以PO-开头");
        assertEquals("DRAFT", saved.getApprovalStatus(), "初始审批状态应为DRAFT");
        assertEquals("DRAFT", saved.getOrderStatus(), "初始订单状态应为DRAFT");
        assertEquals(PROJECT_ID, saved.getProjectId());

        // 3. 保存订单明细
        MatPurchaseOrderItem item1 = buildOrderItem("C30商品砼", "C30", "m³", 100, 450);
        MatPurchaseOrderItem item2 = buildOrderItem("HRB400螺纹钢", "HRB400 φ25", "t", 50, 3800);
        purchaseOrderService.saveItemsBatch(orderId, List.of(item1, item2));

        // 4. 验证明细已保存
        List<MatPurchaseOrderItem> items = purchaseOrderItemMapper.selectList(
                new LambdaQueryWrapper<MatPurchaseOrderItem>()
                        .eq(MatPurchaseOrderItem::getOrderId, orderId));
        assertEquals(2, items.size(), "应保存2条订单明细");

        // 5. 验证总金额已计算
        MatPurchaseOrder updated = purchaseOrderMapper.selectById(orderId);
        assertNotNull(updated.getTotalAmount(), "总金额应已计算");
        BigDecimal expectedTotal = new BigDecimal("45000.00")   // 100*450
                .add(new BigDecimal("190000.00"));               // 50*3800
        assertEquals(0, expectedTotal.compareTo(updated.getTotalAmount()), "总金额应为明细合计");

        // 6. 更新订单
        updated.setDeliveryDate(LocalDate.now().plusDays(60));
        purchaseOrderService.update(updated);
        MatPurchaseOrder afterUpdate = purchaseOrderMapper.selectById(orderId);
        assertEquals(LocalDate.now().plusDays(60), afterUpdate.getDeliveryDate(), "交付日期应已更新");

        System.out.println("✅ 场景1 通过: orderCode=" + saved.getOrderCode()
                + ", items=" + items.size()
                + ", totalAmount=" + updated.getTotalAmount());
    }

    // ═══════════════════════════════════════════════════════════
    // 场景2: 材料验收单 CRUD
    // ═══════════════════════════════════════════════════════════
    @Test
    @Order(2)
    @Transactional
    @DisplayName("场景2: 创建验收单（关联采购订单）→ 保存明细 → 验证已保存")
    void test02_receiptCRUD() {
        // 1. 先创建采购订单和明细
        Long orderId = createOrderWithItems();

        // 2. 创建验收单（关联采购订单）
        MatReceipt receipt = new MatReceipt();
        receipt.setProjectId(PROJECT_ID);
        receipt.setOrderId(orderId);
        receipt.setReceiptDate(LocalDate.now());
        receipt.setQualityStatus("PASSED");
        receipt.setRemark("Phase2全链路集成测试-验收单");

        Long receiptId = receiptService.create(receipt);
        assertNotNull(receiptId, "验收单ID不应为空");

        // 3. 验证验收单已保存（自动填充合同/供应商）
        MatReceipt saved = receiptMapper.selectById(receiptId);
        assertNotNull(saved, "验收单应已保存");
        assertNotNull(saved.getReceiptCode(), "验收单号应自动生成");
        assertTrue(saved.getReceiptCode().startsWith("MR-"), "验收单号应以MR-开头");
        assertEquals("DRAFT", saved.getApprovalStatus(), "初始审批状态应为DRAFT");
        assertEquals(0, saved.getCostGeneratedFlag(), "初始成本生成标志应为0");
        assertEquals(PROJECT_ID, saved.getProjectId());
        assertEquals(CONTRACT_ID, saved.getContractId(), "应自动填充合同ID");
        assertEquals(PARTNER_ID, saved.getPartnerId(), "应自动填充供应商ID");

        // 4. 保存验收明细
        List<MatPurchaseOrderItem> orderItems = purchaseOrderItemMapper.selectList(
                new LambdaQueryWrapper<MatPurchaseOrderItem>()
                        .eq(MatPurchaseOrderItem::getOrderId, orderId));
        assertEquals(2, orderItems.size(), "应有2条订单明细");

        MatReceiptItem ri1 = buildReceiptItem(orderItems.get(0).getId(),
                new BigDecimal("80"), new BigDecimal("80"), new BigDecimal("450"), new BigDecimal("36000.00"));
        MatReceiptItem ri2 = buildReceiptItem(orderItems.get(1).getId(),
                new BigDecimal("30"), new BigDecimal("30"), new BigDecimal("3800"), new BigDecimal("114000.00"));
        receiptService.saveItemsBatch(receiptId, List.of(ri1, ri2));

        // 5. 验证明细已保存
        List<MatReceiptItem> items = receiptItemMapper.selectList(
                new LambdaQueryWrapper<MatReceiptItem>()
                        .eq(MatReceiptItem::getReceiptId, receiptId));
        assertEquals(2, items.size(), "应保存2条验收明细");
        for (MatReceiptItem item : items) {
            assertNotNull(item.getAmount(), "明细金额不应为空");
            assertNotNull(item.getOrderItemId(), "明细应关联订单明细");
        }

        // 6. 验证总金额
        MatReceipt updated = receiptMapper.selectById(receiptId);
        assertNotNull(updated.getTotalAmount(), "验收总金额应已计算");
        BigDecimal expectedTotal = new BigDecimal("36000.00").add(new BigDecimal("114000.00"));
        assertEquals(0, expectedTotal.compareTo(updated.getTotalAmount()), "验收总金额应为明细合计");

        System.out.println("✅ 场景2 通过: receiptCode=" + saved.getReceiptCode()
                + ", items=" + items.size()
                + ", totalAmount=" + updated.getTotalAmount());
    }

    // ═══════════════════════════════════════════════════════════
    // 场景3: 材料验收审批 + 成本生成
    // ═══════════════════════════════════════════════════════════
    @Test
    @Order(3)
    @Transactional
    @DisplayName("场景3: 提交验收审批 → 模拟审批通过 → 验证cost_item生成（sourceType=MAT_RECEIPT）")
    void test03_receiptApprovalAndCostGeneration() {
        // 1. 创建采购订单+明细，创建验收单+明细
        Long orderId = createOrderWithItems();
        Long receiptId = createReceiptWithItems(orderId);

        // 2. 提交验收审批
        assertDoesNotThrow(() -> receiptService.submitForApproval(receiptId),
                "提交验收审批不应抛异常");
        MatReceipt afterSubmit = receiptMapper.selectById(receiptId);
        assertEquals("APPROVING", afterSubmit.getApprovalStatus(), "提交后审批状态应为APPROVING");

        // 3. 查找审批实例
        WfInstance instance = wfInstanceMapper.selectOne(
                new LambdaQueryWrapper<WfInstance>()
                        .eq(WfInstance::getBusinessType, "MATERIAL_RECEIPT")
                        .eq(WfInstance::getBusinessId, receiptId));
        assertNotNull(instance, "应生成审批实例");
        assertEquals("RUNNING", instance.getInstanceStatus());

        // 4. 逐一审批通过所有节点（模板V14有3个节点，POC模式下均为userId=1）
        approveAllPendingTasks(instance.getId());

        // 5. 验证审批状态变为APPROVED
        MatReceipt afterApproval = receiptMapper.selectById(receiptId);
        assertEquals("APPROVED", afterApproval.getApprovalStatus(), "全部节点审批后状态应为APPROVED");
        // onApproved handler 先更新 status 再 generateCost 再更新 flag
        assertEquals(1, afterApproval.getCostGeneratedFlag(), "成本生成后flag应为1");

        // 6. 验证成本记录已生成
        List<CostItem> costItems = costItemMapper.selectList(
                new LambdaQueryWrapper<CostItem>()
                        .eq(CostItem::getSourceType, "MAT_RECEIPT")
                        .eq(CostItem::getSourceId, receiptId));
        assertFalse(costItems.isEmpty(), "应生成成本记录");
        assertEquals(2, costItems.size(), "应为2条验收明细生成2条成本记录");

        for (CostItem cost : costItems) {
            assertEquals("MAT_RECEIPT", cost.getSourceType(), "sourceType应为MAT_RECEIPT");
            assertEquals("MATERIAL", cost.getCostType(), "costType应为MATERIAL");
            assertEquals(receiptId, cost.getSourceId(), "sourceId应对应验收单ID");
            assertNotNull(cost.getSourceItemId(), "sourceItemId应非空");
            assertEquals("CONFIRMED", cost.getCostStatus(), "成本状态应为CONFIRMED");
            assertEquals(1, cost.getGeneratedFlag(), "应标记为系统生成");
            assertEquals(CONTRACT_ID, cost.getContractId(), "应继承验收单的合同ID");
        }

        // 7. 验证审批实例状态
        WfInstance finalInstance = wfInstanceMapper.selectById(instance.getId());
        assertEquals("APPROVED", finalInstance.getInstanceStatus(), "审批实例状态应为APPROVED");

        System.out.println("✅ 场景3 通过: receiptId=" + receiptId
                + ", costItems=" + costItems.size()
                + ", approvalStatus=" + afterApproval.getApprovalStatus()
                + ", costGeneratedFlag=" + afterApproval.getCostGeneratedFlag());
    }

    // ═══════════════════════════════════════════════════════════
    // 场景4: 成本生成幂等
    // ═══════════════════════════════════════════════════════════
    @Test
    @Order(4)
    @Transactional
    @DisplayName("场景4: 成本生成幂等 → 重复调用generateCost不产生重复记录")
    void test04_costIdempotency() {
        // 1. 创建验收单+明细（不通过审批，直接调用成本生成）
        Long orderId = createOrderWithItems();
        Long receiptId = createReceiptWithItems(orderId);

        // 2. 第一次调用成本生成
        costGenerationService.generateCost("MAT_RECEIPT", receiptId);

        // 3. 查询第一次生成的记录
        List<CostItem> firstBatch = costItemMapper.selectList(
                new LambdaQueryWrapper<CostItem>()
                        .eq(CostItem::getSourceType, "MAT_RECEIPT")
                        .eq(CostItem::getSourceId, receiptId));
        assertFalse(firstBatch.isEmpty(), "第一次调用应生成成本记录");
        assertEquals(2, firstBatch.size(), "应为2条验收明细生成2条成本记录");

        // 验证 source_item_id 对应验收明细
        for (CostItem cost : firstBatch) {
            assertEquals("MAT_RECEIPT", cost.getSourceType());
            assertEquals(receiptId, cost.getSourceId());
            assertNotNull(cost.getSourceItemId());
            assertEquals("MATERIAL", cost.getCostType());
        }

        // 4. 第二次调用：幂等 — 不产生重复记录
        costGenerationService.generateCost("MAT_RECEIPT", receiptId);

        long countAfterSecond = costItemMapper.selectCount(
                new LambdaQueryWrapper<CostItem>()
                        .eq(CostItem::getSourceType, "MAT_RECEIPT")
                        .eq(CostItem::getSourceId, receiptId));
        assertEquals(2, countAfterSecond, "幂等：第二次调用不应产生重复记录（仍为2条）");

        // 5. 验证验收单cost_generated_flag已被更新
        MatReceipt receipt = receiptMapper.selectById(receiptId);
        assertEquals(1, receipt.getCostGeneratedFlag(), "策略内应更新costGeneratedFlag为1");

        System.out.println("✅ 场景4 通过: 首次生成" + firstBatch.size() + "条, 第二次后仍为" + countAfterSecond + "条");
    }

    // ═══════════════════════════════════════════════════════════
    // 场景5: 付款申请 CRUD（含依据关联）
    // ═══════════════════════════════════════════════════════════
    @Test
    @Order(5)
    @Transactional
    @DisplayName("场景5: 创建付款申请 → 保存依据（验收明细）→ 验证依据已保存")
    void test05_paymentApplicationCRUD() {
        // 1. 创建验收单+明细
        Long orderId = createOrderWithItems();
        Long receiptId = createReceiptWithItems(orderId);

        // 2. 获取验收明细ID
        List<MatReceiptItem> receiptItems = receiptItemMapper.selectList(
                new LambdaQueryWrapper<MatReceiptItem>()
                        .eq(MatReceiptItem::getReceiptId, receiptId));
        assertEquals(2, receiptItems.size());

        // 3. 创建付款申请
        PayApplication app = new PayApplication();
        app.setProjectId(PROJECT_ID);
        app.setContractId(CONTRACT_ID);
        app.setPartnerId(PARTNER_ID);
        app.setApplyAmount(new BigDecimal("50000.00"));
        app.setPayType("MATERIAL");
        app.setApplyReason("Phase2全链路集成测试-材料款付款");
        app.setRemark("测试付款申请");

        Long appId = payApplicationService.create(app);
        assertNotNull(appId, "付款申请ID不应为空");

        // 4. 验证付款申请已保存
        PayApplication saved = payApplicationMapper.selectById(appId);
        assertNotNull(saved, "付款申请应已保存");
        assertNotNull(saved.getApplyCode(), "申请编号应自动生成");
        assertTrue(saved.getApplyCode().startsWith("PAY-"), "申请编号应以PAY-开头");
        assertEquals("DRAFT", saved.getApprovalStatus(), "初始审批状态应为DRAFT");
        assertEquals("PENDING", saved.getPayStatus(), "初始付款状态应为PENDING");
        assertEquals(0, new BigDecimal("50000.00").compareTo(saved.getApplyAmount()), "申请金额应一致");

        // 5. 保存付款依据（关联验收明细）
        PayApplicationBasis basis1 = buildBasis("MAT_RECEIPT",
                receiptItems.get(0).getId(), new BigDecimal("20000.00"));
        PayApplicationBasis basis2 = buildBasis("MAT_RECEIPT",
                receiptItems.get(1).getId(), new BigDecimal("30000.00"));
        payApplicationService.saveBasis(appId, List.of(basis1, basis2));

        // 6. 验证依据已保存
        List<PayApplicationBasis> savedBasis = payApplicationBasisMapper.selectList(
                new LambdaQueryWrapper<PayApplicationBasis>()
                        .eq(PayApplicationBasis::getPayApplicationId, appId));
        assertEquals(2, savedBasis.size(), "应保存2条付款依据");
        for (PayApplicationBasis basis : savedBasis) {
            assertEquals("MAT_RECEIPT", basis.getBasisType(), "依据类型应为MAT_RECEIPT");
            assertNotNull(basis.getBasisId(), "依据ID不应为空");
            assertNotNull(basis.getBasisAmount(), "依据金额不应为空");
        }

        // 验证金额合计与申请金额一致
        BigDecimal basisTotal = savedBasis.stream()
                .map(b -> b.getBasisAmount() != null ? b.getBasisAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("50000.00").compareTo(basisTotal), "依据金额合计应与申请金额一致");

        System.out.println("✅ 场景5 通过: applyCode=" + saved.getApplyCode()
                + ", basisCount=" + savedBasis.size()
                + ", basisTotal=" + basisTotal);
    }

    // ═══════════════════════════════════════════════════════════
    // 场景6: 付款金额校验（边界/超出拒止）
    // ═══════════════════════════════════════════════════════════
    @Test
    @Order(6)
    @Transactional
    @DisplayName("场景6: 付款金额校验 → 依据金额超过验收明细金额时抛出BASIS_EXCEED_SOURCE")
    void test06_paymentValidation() {
        // 1. 创建验收单（明细金额36000和114000）
        Long orderId = createOrderWithItems();
        Long receiptId = createReceiptWithItems(orderId);

        List<MatReceiptItem> receiptItems = receiptItemMapper.selectList(
                new LambdaQueryWrapper<MatReceiptItem>()
                        .eq(MatReceiptItem::getReceiptId, receiptId));
        assertEquals(2, receiptItems.size());

        // 2. 创建付款申请（金额超出单个明细金额）
        PayApplication app = new PayApplication();
        app.setProjectId(PROJECT_ID);
        app.setContractId(CONTRACT_ID);
        app.setPartnerId(PARTNER_ID);
        app.setApplyAmount(new BigDecimal("50000.00"));
        app.setPayType("MATERIAL");
        app.setApplyReason("超金额测试");

        Long appId = payApplicationService.create(app);

        // 3. 保存依据：单个依据金额超过对应验收明细金额
        // 明细1金额36000, 但依据填了50000（等于申请金额，满足saveBasis校验但会失败于validateBasisAmount）
        PayApplicationBasis excessiveBasis = buildBasis("MAT_RECEIPT",
                receiptItems.get(0).getId(), new BigDecimal("50000.00"));
        payApplicationService.saveBasis(appId, List.of(excessiveBasis));

        // 4. 提交审批时应抛出BASIS_EXCEED_SOURCE（50000 > 36000）
        BusinessException ex = assertThrows(BusinessException.class, () -> {
            payApplicationService.submitForApproval(appId);
        }, "依据金额超过验收明细金额应抛出异常");
        assertEquals("BASIS_EXCEED_SOURCE", ex.getCode(), "错误码应为BASIS_EXCEED_SOURCE");

        System.out.println("✅ 场景6 通过: 超额依据被正确拒止, code=" + ex.getCode()
                + ", message=" + ex.getMessage());

        // ── 场景6b: 零金额拒止 ──
        PayApplication zeroApp = new PayApplication();
        zeroApp.setProjectId(PROJECT_ID);
        zeroApp.setContractId(CONTRACT_ID);
        zeroApp.setPartnerId(PARTNER_ID);
        zeroApp.setApplyAmount(BigDecimal.ZERO);
        zeroApp.setPayType("MATERIAL");
        zeroApp.setApplyReason("零金额测试");

        Long zeroAppId = payApplicationService.create(zeroApp);
        BusinessException zeroEx = assertThrows(BusinessException.class, () -> {
            payApplicationService.submitForApproval(zeroAppId);
        }, "零金额申请应抛出异常");
        assertEquals("INVALID_AMOUNT", zeroEx.getCode(), "错误码应为INVALID_AMOUNT");
        System.out.println("  场景6b 通过: 零金额被拒止, code=" + zeroEx.getCode());
    }

    // ═══════════════════════════════════════════════════════════
    // 场景6c: 付款边界——累计付款精确等于合同金额
    // ═══════════════════════════════════════════════════════════
    @Test
    @Order(18)
    @Transactional
    @DisplayName("场景6c: 付款金额边界——累计付款精确等于合同金额")
    void test06c_balanceBoundaryExactMatch() {
        // Set contract amount to 100000
        CtContract contract = contractMapper.selectById(CONTRACT_ID);
        contract.setContractAmount(new BigDecimal("100000"));
        contract.setCurrentAmount(new BigDecimal("100000"));
        contractMapper.updateById(contract);

        // Create and approve first payment of 50000
        PayApplication app1 = new PayApplication();
        app1.setProjectId(PROJECT_ID);
        app1.setContractId(CONTRACT_ID);
        app1.setPartnerId(PARTNER_ID);
        app1.setApplyAmount(new BigDecimal("50000"));
        app1.setPayType("MATERIAL");
        app1.setApplyReason("boundary test 1");
        payApplicationService.create(app1);
        app1.setApprovalStatus("APPROVED");
        payApplicationMapper.updateById(app1);

        // Second payment of 50000 should pass (total = 100000 = contract amount)
        PayApplication app2 = new PayApplication();
        app2.setProjectId(PROJECT_ID);
        app2.setContractId(CONTRACT_ID);
        app2.setPartnerId(PARTNER_ID);
        app2.setApplyAmount(new BigDecimal("50000"));
        app2.setPayType("MATERIAL");
        app2.setApplyReason("boundary test 2");

        assertDoesNotThrow(() -> {
            payApplicationService.create(app2);
            payApplicationService.validatePaymentAmount(app2);
        });

        System.out.println("✅ 场景6c 通过: 精确等于合同余额通过校验");
    }

    // ═══════════════════════════════════════════════════════════
    // 场景6d: 付款边界——超出合同余额1分钱
    // ═══════════════════════════════════════════════════════════
    @Test
    @Order(19)
    @Transactional
    @DisplayName("场景6d: 付款金额边界——超出合同余额1分钱应拒止")
    void test06d_balanceBoundaryExceedByOneCent() {
        // Contract amount = 100000, first payment = 50000 (approved)
        // Second payment = 50000.01 → should be REJECTED (exceeds by 1 cent)
        CtContract contract = contractMapper.selectById(CONTRACT_ID);
        contract.setContractAmount(new BigDecimal("100000"));
        contract.setCurrentAmount(new BigDecimal("100000"));
        contractMapper.updateById(contract);

        PayApplication app1 = new PayApplication();
        app1.setProjectId(PROJECT_ID);
        app1.setContractId(CONTRACT_ID);
        app1.setPartnerId(PARTNER_ID);
        app1.setApplyAmount(new BigDecimal("50000"));
        app1.setPayType("MATERIAL");
        app1.setApplyReason("boundary test");
        payApplicationService.create(app1);
        app1.setApprovalStatus("APPROVED");
        payApplicationMapper.updateById(app1);

        PayApplication app2 = new PayApplication();
        app2.setProjectId(PROJECT_ID);
        app2.setContractId(CONTRACT_ID);
        app2.setPartnerId(PARTNER_ID);
        app2.setApplyAmount(new BigDecimal("50000.01"));
        app2.setPayType("MATERIAL");
        app2.setApplyReason("exceed by 1 cent");

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            payApplicationService.create(app2);
            payApplicationService.validatePaymentAmount(app2);
        });

        assertEquals("EXCEED_CONTRACT_BALANCE", ex.getCode(),
                "超出合同余额1分钱应抛出EXCEED_CONTRACT_BALANCE");

        System.out.println("✅ 场景6d 通过: 超出合同余额1分钱被拒止, code=" + ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════
    // 场景7: 付款回写与联动
    // ═══════════════════════════════════════════════════════════
    @Test
    @Order(7)
    @Transactional
    @DisplayName("场景7: 付款回写 → pay_record创建，application.payStatus更新，contract.paid_amount更新")
    void test07_writebackAndLinkage() {
        // 1. 创建采购订单+明细 → 验收单+明细
        Long orderId = createOrderWithItems();
        Long receiptId = createReceiptWithItems(orderId);

        List<MatReceiptItem> receiptItems = receiptItemMapper.selectList(
                new LambdaQueryWrapper<MatReceiptItem>()
                        .eq(MatReceiptItem::getReceiptId, receiptId));
        assertEquals(2, receiptItems.size());

        // 2. 创建并保存付款申请
        PayApplication app = new PayApplication();
        app.setProjectId(PROJECT_ID);
        app.setContractId(CONTRACT_ID);
        app.setPartnerId(PARTNER_ID);
        app.setApplyAmount(new BigDecimal("150000.00"));
        app.setPayType("MATERIAL");
        app.setApplyReason("Phase2全链路集成测试-回写测试");

        Long appId = payApplicationService.create(app);

        PayApplicationBasis b1 = buildBasis("MAT_RECEIPT",
                receiptItems.get(0).getId(), new BigDecimal("36000.00"));
        PayApplicationBasis b2 = buildBasis("MAT_RECEIPT",
                receiptItems.get(1).getId(), new BigDecimal("114000.00"));
        payApplicationService.saveBasis(appId, List.of(b1, b2));

        app.setId(appId);
        app.setApprovalStatus("APPROVED");
        payApplicationMapper.updateById(app);

        // 记录合同原有已付金额
        CtContract contractBefore = contractMapper.selectById(CONTRACT_ID);
        BigDecimal paidBefore = contractBefore.getPaidAmount() != null ? contractBefore.getPaidAmount() : BigDecimal.ZERO;

        // 3. 执行第一笔回写（部分付款：150000中的80000）
        PayRecord input1 = new PayRecord();
        input1.setPayApplicationId(appId);
        input1.setPayAmount(new BigDecimal("80000.00"));
        input1.setPayDate(LocalDate.now());
        input1.setPayMethod("BANK_TRANSFER");
        input1.setVoucherNo("VCH-" + System.currentTimeMillis() + "-01");
        input1.setExternalTxnNo("EXT-TXN-" + System.currentTimeMillis() + "-01");

        payRecordService.writeback(input1);

        // 4. 验证 pay_record 已创建
        List<PayRecord> records = payRecordMapper.selectList(
                new LambdaQueryWrapper<PayRecord>()
                        .eq(PayRecord::getPayApplicationId, appId));
        assertEquals(1, records.size(), "应有1条付款记录");
        PayRecord record = records.get(0);
        assertEquals(0, new BigDecimal("80000.00").compareTo(record.getPayAmount()), "付款金额应一致");
        assertEquals("SUCCESS", record.getPayStatus(), "付款状态应为SUCCESS");
        assertEquals("BANK_TRANSFER", record.getPayMethod());
        assertEquals(CONTRACT_ID, record.getContractId());

        // 5. 验证 application.payStatus 更新为 PARTIALLY_PAID（80000 < 150000）
        PayApplication afterWriteback = payApplicationMapper.selectById(appId);
        assertEquals("PARTIALLY_PAID", afterWriteback.getPayStatus(),
                "部分付款后状态应为PARTIALLY_PAID");
        assertEquals(0, new BigDecimal("80000.00").compareTo(afterWriteback.getActualPayAmount()),
                "实际支付金额应为已付款总和");

        // 6. 验证 contract.paid_amount 已更新
        CtContract contractAfter = contractMapper.selectById(CONTRACT_ID);
        BigDecimal expectedPaid = paidBefore.add(new BigDecimal("80000.00"));
        assertEquals(0, expectedPaid.compareTo(contractAfter.getPaidAmount()),
                "合同已付金额应增加80000");

        // 7. 执行第二笔回写（付清剩余）
        PayRecord input2 = new PayRecord();
        input2.setPayApplicationId(appId);
        input2.setPayAmount(new BigDecimal("70000.00"));
        input2.setPayDate(LocalDate.now());
        input2.setPayMethod("BANK_TRANSFER");
        input2.setVoucherNo("VCH-" + System.currentTimeMillis() + "-02");
        input2.setExternalTxnNo("EXT-TXN-" + System.currentTimeMillis() + "-02");

        payRecordService.writeback(input2);

        // 8. 验证状态变为 PAID
        PayApplication afterFullPay = payApplicationMapper.selectById(appId);
        assertEquals("PAID", afterFullPay.getPayStatus(), "付清后状态应变为PAID");
        assertEquals(0, new BigDecimal("150000.00").compareTo(afterFullPay.getActualPayAmount()),
                "实际支付金额应为全部申请金额");

        // 9. 验证 records 数量
        List<PayRecord> allRecords = payRecordMapper.selectList(
                new LambdaQueryWrapper<PayRecord>()
                        .eq(PayRecord::getPayApplicationId, appId));
        assertEquals(2, allRecords.size(), "应有2条付款记录");

        // 10. 验证 contract.paid_amount 继续累加
        CtContract contractFinal = contractMapper.selectById(CONTRACT_ID);
        BigDecimal expectedFinal = paidBefore.add(new BigDecimal("150000.00"));
        assertEquals(0, expectedFinal.compareTo(contractFinal.getPaidAmount()),
                "合同已付金额应再增加70000");

        // 11. 验证超付拒止
        PayRecord overpay = new PayRecord();
        overpay.setPayApplicationId(appId);
        overpay.setPayAmount(new BigDecimal("1.00"));
        overpay.setPayDate(LocalDate.now());
        overpay.setPayMethod("BANK_TRANSFER");
        overpay.setVoucherNo("VCH-OVERPAY");
        overpay.setExternalTxnNo("EXT-TXN-OVERPAY");

        BusinessException overEx = assertThrows(BusinessException.class, () -> {
            payRecordService.writeback(overpay);
        }, "超付应抛出异常");
        assertEquals("PAY_OVERPAYMENT", overEx.getCode(), "错误码应为PAY_OVERPAYMENT");

        System.out.println("✅ 场景7 通过: 回写2笔(80000+70000), "
                + "payStatus=" + afterFullPay.getPayStatus()
                + ", records=" + allRecords.size()
                + ", contractPaid=" + contractFinal.getPaidAmount()
                + ", overpayRejected=" + overEx.getCode());
    }

    // ═══════════════════════════════════════════════════════════
    // 辅助方法
    // ═══════════════════════════════════════════════════════════

    /** 创建采购订单并保存2条明细，返回订单ID */
    private Long createOrderWithItems() {
        MatPurchaseOrder order = new MatPurchaseOrder();
        order.setProjectId(PROJECT_ID);
        order.setContractId(CONTRACT_ID);
        order.setPartnerId(PARTNER_ID);
        order.setOrderType("NORMAL");
        order.setOrderDate(LocalDate.now());
        order.setDeliveryDate(LocalDate.now().plusDays(30));
        order.setRemark("Phase2辅助数据-采购订单");

        Long orderId = purchaseOrderService.create(order);

        MatPurchaseOrderItem item1 = buildOrderItem("C30商品砼", "C30", "m³", 100, 450);
        MatPurchaseOrderItem item2 = buildOrderItem("HRB400螺纹钢", "HRB400 φ25", "t", 50, 3800);
        purchaseOrderService.saveItemsBatch(orderId, List.of(item1, item2));

        return orderId;
    }

    /** 创建验收单并保存2条明细（关联采购订单），返回验收单ID */
    private Long createReceiptWithItems(Long orderId) {
        MatReceipt receipt = new MatReceipt();
        receipt.setProjectId(PROJECT_ID);
        receipt.setOrderId(orderId);
        receipt.setReceiptDate(LocalDate.now());
        receipt.setQualityStatus("PASSED");
        receipt.setRemark("Phase2辅助数据-验收单");

        Long receiptId = receiptService.create(receipt);

        List<MatPurchaseOrderItem> orderItems = purchaseOrderItemMapper.selectList(
                new LambdaQueryWrapper<MatPurchaseOrderItem>()
                        .eq(MatPurchaseOrderItem::getOrderId, orderId));

        MatReceiptItem ri1 = buildReceiptItem(orderItems.get(0).getId(),
                new BigDecimal("80"), new BigDecimal("80"), new BigDecimal("450"), new BigDecimal("36000.00"));
        MatReceiptItem ri2 = buildReceiptItem(orderItems.get(1).getId(),
                new BigDecimal("30"), new BigDecimal("30"), new BigDecimal("3800"), new BigDecimal("114000.00"));
        receiptService.saveItemsBatch(receiptId, List.of(ri1, ri2));

        return receiptId;
    }

    /** 构建采购订单明细 */
    private MatPurchaseOrderItem buildOrderItem(String name, String spec, String unit,
                                                  long quantity, long unitPrice) {
        MatPurchaseOrderItem item = new MatPurchaseOrderItem();
        item.setMaterialName(name);
        item.setSpecification(spec);
        item.setUnit(unit);
        item.setQuantity(new BigDecimal(quantity));
        item.setUnitPrice(new BigDecimal(unitPrice));
        item.setAmount(new BigDecimal(quantity * unitPrice));
        return item;
    }

    /** 构建验收单明细 */
    private MatReceiptItem buildReceiptItem(Long orderItemId,
                                             BigDecimal actualQty, BigDecimal qualifiedQty,
                                             BigDecimal unitPrice, BigDecimal amount) {
        MatReceiptItem item = new MatReceiptItem();
        item.setOrderItemId(orderItemId);
        item.setActualQuantity(actualQty);
        item.setQualifiedQuantity(qualifiedQty);
        item.setUnitPrice(unitPrice);
        item.setAmount(amount);
        return item;
    }

    /** 构建付款依据 */
    private PayApplicationBasis buildBasis(String basisType, Long basisId, BigDecimal basisAmount) {
        PayApplicationBasis basis = new PayApplicationBasis();
        basis.setBasisType(basisType);
        basis.setBasisId(basisId);
        basis.setBasisAmount(basisAmount);
        return basis;
    }

    /** 逐节点审批通过所有待办任务 */
    private void approveAllPendingTasks(Long instanceId) {
        // POC模式：所有审批节点userId=1，需要逐节点审批
        for (int i = 0; i < 10; i++) { // 最多10轮防无限循环
            List<WfTask> pendingTasks = wfTaskMapper.selectList(
                    new LambdaQueryWrapper<WfTask>()
                            .eq(WfTask::getInstanceId, instanceId)
                            .eq(WfTask::getTaskStatus, "PENDING"));
            if (pendingTasks.isEmpty()) {
                // 检查实例是否已完成
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
                        "集成测试审批通过", "phase2-" + UUID.randomUUID() + "-" + task.getId());
            }
        }
    }

    /** V85 会删掉默认 admin；材料验收审批模板仍使用 userId=1。 */
    private void seedAdminUser() {
        if (sysUserMapper.selectById(USER_ADMIN) == null) {
            SysUser admin = new SysUser();
            admin.setId(USER_ADMIN);
            admin.setTenantId(0L);
            admin.setUsername("admin");
            admin.setPassword("seeded");
            admin.setRealName("系统管理员");
            admin.setStatus("ENABLE");
            admin.setIsAdmin(1);
            sysUserMapper.insert(admin);
        }
    }
}
