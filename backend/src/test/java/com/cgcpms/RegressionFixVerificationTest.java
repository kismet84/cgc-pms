package com.cgcpms;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.entity.CostItem;
import com.cgcpms.cost.mapper.CostItemMapper;
import com.cgcpms.cost.service.CostSummaryService;
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
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RegressionFixVerificationTest {

    private static final long USER_ADMIN = 1L;

    private static final long PROJECT_ID = 10001L;
    private static final long CONTRACT_ID = 30001L;
    private static final long PARTNER_ID = 20001L;

    @Autowired private MatPurchaseOrderService purchaseOrderService;
    @Autowired private MatReceiptService receiptService;

    @Autowired private MatPurchaseOrderMapper purchaseOrderMapper;
    @Autowired private MatPurchaseOrderItemMapper purchaseOrderItemMapper;
    @Autowired private MatReceiptMapper receiptMapper;
    @Autowired private MatReceiptItemMapper receiptItemMapper;
    @Autowired private CostSummaryService costSummaryService;
    @Autowired private CostItemMapper costItemMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

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
    // F10: MatReceipt quantity no double-count
    // ═══════════════════════════════════════════════════════════
    @Test
    @Order(1)
    @Transactional
    @DisplayName("F10: saveItemsBatch不重复累加receivedQuantity")
    void testF10_receiptQuantityNoDoubleCount() {
        Long orderId = null;
        Long receiptId = null;
        Long orderItemId = null;

        try {
            // 1. Create purchase order with 1 item (ordered 100 units)
            MatPurchaseOrder order = new MatPurchaseOrder();
            order.setProjectId(PROJECT_ID);
            order.setContractId(CONTRACT_ID);
            order.setPartnerId(PARTNER_ID);
            order.setOrderType("NORMAL");
            order.setOrderDate(LocalDate.now());
            order.setDeliveryDate(LocalDate.now().plusDays(30));
            order.setRemark("Regression-F10-采购订单");

            orderId = purchaseOrderService.create(order);
            assertNotNull(orderId);

            MatPurchaseOrderItem item = buildOrderItem("C30商品砼", "C30", "m³", 100, 450);
            purchaseOrderService.saveItemsBatch(orderId, List.of(item));

            List<MatPurchaseOrderItem> orderItems = purchaseOrderItemMapper.selectList(
                    new LambdaQueryWrapper<MatPurchaseOrderItem>()
                            .eq(MatPurchaseOrderItem::getOrderId, orderId));
            assertEquals(1, orderItems.size());
            orderItemId = orderItems.get(0).getId();

            // 2. Create receipt referencing that order
            MatReceipt receipt = new MatReceipt();
            receipt.setProjectId(PROJECT_ID);
            receipt.setOrderId(orderId);
            receipt.setReceiptDate(LocalDate.now());
            receipt.setQualityStatus("PASSED");
            receipt.setRemark("Regression-F10-验收单");

            receiptId = receiptService.create(receipt);
            assertNotNull(receiptId);

            // 3. First saveItemsBatch with actualQuantity=10
            MatReceiptItem ri1 = buildReceiptItem(orderItemId, 10, 10, 450, 10 * 450);
            receiptService.saveItemsBatch(receiptId, List.of(ri1));

            MatPurchaseOrderItem oi = purchaseOrderItemMapper.selectById(orderItemId);
            assertEquals(0, new BigDecimal("10").compareTo(oi.getReceivedQuantity()),
                    "第一次保存后receivedQuantity应为10");

            // 4. Second saveItemsBatch with same quantity → should NOT double-count
            MatReceiptItem ri2 = buildReceiptItem(orderItemId, 10, 10, 450, 10 * 450);
            receiptService.saveItemsBatch(receiptId, List.of(ri2));

            oi = purchaseOrderItemMapper.selectById(orderItemId);
            assertEquals(0, new BigDecimal("10").compareTo(oi.getReceivedQuantity()),
                    "第二次保存后receivedQuantity仍应为10，不应累加为20");

            System.out.println("F10 PASS: receivedQuantity=" + oi.getReceivedQuantity()
                    + " (expected 10, NOT 20)");

        } finally {
            // Cleanup
            if (receiptId != null) {
                LambdaQueryWrapper<MatReceiptItem> riWrapper = new LambdaQueryWrapper<>();
                riWrapper.eq(MatReceiptItem::getReceiptId, receiptId);
                receiptItemMapper.delete(riWrapper);

                MatReceipt r = receiptMapper.selectById(receiptId);
                if (r != null) {
                    r.setApprovalStatus("DRAFT");
                    r.setCostGeneratedFlag(0);
                    receiptMapper.updateById(r);
                    receiptMapper.deleteById(receiptId);
                }
            }
            if (orderId != null) {
                LambdaQueryWrapper<MatPurchaseOrderItem> oiWrapper = new LambdaQueryWrapper<>();
                oiWrapper.eq(MatPurchaseOrderItem::getOrderId, orderId);
                purchaseOrderItemMapper.delete(oiWrapper);

                MatPurchaseOrder o = purchaseOrderMapper.selectById(orderId);
                if (o != null) {
                    o.setApprovalStatus("DRAFT");
                    purchaseOrderMapper.updateById(o);
                    purchaseOrderMapper.deleteById(orderId);
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // F17: Edit/delete guards block approved documents
    // ═══════════════════════════════════════════════════════════
    @Test
    @Order(2)
    @Transactional
    @DisplayName("F17: 已审批/已生成成本单据的编辑/删除守卫")
    void testF17_approvedDocumentGuards() {
        // ── Purchase order guards ──
        Long orderId = null;
        Long receiptId = null;
        Long helperOrderId = null;

        try {
            // Create purchase order with guaranteed-unique code via raw JDBC
            orderId = System.currentTimeMillis();
            String uniqueCode = "PO-REG-" + System.nanoTime();
            jdbcTemplate.update("INSERT INTO mat_purchase_order (id, tenant_id, project_id, contract_id, partner_id, order_code, order_type, order_date, delivery_date, approval_status, order_status, created_by, created_at, updated_by, updated_at, remark, deleted_flag) VALUES (?, 0, ?, ?, ?, ?, 'NORMAL', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 30 DAY), 'DRAFT', 'DRAFT', 1, NOW(), 1, NOW(), ?, 0)",
                    orderId, PROJECT_ID, CONTRACT_ID, PARTNER_ID, uniqueCode, "Regression-F17-采购订单");
            assertNotNull(orderId);

            // Set approvalStatus to APPROVED via mapper
            MatPurchaseOrder existing = purchaseOrderMapper.selectById(orderId);
            existing.setApprovalStatus("APPROVED");
            purchaseOrderMapper.updateById(existing);

            // update() should throw
            MatPurchaseOrder updateOrder = new MatPurchaseOrder();
            updateOrder.setId(orderId);
            updateOrder.setDeliveryDate(LocalDate.now().plusDays(60));
            BusinessException updateEx = assertThrows(BusinessException.class,
                    () -> purchaseOrderService.update(updateOrder),
                    "已审批订单不可编辑");
            assertEquals("ORDER_IN_APPROVAL", updateEx.getCode());

            // delete() should throw
            Long orderIdSnapshot = orderId;
            BusinessException deleteEx = assertThrows(BusinessException.class,
                    () -> purchaseOrderService.delete(orderIdSnapshot),
                    "已审批订单不可删除");
            assertEquals("ORDER_IN_APPROVAL", deleteEx.getCode());

            System.out.println("F17 采购订单守卫 PASS: update=" + updateEx.getCode()
                    + ", delete=" + deleteEx.getCode());

            // Reset and delete order
            existing.setApprovalStatus("DRAFT");
            purchaseOrderMapper.updateById(existing);
            purchaseOrderMapper.deleteById(orderId);
            orderId = null;

            // ── Receipt guards ──
            // Create helper order with guaranteed-unique code via raw JDBC
            helperOrderId = System.currentTimeMillis() + 1;
            String helperCode = "PO-REG-" + (System.nanoTime() + 1);
            jdbcTemplate.update("INSERT INTO mat_purchase_order (id, tenant_id, project_id, contract_id, partner_id, order_code, order_type, order_date, delivery_date, approval_status, order_status, created_by, created_at, updated_by, updated_at, remark, deleted_flag) VALUES (?, 0, ?, ?, ?, ?, 'NORMAL', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 30 DAY), 'DRAFT', 'DRAFT', 1, NOW(), 1, NOW(), ?, 0)",
                    helperOrderId, PROJECT_ID, CONTRACT_ID, PARTNER_ID, helperCode, "Regression-F17-辅助订单");

            // Create receipt
            MatReceipt receipt = new MatReceipt();
            receipt.setProjectId(PROJECT_ID);
            receipt.setOrderId(helperOrderId);
            receipt.setReceiptDate(LocalDate.now());
            receipt.setQualityStatus("PASSED");
            receipt.setRemark("Regression-F17-验收单");

            receiptId = receiptService.create(receipt);
            assertNotNull(receiptId);

            // Set BOTH approvalStatus=APPROVED and costGeneratedFlag=1
            MatReceipt existingReceipt = receiptMapper.selectById(receiptId);
            existingReceipt.setApprovalStatus("APPROVED");
            existingReceipt.setCostGeneratedFlag(1);
            receiptMapper.updateById(existingReceipt);

            // receiptService.update() should throw
            MatReceipt updateReceipt = new MatReceipt();
            updateReceipt.setId(receiptId);
            updateReceipt.setQualityStatus("FAILED");
            BusinessException receiptUpdateEx = assertThrows(BusinessException.class,
                    () -> receiptService.update(updateReceipt),
                    "已审批验收单不可编辑");
            assertEquals("RECEIPT_IN_APPROVAL", receiptUpdateEx.getCode());

            // receiptService.delete() should throw
            Long receiptIdSnapshot = receiptId;
            BusinessException receiptDeleteEx = assertThrows(BusinessException.class,
                    () -> receiptService.delete(receiptIdSnapshot),
                    "已审批验收单不可删除");
            assertEquals("RECEIPT_IN_APPROVAL", receiptDeleteEx.getCode());

            // receiptService.saveItemsBatch() should throw
            MatReceiptItem dummyItem = buildReceiptItem(999L, 1, 1, 0, 0);
            Long receiptIdForBatch = receiptId;
            BusinessException receiptBatchEx = assertThrows(BusinessException.class,
                    () -> receiptService.saveItemsBatch(receiptIdForBatch, List.of(dummyItem)),
                    "已审批验收单不可编辑明细");
            assertEquals("RECEIPT_IN_APPROVAL", receiptBatchEx.getCode());

            System.out.println("F17 验收单守卫 PASS: update=" + receiptUpdateEx.getCode()
                    + ", delete=" + receiptDeleteEx.getCode()
                    + ", saveItemsBatch=" + receiptBatchEx.getCode());

        } finally {
            // Cleanup receipt
            if (receiptId != null) {
                MatReceipt r = receiptMapper.selectById(receiptId);
                if (r != null) {
                    r.setApprovalStatus("DRAFT");
                    r.setCostGeneratedFlag(0);
                    receiptMapper.updateById(r);
                    receiptMapper.deleteById(receiptId);
                }
            }
            // Cleanup helper order
            if (helperOrderId != null) {
                MatPurchaseOrder ho = purchaseOrderMapper.selectById(helperOrderId);
                if (ho != null) {
                    ho.setApprovalStatus("DRAFT");
                    purchaseOrderMapper.updateById(ho);
                    purchaseOrderMapper.deleteById(helperOrderId);
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // F18: Cost summary refresh idempotency and tenant isolation
    // ═══════════════════════════════════════════════════════════
    @Test
    @Order(3)
    @Transactional
    @DisplayName("F18: 动态成本汇总同日连续刷新不撞唯一键")
    void testF18_costSummaryRefreshTwiceDoesNotDuplicateKey() {
        CostItem item = new CostItem();
        item.setTenantId(0L);
        item.setProjectId(PROJECT_ID);
        item.setContractId(CONTRACT_ID);
        item.setPartnerId(PARTNER_ID);
        item.setCostSubjectId(System.currentTimeMillis());
        item.setCostType("MATERIAL");
        item.setAmount(new BigDecimal("12345.67"));
        item.setTaxAmount(BigDecimal.ZERO);
        item.setAmountWithoutTax(new BigDecimal("12345.67"));
        item.setSourceType("REGRESSION_F18");
        item.setSourceId(System.nanoTime());
        item.setSourceItemId(1L);
        item.setCostDate(LocalDate.now());
        item.setCostStatus("CONFIRMED");
        item.setGeneratedFlag(1);
        costItemMapper.insert(item);

        assertDoesNotThrow(() -> {
            costSummaryService.refreshSummary(0L, PROJECT_ID);
            costSummaryService.refreshSummary(0L, PROJECT_ID);
        }, "同一项目同日连续刷新动态成本汇总不应因逻辑删除残留记录撞唯一键");
    }

    @Test
    @Order(4)
    @Transactional
    @DisplayName("F19: 动态成本汇总拒绝跨租户项目")
    void testF19_costSummaryRejectsCrossTenantProject() {
        long otherTenantProjectId = System.currentTimeMillis();
        jdbcTemplate.update("""
                INSERT INTO pm_project (
                    id, tenant_id, project_code, project_name, project_type,
                    contract_amount, target_cost, status, approval_status,
                    created_by, created_at, updated_by, updated_at, deleted_flag
                ) VALUES (?, 999, ?, '跨租户项目', '房建工程', 1000, 800, 'ACTIVE', 'APPROVED', 1, NOW(), 1, NOW(), 0)
                """, otherTenantProjectId, "PRJ-REG-" + otherTenantProjectId);

        BusinessException getEx = assertThrows(BusinessException.class,
                () -> costSummaryService.getProjectSummary(0L, otherTenantProjectId),
                "当前租户不应读取其他租户项目汇总");
        assertEquals("PROJECT_NOT_FOUND", getEx.getCode());

        BusinessException refreshEx = assertThrows(BusinessException.class,
                () -> costSummaryService.refreshSummary(0L, otherTenantProjectId),
                "当前租户不应刷新其他租户项目汇总");
        assertEquals("PROJECT_NOT_FOUND", refreshEx.getCode());
    }

    // ═══════════════════════════════════════════════════════════
    // Helper methods
    // ═══════════════════════════════════════════════════════════

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

    private MatReceiptItem buildReceiptItem(Long orderItemId,
                                             int actualQty, int qualifiedQty,
                                             int unitPrice, int amount) {
        MatReceiptItem item = new MatReceiptItem();
        item.setOrderItemId(orderItemId);
        item.setActualQuantity(new BigDecimal(actualQty));
        item.setQualifiedQuantity(new BigDecimal(qualifiedQty));
        item.setUnitPrice(new BigDecimal(unitPrice));
        item.setAmount(new BigDecimal(amount));
        return item;
    }
}
