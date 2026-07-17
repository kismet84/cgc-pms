package com.cgcpms.purchase;

import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.purchase.entity.MatPurchaseOrder;
import com.cgcpms.purchase.entity.MatPurchaseOrderItem;
import com.cgcpms.purchase.mapper.MatPurchaseOrderItemMapper;
import com.cgcpms.purchase.mapper.MatPurchaseOrderMapper;
import com.cgcpms.purchase.service.MatPurchaseOrderService;
import com.cgcpms.purchase.vo.MatPurchaseOrderItemVO;
import com.cgcpms.purchase.vo.MatPurchaseOrderVO;
import org.junit.jupiter.api.*;
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
@DisplayName("MatPurchaseOrderService — CRUD + guards + batch items")
class MatPurchaseOrderServiceTest {

    private static final long USER_ADMIN = 1L;
    private static final long TENANT_ID = 0L;
    private static final long PROJECT_ID = 10001L;

    @Autowired private MatPurchaseOrderService service;
    @Autowired private MatPurchaseOrderMapper orderMapper;
    @Autowired private MatPurchaseOrderItemMapper itemMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach void setupContext() {
        TestUserContext.setAdmin(TENANT_ID, USER_ADMIN);
        ensureWorkflowApprover();
    }
    @AfterEach void clearContext() {
        jdbcTemplate.update("DELETE FROM sys_user WHERE id = ? AND username = ?", USER_ADMIN, "test_purchase_approver");
        TestUserContext.clear();
    }

    private void ensureWorkflowApprover() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_user WHERE id = ?", Integer.class, USER_ADMIN);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO sys_user
                    (id, tenant_id, username, password, real_name, status, is_admin,
                     created_by, updated_by, deleted_flag, remark)
                VALUES (?, ?, ?, ?, ?, 'ENABLE', 1, ?, ?, 0, ?)
                """, USER_ADMIN, TENANT_ID, "test_purchase_approver", "{noop}test",
                "采购审批测试人", USER_ADMIN, USER_ADMIN, "MatPurchaseOrderServiceTest local approver");
    }

    @Test @Transactional @DisplayName("create → auto-generates PO code, returns ID")
    void testCreate() {
        MatPurchaseOrder order = new MatPurchaseOrder();
        order.setProjectId(PROJECT_ID);
        order.setOrderType("PURCHASE");
        Long id = service.create(order);
        assertNotNull(id);
        MatPurchaseOrderVO vo = service.getById(id);
        assertNotNull(vo.getOrderCode(), "应自动生成订单编码");
        assertTrue(vo.getOrderCode().startsWith("PO-"), "编码应以 PO- 开头");
        assertEquals("DRAFT", vo.getApprovalStatus());
        assertEquals("DRAFT", vo.getOrderStatus());
    }

    @Test @Transactional @DisplayName("create → contract validation with PERFORMING contract")
    void testCreate_WithContract() {
        MatPurchaseOrder order = new MatPurchaseOrder();
        order.setProjectId(PROJECT_ID);
        order.setContractId(30001L); // CT-2026-001 is SUB with PERFORMING status
        Long id = service.create(order);
        assertNotNull(id);
    }

    @Test @Transactional @DisplayName("getById → throws on non-existent")
    void testGetById_NotFound() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.getById(99999999L));
        assertEquals("PURCHASE_ORDER_NOT_FOUND", ex.getCode());
    }

    @Test @Transactional @DisplayName("getById → tenant isolation")
    void testGetById_CrossTenant() {
        MatPurchaseOrder order = new MatPurchaseOrder();
        order.setProjectId(PROJECT_ID);
        order.setOrderType("PURCHASE");
        Long id = service.create(order);

        TestUserContext.clear();
        TestUserContext.setUser(999L, 999L, "other-tenant", List.of("ADMIN"));
        assertThrows(BusinessException.class, () -> service.getById(id));
    }

    @Test @Transactional @DisplayName("M2: getById → same tenant without project access is denied")
    void testGetById_NoProjectAccess() {
        MatPurchaseOrder order = new MatPurchaseOrder();
        order.setProjectId(PROJECT_ID);
        order.setOrderType("PURCHASE");
        Long id = service.create(order);

        TestUserContext.clear();
        TestUserContext.setUser(TENANT_ID, 999L, "no-project", List.of());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getById(id));
        assertEquals("PROJECT_ACCESS_DENIED", ex.getCode());
    }

    @Test @Transactional @DisplayName("getPage → returns paginated results")
    void testGetPage() {
        var page = service.getPage(1, 10, null, null, null, null, null, null);
        assertNotNull(page);
        assertTrue(page.getTotal() >= 0);
    }

    @Test @Transactional @DisplayName("update → succeeds")
    void testUpdate() {
        MatPurchaseOrder order = new MatPurchaseOrder();
        order.setProjectId(PROJECT_ID);
        order.setOrderType("PURCHASE");
        Long id = service.create(order);

        MatPurchaseOrder upd = new MatPurchaseOrder();
        upd.setId(id);
        upd.setProjectId(PROJECT_ID);
        upd.setOrderType("PURCHASE");
        service.update(upd);
    }

    @Test @Transactional @DisplayName("update → guard: cannot update when APPROVING")
    void testUpdate_WhenApproving() {
        MatPurchaseOrder order = new MatPurchaseOrder();
        order.setProjectId(PROJECT_ID); order.setOrderType("PURCHASE");
        Long id = service.create(order);
        MatPurchaseOrder db = orderMapper.selectById(id);
        db.setApprovalStatus("APPROVING"); orderMapper.updateById(db);

        MatPurchaseOrder upd = new MatPurchaseOrder();
        upd.setId(id); upd.setProjectId(PROJECT_ID);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(upd));
        assertEquals("ORDER_IN_APPROVAL", ex.getCode());
    }

    @Test @Transactional @DisplayName("update → guard: cannot update when APPROVED")
    void testUpdate_WhenApproved() {
        MatPurchaseOrder order = new MatPurchaseOrder();
        order.setProjectId(PROJECT_ID); order.setOrderType("PURCHASE");
        Long id = service.create(order);
        MatPurchaseOrder db = orderMapper.selectById(id);
        db.setApprovalStatus("APPROVED"); orderMapper.updateById(db);

        MatPurchaseOrder upd = new MatPurchaseOrder();
        upd.setId(id); upd.setProjectId(PROJECT_ID);
        assertThrows(BusinessException.class, () -> service.update(upd));
    }

    @Test @Transactional @DisplayName("submitForApproval → DRAFT→APPROVING")
    void testSubmitForApproval() {
        Long id = createSubmittableOrder();

        service.submitForApproval(id);
        MatPurchaseOrderVO vo = service.getById(id);
        assertEquals("APPROVING", vo.getApprovalStatus());
    }

    @Test @Transactional @DisplayName("submitForApproval → duplicate throws")
    void testSubmitForApproval_Duplicate() {
        Long id = createSubmittableOrder();
        service.submitForApproval(id);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.submitForApproval(id));
        assertEquals("PURCHASE_ORDER_ALREADY_SUBMITTED", ex.getCode());
    }

    @Test @Transactional @DisplayName("submitForApproval → 缺合同、供应商或商业明细时拒绝")
    void testSubmitForApproval_IncompleteCommercialTerms() {
        MatPurchaseOrder order = new MatPurchaseOrder();
        order.setProjectId(PROJECT_ID);
        order.setOrderType("PURCHASE");
        order.setOrderDate(LocalDate.now());
        order.setDeliveryDate(LocalDate.now().plusDays(1));
        Long id = service.create(order);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.submitForApproval(id));
        assertEquals("PURCHASE_ORDER_CONTRACT_REQUIRED", ex.getCode());
    }

    @Test @Transactional @DisplayName("update → 驳回订单修改后恢复草稿并可重新提交")
    void testUpdate_RejectedOrderReturnsToDraft() {
        Long id = createSubmittableOrder();
        MatPurchaseOrder db = orderMapper.selectById(id);
        db.setApprovalStatus("REJECTED");
        orderMapper.updateById(db);

        MatPurchaseOrder update = new MatPurchaseOrder();
        update.setId(id);
        update.setProjectId(PROJECT_ID);
        update.setContractId(30001L);
        update.setPartnerId(20002L);
        update.setOrderType("PURCHASE");
        update.setOrderDate(LocalDate.now());
        update.setDeliveryDate(LocalDate.now().plusDays(7));
        update.setTotalAmount(new BigDecimal("35000.00"));
        service.update(update);

        assertEquals("DRAFT", orderMapper.selectById(id).getApprovalStatus());
        service.submitForApproval(id);
        assertEquals("APPROVING", orderMapper.selectById(id).getApprovalStatus());
    }

    @Test @Transactional @DisplayName("saveItemsBatch → bulks saves items")
    void testSaveItemsBatch() {
        MatPurchaseOrder order = new MatPurchaseOrder();
        order.setProjectId(PROJECT_ID); order.setOrderType("PURCHASE");
        Long id = service.create(order);

        MatPurchaseOrderItem item = new MatPurchaseOrderItem();
        item.setMaterialId(1L); item.setOrderId(id);
        item.setQuantity(new BigDecimal("10.00"));
        item.setUnitPrice(new BigDecimal("3500.00"));
        item.setAmount(new BigDecimal("35000.00"));
        service.saveItemsBatch(id, List.of(item));

        List<MatPurchaseOrderItemVO> items = service.getItems(id);
        assertEquals(1, items.size());
    }

    @Test @Transactional @DisplayName("getItems → throws on non-existent")
    void testGetItems_NotFound() {
        assertThrows(BusinessException.class, () -> service.getItems(99999999L));
    }

    @Test @Transactional @DisplayName("getItems → returns empty list")
    void testGetItems_Empty() {
        MatPurchaseOrder order = new MatPurchaseOrder();
        order.setProjectId(PROJECT_ID); order.setOrderType("PURCHASE");
        Long id = service.create(order);

        List<MatPurchaseOrderItemVO> items = service.getItems(id);
        assertNotNull(items);
        assertTrue(items.isEmpty());
    }

    private Long createSubmittableOrder() {
        MatPurchaseOrder order = new MatPurchaseOrder();
        order.setProjectId(PROJECT_ID);
        order.setContractId(30001L);
        order.setPartnerId(20002L);
        order.setOrderType("PURCHASE");
        order.setOrderDate(LocalDate.now());
        order.setDeliveryDate(LocalDate.now().plusDays(7));
        Long id = service.create(order);

        MatPurchaseOrderItem item = new MatPurchaseOrderItem();
        item.setMaterialId(1L);
        item.setQuantity(new BigDecimal("10.00"));
        item.setUnitPrice(new BigDecimal("3500.00"));
        item.setAmount(new BigDecimal("35000.00"));
        service.saveItemsBatch(id, List.of(item));
        return id;
    }
}
