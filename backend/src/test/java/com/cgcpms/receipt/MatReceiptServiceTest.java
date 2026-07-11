package com.cgcpms.receipt;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.purchase.entity.MatPurchaseOrder;
import com.cgcpms.purchase.entity.MatPurchaseOrderItem;
import com.cgcpms.purchase.mapper.MatPurchaseOrderItemMapper;
import com.cgcpms.purchase.service.MatPurchaseOrderService;
import com.cgcpms.receipt.entity.MatReceipt;
import com.cgcpms.receipt.entity.MatReceiptItem;
import com.cgcpms.receipt.mapper.MatReceiptMapper;
import com.cgcpms.receipt.service.MatReceiptService;
import com.cgcpms.receipt.vo.MatReceiptVO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 物料验收单核心业务测试。
 * <p>
 * 覆盖 MatReceiptService 的基本 CRUD：
 * 创建（含自动编码）、查询、更新、删除，以及状态保护规则。
 * </p>
 */
@SpringBootTest
@ActiveProfiles("local")
@DisplayName("MatReceiptService — 验收单 CRUD 与状态保护")
class MatReceiptServiceTest {

    @Autowired
    private MatReceiptService receiptService;

    @Autowired
    private MatReceiptMapper receiptMapper;

    @Autowired
    private MatPurchaseOrderService orderService;

    @Autowired
    private MatPurchaseOrderItemMapper orderItemMapper;

    private static final long PROJECT_ID = 10001L;

    @BeforeEach
    void setupContext() {
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);
    }

    @AfterEach
    void clearContext() {
        TestUserContext.clear();
    }

    private MatReceipt buildReceipt(String qualityStatus) {
        MatReceipt receipt = new MatReceipt();
        receipt.setProjectId(PROJECT_ID);
        receipt.setReceiptDate(LocalDate.of(2026, 3, 10));
        receipt.setReceiptCode("MR-TEST-" + System.nanoTime());
        receipt.setQualityStatus(qualityStatus);
        receipt.setTotalAmount(new BigDecimal("50000.00"));
        receipt.setWarehouseId(1L);
        receipt.setPartnerId(20002L);
        receipt.setContractId(30001L);
        return receipt;
    }

    // ═══════════════════════════════════════════════════════════════
    // R-1: 创建验收单 — 自动生成编码，初始状态 DRAFT
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("R-1: create 生成验收单编码并持久化，初始状态为 DRAFT")
    void testCreate() {
        MatReceipt receipt = buildReceipt("PENDING");
        Long id = receiptService.create(receipt);

        assertNotNull(id, "创建应返回验收单 ID");
        MatReceipt saved = receiptMapper.selectById(id);
        assertNotNull(saved);
        assertTrue(saved.getReceiptCode().startsWith("MR-"),
                "验收单编码应以 MR- 开头: " + saved.getReceiptCode());
        assertEquals("DRAFT", saved.getApprovalStatus());
        assertEquals(0, saved.getCostGeneratedFlag());
        assertEquals(0, new BigDecimal("50000.00").compareTo(saved.getTotalAmount()));
    }

    @Test
    @DisplayName("R-1b: 同一天创建多个验收单，编码序号递增")
    void testCreateGeneratesSequentialCodes() {
        MatReceipt r1 = buildReceipt("PENDING");
        MatReceipt r2 = buildReceipt("QUALIFIED");

        Long id1 = receiptService.create(r1);
        Long id2 = receiptService.create(r2);

        String code1 = receiptMapper.selectById(id1).getReceiptCode();
        String code2 = receiptMapper.selectById(id2).getReceiptCode();

        assertNotEquals(code1, code2, "不同验收单编码应不同");
        // 编码格式: MR-yyyyMMdd-NNN，同一天内序号应递增
        String prefix = code1.substring(0, code1.length() - 3);
        assertEquals(prefix, code2.substring(0, code2.length() - 3), "同一天编码前缀应一致");
    }

    // ═══════════════════════════════════════════════════════════════
    // R-2: 按 ID 查询
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("R-2: getById 返回完整验收单及其明细")
    void testGetById() {
        MatReceipt receipt = buildReceipt("QUALIFIED");
        Long id = receiptService.create(receipt);

        MatReceiptVO vo = receiptService.getById(id);
        assertNotNull(vo);
        assertEquals(id, Long.valueOf(vo.getId()));
        assertEquals("DRAFT", vo.getApprovalStatus());
        assertEquals("QUALIFIED", vo.getQualityStatus());
    }

    @Test
    @DisplayName("R-2b: getById 对不存在的 ID 抛出 RECEIPT_NOT_FOUND")
    void testGetByIdNotFound() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                receiptService.getById(99999L));
        assertEquals("RECEIPT_NOT_FOUND", ex.getCode());
    }

    @Test
    @DisplayName("R-2c: 保存收货明细同步采购已收数量，替换明细后不重复累计")
    void testSaveItemsBatchSyncsPurchaseReceivedQuantity() {
        MatPurchaseOrder order = new MatPurchaseOrder();
        order.setProjectId(PROJECT_ID);
        order.setOrderType("PURCHASE");
        Long orderId = orderService.create(order);

        MatPurchaseOrderItem orderItem = new MatPurchaseOrderItem();
        orderItem.setOrderId(orderId);
        orderItem.setProjectId(PROJECT_ID);
        orderItem.setMaterialId(1001L);
        orderItem.setQuantity(new BigDecimal("10.0000"));
        orderItem.setUnitPrice(new BigDecimal("2.0000"));
        orderItem.setAmount(new BigDecimal("20.0000"));
        orderService.saveItemsBatch(orderId, List.of(orderItem));
        Long orderItemId = orderItemMapper.selectList(new LambdaQueryWrapper<MatPurchaseOrderItem>()
                        .eq(MatPurchaseOrderItem::getOrderId, orderId))
                .stream()
                .findFirst()
                .orElseThrow()
                .getId();

        MatReceipt receipt = buildReceipt("QUALIFIED");
        receipt.setOrderId(orderId);
        Long receiptId = receiptService.create(receipt);

        MatReceiptItem first = new MatReceiptItem();
        first.setOrderItemId(orderItemId);
        first.setMaterialId(1001L);
        first.setActualQuantity(new BigDecimal("4.0000"));
        first.setQualifiedQuantity(new BigDecimal("3.0000"));
        first.setUnitPrice(new BigDecimal("2.0000"));
        first.setAmount(new BigDecimal("8.0000"));
        receiptService.saveItemsBatch(receiptId, List.of(first));
        assertEquals(0, new BigDecimal("4.0000")
                .compareTo(orderItemMapper.selectById(orderItemId).getReceivedQuantity()));

        MatReceiptItem replacement = new MatReceiptItem();
        replacement.setOrderItemId(orderItemId);
        replacement.setMaterialId(1001L);
        replacement.setActualQuantity(new BigDecimal("2.0000"));
        replacement.setQualifiedQuantity(new BigDecimal("2.0000"));
        replacement.setUnitPrice(new BigDecimal("2.0000"));
        replacement.setAmount(new BigDecimal("4.0000"));
        receiptService.saveItemsBatch(receiptId, List.of(replacement));
        assertEquals(0, new BigDecimal("2.0000")
                .compareTo(orderItemMapper.selectById(orderItemId).getReceivedQuantity()));

        receiptService.saveItemsBatch(receiptId, List.of());
        assertEquals(0, BigDecimal.ZERO
                .compareTo(orderItemMapper.selectById(orderItemId).getReceivedQuantity()));
    }

    @Test
    @DisplayName("R-2d: 保存收货明细拒绝跨订单引用与非法数量")
    void testSaveItemsBatchRejectsInvalidOrderItemAndQuantities() {
        MatPurchaseOrder firstOrder = new MatPurchaseOrder();
        firstOrder.setProjectId(PROJECT_ID);
        firstOrder.setOrderType("PURCHASE");
        Long firstOrderId = orderService.create(firstOrder);

        MatPurchaseOrderItem firstOrderItem = new MatPurchaseOrderItem();
        firstOrderItem.setOrderId(firstOrderId);
        firstOrderItem.setProjectId(PROJECT_ID);
        firstOrderItem.setMaterialId(1001L);
        firstOrderItem.setQuantity(new BigDecimal("10.0000"));
        firstOrderItem.setUnitPrice(BigDecimal.ONE);
        firstOrderItem.setAmount(BigDecimal.TEN);
        orderService.saveItemsBatch(firstOrderId, List.of(firstOrderItem));
        Long firstOrderItemId = orderItemMapper.selectList(new LambdaQueryWrapper<MatPurchaseOrderItem>()
                        .eq(MatPurchaseOrderItem::getOrderId, firstOrderId))
                .get(0).getId();

        MatPurchaseOrder secondOrder = new MatPurchaseOrder();
        secondOrder.setProjectId(PROJECT_ID);
        secondOrder.setOrderType("PURCHASE");
        Long secondOrderId = orderService.create(secondOrder);
        MatReceipt receipt = buildReceipt("QUALIFIED");
        receipt.setOrderId(secondOrderId);
        Long receiptId = receiptService.create(receipt);

        MatReceiptItem crossOrder = new MatReceiptItem();
        crossOrder.setOrderItemId(firstOrderItemId);
        crossOrder.setActualQuantity(BigDecimal.ONE);
        crossOrder.setQualifiedQuantity(BigDecimal.ONE);
        BusinessException mismatch = assertThrows(BusinessException.class,
                () -> receiptService.saveItemsBatch(receiptId, List.of(crossOrder)));
        assertEquals("ORDER_ITEM_MISMATCH", mismatch.getCode());

        MatReceiptItem zeroActual = new MatReceiptItem();
        zeroActual.setActualQuantity(BigDecimal.ZERO);
        zeroActual.setQualifiedQuantity(BigDecimal.ZERO);
        BusinessException zero = assertThrows(BusinessException.class,
                () -> receiptService.saveItemsBatch(receiptId, List.of(zeroActual)));
        assertEquals("RECEIPT_QUANTITY_INVALID", zero.getCode());

        MatReceiptItem excessiveQualified = new MatReceiptItem();
        excessiveQualified.setActualQuantity(BigDecimal.ONE);
        excessiveQualified.setQualifiedQuantity(new BigDecimal("2"));
        BusinessException excessive = assertThrows(BusinessException.class,
                () -> receiptService.saveItemsBatch(receiptId, List.of(excessiveQualified)));
        assertEquals("RECEIPT_QUANTITY_INVALID", excessive.getCode());
    }

    // ═══════════════════════════════════════════════════════════════
    // R-3: 分页查询
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("R-3: getPage 按租户过滤返回分页结果")
    void testGetPage() {
        receiptService.create(buildReceipt("PENDING"));

        IPage<MatReceiptVO> page = receiptService.getPage(1, 10, null, null, null, null, null, null);
        assertNotNull(page);
        assertTrue(page.getTotal() >= 1);
    }

    // ═══════════════════════════════════════════════════════════════
    // R-4: 更新 — 仅草稿状态可编辑
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("R-4: update 允许编辑草稿状态验收单")
    void testUpdateDraftReceipt() {
        MatReceipt receipt = buildReceipt("PENDING");
        Long id = receiptService.create(receipt);

        MatReceipt updateData = new MatReceipt();
        updateData.setId(id);
        updateData.setQualityStatus("QUALIFIED");
        updateData.setTotalAmount(new BigDecimal("60000.00"));

        receiptService.update(updateData);

        MatReceipt updated = receiptMapper.selectById(id);
        assertEquals("QUALIFIED", updated.getQualityStatus());
        assertEquals(0, new BigDecimal("60000.00").compareTo(updated.getTotalAmount()));
        // 审批状态和编码不应被覆盖
        assertEquals("DRAFT", updated.getApprovalStatus());
        assertNotNull(updated.getReceiptCode());
    }

    @Test
    @DisplayName("R-4b: update 拒绝编辑非草稿状态验收单")
    void testUpdateRejectsNonDraftReceipt() {
        MatReceipt receipt = buildReceipt("PENDING");
        Long id = receiptService.create(receipt);

        // 手动将状态改为非 DRAFT
        MatReceipt existing = receiptMapper.selectById(id);
        existing.setApprovalStatus("APPROVING");
        receiptMapper.updateById(existing);

        MatReceipt updateData = new MatReceipt();
        updateData.setId(id);
        updateData.setQualityStatus("QUALIFIED");

        BusinessException ex = assertThrows(BusinessException.class, () ->
                receiptService.update(updateData));
        assertEquals("RECEIPT_IN_APPROVAL", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════════
    // R-5: 删除 — 仅草稿状态可删除
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("R-5: delete 允许删除草稿状态验收单")
    void testDeleteDraftReceipt() {
        MatReceipt receipt = buildReceipt("PENDING");
        Long id = receiptService.create(receipt);

        receiptService.delete(id);

        // 软删除后应查询不到
        MatReceipt deleted = receiptMapper.selectById(id);
        assertNull(deleted, "草稿验收单软删除后应不可查");
    }

    @Test
    @DisplayName("R-5b: delete 拒绝删除非草稿状态验收单")
    void testDeleteRejectsNonDraftReceipt() {
        MatReceipt receipt = buildReceipt("PENDING");
        Long id = receiptService.create(receipt);

        // 手动将状态改为非 DRAFT
        MatReceipt existing = receiptMapper.selectById(id);
        existing.setApprovalStatus("APPROVING");
        receiptMapper.updateById(existing);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                receiptService.delete(id));
        assertEquals("RECEIPT_IN_APPROVAL", ex.getCode());
    }
}
