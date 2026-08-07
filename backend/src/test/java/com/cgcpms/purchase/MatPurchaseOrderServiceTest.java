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

@SpringBootTest
@ActiveProfiles("local")
@DisplayName("MatPurchaseOrderService — CRUD + guards + batch items")
class MatPurchaseOrderServiceTest {

    private static final long USER_ADMIN = 1L;
    private static final long TENANT_ID = 0L;
    private static final long PROJECT_ID = 10001L;
    private static final long BUDGET_ID = 49991L;
    private static final long BUDGET_LINE_ID = 49992L;

    @Autowired private MatPurchaseOrderService service;
    @Autowired private MatPurchaseOrderMapper orderMapper;
    @Autowired private MatPurchaseOrderItemMapper itemMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach void setupContext() {
        TestUserContext.setAdmin(TENANT_ID, USER_ADMIN);
        ensureWorkflowApprover();
        ensureActiveBudget();
        ensurePurchasePricingFixture();
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

    private void ensureActiveBudget() {
        Long costSubjectId = jdbcTemplate.queryForObject(
                "SELECT id FROM cost_subject ORDER BY id LIMIT 1", Long.class);
        jdbcTemplate.update("""
                INSERT INTO project_budget (
                    id, tenant_id, project_id, budget_code, version_no, budget_name, total_amount,
                    approval_status, status, active_flag, active_token, created_by, deleted_flag
                ) SELECT ?, ?, ?, 'BUD-PO-SERVICE', 'PO-TDD-V1', '采购订单测试预算', 5000000,
                    'APPROVED', 'ACTIVE', 1, ?, ?, 0
                WHERE NOT EXISTS (SELECT 1 FROM project_budget WHERE id = ?)
                """, BUDGET_ID, TENANT_ID, PROJECT_ID, BUDGET_ID, USER_ADMIN, BUDGET_ID);
        jdbcTemplate.update("""
                INSERT INTO project_budget_line (
                    id, tenant_id, budget_id, project_id, cost_subject_id, budget_amount,
                    reserved_amount, consumed_amount, version, created_by, deleted_flag
                ) SELECT ?, ?, ?, ?, ?, 5000000, 0, 0, 0, ?, 0
                WHERE NOT EXISTS (SELECT 1 FROM project_budget_line WHERE id = ?)
                """, BUDGET_LINE_ID, TENANT_ID, BUDGET_ID, PROJECT_ID, costSubjectId,
                USER_ADMIN, BUDGET_LINE_ID);
    }

    private void ensurePurchasePricingFixture() {
        jdbcTemplate.update("UPDATE ct_contract SET contract_type='PURCHASE', pricing_mode='FIXED' WHERE id=30001");
        jdbcTemplate.update("""
                INSERT INTO ct_contract_item
                    (id,tenant_id,contract_id,material_id,item_code,item_name,item_spec,unit,
                     quantity,unit_price,amount,sort_order,created_by,deleted_flag)
                SELECT 49994,0,30001,1,'PO-TEST-MAT','测试材料','测试规格','m',
                       1000,3500,3500000,1,1,0
                WHERE NOT EXISTS (
                    SELECT 1 FROM ct_contract_item
                    WHERE tenant_id=0 AND contract_id=30001 AND material_id=1 AND deleted_flag=0)
                """);
    }

    private void attachCleanFile(Long orderId) {
        jdbcTemplate.update("""
                INSERT INTO sys_file (
                    id, tenant_id, business_type, business_id, file_name, original_name,
                    file_size, storage_path, bucket_name, virus_scan_status, created_by, deleted_flag
                ) VALUES (?, ?, 'PURCHASE_ORDER', ?, 'order.pdf', 'order.pdf', 10,
                    '/test/order.pdf', 'test', 'CLEAN', ?, 0)
                """, Math.abs(System.nanoTime()), TENANT_ID, orderId, USER_ADMIN);
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
        assertEquals(0, vo.getExceptionPurchaseFlag());
    }

    @Test @Transactional @DisplayName("create → ignores tenant, identity, amount, and state injection")
    void createIgnoresProtectedFields() {
        MatPurchaseOrder order = new MatPurchaseOrder();
        order.setId(Long.MAX_VALUE - 1000);
        order.setTenantId(999L);
        order.setProjectId(PROJECT_ID);
        order.setOrderCode("PO-INJECTED");
        order.setRequestId(null);
        order.setTotalAmount(new BigDecimal("999999.99"));
        order.setApprovalStatus("APPROVED");
        order.setOrderStatus("COMPLETED");
        order.setExceptionPurchaseFlag(2);
        order.setExceptionReason("无有效例外标记时不得保存原因");
        order.setDeletedFlag(1);

        Long id = service.create(order);
        MatPurchaseOrder stored = orderMapper.selectById(id);

        assertNotEquals(order.getId(), id);
        assertEquals(TENANT_ID, stored.getTenantId());
        assertTrue(stored.getOrderCode().startsWith("PO-"));
        assertNotEquals("PO-INJECTED", stored.getOrderCode());
        assertEquals(0, BigDecimal.ZERO.compareTo(stored.getTotalAmount()));
        assertEquals("DRAFT", stored.getApprovalStatus());
        assertEquals("DRAFT", stored.getOrderStatus());
        assertEquals(0, stored.getExceptionPurchaseFlag());
        assertNull(stored.getExceptionReason());
        assertEquals(0, stored.getDeletedFlag());
    }

    @Test @Transactional @DisplayName("订单详情与列表返回采购申请业务编号")
    void orderReturnsPurchaseRequestBusinessCode() {
        long requestId = Math.abs(System.nanoTime());
        String requestCode = "PR-SOURCE-" + requestId;
        jdbcTemplate.update("""
                INSERT INTO mat_purchase_request (
                    id, tenant_id, project_id, request_code, approval_status, status,
                    created_by, updated_by, deleted_flag
                ) VALUES (?, ?, ?, ?, 'APPROVED', 'APPROVED', ?, ?, 0)
                """, requestId, TENANT_ID, PROJECT_ID, requestCode, USER_ADMIN, USER_ADMIN);

        MatPurchaseOrder order = new MatPurchaseOrder();
        order.setTenantId(TENANT_ID);
        order.setProjectId(PROJECT_ID);
        order.setRequestId(requestId);
        order.setOrderCode("PO-SOURCE-" + requestId);
        order.setOrderType("PURCHASE");
        order.setApprovalStatus("DRAFT");
        order.setOrderStatus("DRAFT");
        orderMapper.insert(order);
        Long id = order.getId();

        assertEquals(requestCode, service.getById(id).getRequestCode());
        MatPurchaseOrderVO row = service.getPage(1, 20, PROJECT_ID, null, null, null, null, null)
                .getRecords().stream()
                .filter(item -> id.toString().equals(item.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals(requestCode, row.getRequestCode());
    }

    @Test @Transactional @DisplayName("create → 采购申请必须通过转换流程")
    void createRejectsDirectPurchaseRequestLinkage() {
        MatPurchaseOrder order = new MatPurchaseOrder();
        order.setProjectId(PROJECT_ID);
        order.setRequestId(Math.abs(System.nanoTime()));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(order));
        assertEquals("PURCHASE_REQUEST_CONVERSION_REQUIRED", ex.getCode());
    }

    @Test @Transactional @DisplayName("create → contract validation with PERFORMING contract")
    void testCreate_WithContract() {
        MatPurchaseOrder order = new MatPurchaseOrder();
        order.setProjectId(PROJECT_ID);
        order.setContractId(30001L);
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
        MatPurchaseOrderItem item = new MatPurchaseOrderItem();
        item.setTenantId(TENANT_ID);
        item.setOrderId(id);
        item.setProjectId(PROJECT_ID);
        item.setMaterialId(1L);
        item.setQuantity(BigDecimal.ONE);
        item.setUnitPrice(new BigDecimal("123.00"));
        item.setAmount(new BigDecimal("123.00"));
        item.setTaxRate(BigDecimal.ZERO);
        item.setTaxAmount(BigDecimal.ZERO);
        item.setAmountWithoutTax(new BigDecimal("123.00"));
        item.setReceivedQuantity(BigDecimal.ZERO);
        itemMapper.insert(item);

        MatPurchaseOrder upd = new MatPurchaseOrder();
        upd.setId(id);
        upd.setProjectId(PROJECT_ID);
        upd.setOrderType("PURCHASE");
        service.update(upd);
        assertEquals(0, new BigDecimal("123.00").compareTo(orderMapper.selectById(id).getTotalAmount()));
    }

    @Test @Transactional @DisplayName("update → 仅允许修改商业字段")
    void updateIgnoresProtectedFields() {
        MatPurchaseOrder order = new MatPurchaseOrder();
        order.setProjectId(PROJECT_ID);
        order.setOrderType("PURCHASE");
        Long id = service.create(order);
        MatPurchaseOrder original = orderMapper.selectById(id);

        MatPurchaseOrder update = new MatPurchaseOrder();
        update.setId(id);
        update.setTenantId(999L);
        update.setProjectId(999L);
        update.setRequestId(999L);
        update.setOrderCode("PO-TAMPERED");
        update.setOrderType("TAMPERED");
        update.setOrderStatus("APPROVED");
        update.setApprovalStatus("APPROVED");
        update.setTotalAmount(new BigDecimal("999"));
        service.update(update);

        MatPurchaseOrder stored = orderMapper.selectById(id);
        assertEquals(original.getTenantId(), stored.getTenantId());
        assertEquals(original.getProjectId(), stored.getProjectId());
        assertEquals(original.getRequestId(), stored.getRequestId());
        assertEquals(original.getOrderCode(), stored.getOrderCode());
        assertEquals(original.getOrderType(), stored.getOrderType());
        assertEquals(original.getOrderStatus(), stored.getOrderStatus());
        assertEquals(original.getApprovalStatus(), stored.getApprovalStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(stored.getTotalAmount()));
    }

    @Test @Transactional @DisplayName("update → guard: cannot update when APPROVING")
    void testUpdate_WhenApproving() {
        MatPurchaseOrder order = new MatPurchaseOrder();
        order.setProjectId(PROJECT_ID); order.setOrderType("PURCHASE");
        order.setContractId(30001L);
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

    @Test @Transactional @DisplayName("submitForApproval → 黑名单供应商拒绝")
    void testSubmitForApproval_BlacklistedSupplier() {
        Long id = createSubmittableOrder();
        jdbcTemplate.update("UPDATE md_partner SET blacklist_flag=1 WHERE id=20002");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.submitForApproval(id));
        assertEquals("PURCHASE_ORDER_PARTNER_BLACKLISTED", ex.getCode());
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
        update.setDeliveryTerms("送达项目仓库并验收");
        update.setExceptionPurchaseFlag(1);
        update.setExceptionReason("现场紧急采购测试");
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
        order.setContractId(30001L);
        Long id = service.create(order);

        MatPurchaseOrderItem item = new MatPurchaseOrderItem();
        item.setMaterialId(1L); item.setOrderId(id);
        item.setQuantity(new BigDecimal("10.00"));
        item.setUnitPrice(BigDecimal.ONE);
        item.setAmount(BigDecimal.TEN);
        service.saveItemsBatch(id, List.of(item));

        List<MatPurchaseOrderItemVO> items = service.getItems(id);
        Long expectedContractItemId = jdbcTemplate.queryForObject("""
                SELECT id FROM ct_contract_item
                WHERE tenant_id=0 AND contract_id=30001 AND material_id=1 AND deleted_flag=0
                """, Long.class);
        assertEquals(1, items.size());
        assertEquals("3500.00", items.getFirst().getUnitPrice());
        assertEquals(expectedContractItemId.toString(), items.getFirst().getContractItemId());
    }

    @Test @Transactional @DisplayName("来源申请订单仅允许补录价格税率，不得改来源字段")
    void linkedOrderItemsPreserveRequestSource() {
        long requestId = Math.abs(System.nanoTime());
        long requestItemId = requestId + 1;
        jdbcTemplate.update("""
                INSERT INTO mat_purchase_request (
                    id, tenant_id, project_id, request_code, approval_status, status,
                    created_by, updated_by, deleted_flag
                ) VALUES (?, ?, ?, ?, 'APPROVED', 'CONVERTED', ?, ?, 0)
                """, requestId, TENANT_ID, PROJECT_ID, "PR-LINKED-" + requestId, USER_ADMIN, USER_ADMIN);
        jdbcTemplate.update("""
                INSERT INTO mat_purchase_request_item (
                    id, tenant_id, request_id, material_id, budget_line_id, quantity,
                    estimated_unit_price, estimated_amount, unit, created_by, updated_by, deleted_flag
                ) VALUES (?, ?, ?, 1, ?, 2, 50, 100, '个', ?, ?, 0)
                """, requestItemId, TENANT_ID, requestId, BUDGET_LINE_ID, USER_ADMIN, USER_ADMIN);

        MatPurchaseOrder order = new MatPurchaseOrder();
        order.setTenantId(TENANT_ID);
        order.setProjectId(PROJECT_ID);
        order.setRequestId(requestId);
        order.setContractId(30001L);
        order.setPartnerId(20002L);
        order.setPricingMode("FIXED");
        order.setOrderCode("PO-LINKED-" + requestId);
        order.setOrderType("PURCHASE");
        order.setOrderStatus("DRAFT");
        order.setApprovalStatus("DRAFT");
        orderMapper.insert(order);

        MatPurchaseOrderItem item = new MatPurchaseOrderItem();
        item.setRequestItemId(requestItemId);
        item.setMaterialId(1L);
        item.setBudgetLineId(BUDGET_LINE_ID);
        item.setQuantity(new BigDecimal("2"));
        item.setUnit("个");
        item.setUnitPrice(new BigDecimal("10"));
        item.setTaxRate(new BigDecimal("13"));
        service.saveItemsBatch(order.getId(), List.of(item));

        assertEquals(0, new BigDecimal("7000").compareTo(orderMapper.selectById(order.getId()).getTotalAmount()));
        assertEquals(0, new BigDecimal("13").compareTo(itemMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MatPurchaseOrderItem>()
                        .eq(MatPurchaseOrderItem::getOrderId, order.getId())).getTaxRate()));

        jdbcTemplate.update("UPDATE mat_purchase_request_item SET budget_line_id=NULL WHERE id=?", requestItemId);
        item.setId(null);
        item.setBudgetLineId(BUDGET_LINE_ID);
        service.saveItemsBatch(order.getId(), List.of(item));
        assertEquals(BUDGET_LINE_ID, itemMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MatPurchaseOrderItem>()
                        .eq(MatPurchaseOrderItem::getOrderId, order.getId())).getBudgetLineId());

        item.setQuantity(new BigDecimal("3"));
        assertEquals("PURCHASE_ORDER_QUANTITY_ADJUST_REASON_REQUIRED",
                assertThrows(BusinessException.class,
                        () -> service.saveItemsBatch(order.getId(), List.of(item))).getCode());

        long otherRequestId = requestId + 2;
        long otherItemId = requestId + 3;
        jdbcTemplate.update("""
                INSERT INTO mat_purchase_request (
                    id, tenant_id, project_id, request_code, approval_status, status,
                    created_by, updated_by, deleted_flag
                ) VALUES (?, ?, ?, ?, 'APPROVED', 'CONVERTED', ?, ?, 0)
                """, otherRequestId, TENANT_ID, PROJECT_ID, "PR-OTHER-" + otherRequestId, USER_ADMIN, USER_ADMIN);
        jdbcTemplate.update("""
                INSERT INTO mat_purchase_request_item (
                    id, tenant_id, request_id, material_id, budget_line_id, quantity,
                    estimated_unit_price, estimated_amount, unit, created_by, updated_by, deleted_flag
                ) VALUES (?, ?, ?, 1, ?, 2, 50, 100, '个', ?, ?, 0)
                """, otherItemId, TENANT_ID, otherRequestId, BUDGET_LINE_ID, USER_ADMIN, USER_ADMIN);
        item.setRequestItemId(otherItemId);
        item.setQuantity(new BigDecimal("2"));
        assertEquals("PURCHASE_ORDER_REQUEST_ITEM_MISMATCH",
                assertThrows(BusinessException.class,
                        () -> service.saveItemsBatch(order.getId(), List.of(item))).getCode());

    }

    @Test @Transactional @DisplayName("来源申请订单不得删减明细")
    void linkedOrderRejectsRequestItemSubset() {
        long requestId = Math.abs(System.nanoTime());
        jdbcTemplate.update("""
                INSERT INTO mat_purchase_request (
                    id, tenant_id, project_id, request_code, approval_status, status,
                    created_by, updated_by, deleted_flag
                ) VALUES (?, ?, ?, ?, 'APPROVED', 'CONVERTED', ?, ?, 0)
                """, requestId, TENANT_ID, PROJECT_ID, "PR-SUBSET-" + requestId, USER_ADMIN, USER_ADMIN);
        jdbcTemplate.update("""
                INSERT INTO mat_purchase_request_item (
                    id, tenant_id, request_id, material_id, budget_line_id, quantity,
                    estimated_unit_price, estimated_amount, unit, created_by, updated_by, deleted_flag
                ) VALUES (?, ?, ?, 1, ?, 2, 50, 100, '个', ?, ?, 0),
                       (?, ?, ?, 1, ?, 1, 50, 50, '个', ?, ?, 0)
                """, requestId + 1, TENANT_ID, requestId, BUDGET_LINE_ID, USER_ADMIN, USER_ADMIN,
                requestId + 2, TENANT_ID, requestId, BUDGET_LINE_ID, USER_ADMIN, USER_ADMIN);

        MatPurchaseOrder order = new MatPurchaseOrder();
        order.setTenantId(TENANT_ID);
        order.setProjectId(PROJECT_ID);
        order.setRequestId(requestId);
        order.setContractId(30001L);
        order.setPartnerId(20002L);
        order.setPricingMode("FIXED");
        order.setOrderCode("PO-SUBSET-" + requestId);
        order.setOrderType("PURCHASE");
        order.setOrderStatus("DRAFT");
        order.setApprovalStatus("DRAFT");
        orderMapper.insert(order);

        MatPurchaseOrderItem item = new MatPurchaseOrderItem();
        item.setRequestItemId(requestId + 1);
        item.setMaterialId(1L);
        item.setBudgetLineId(BUDGET_LINE_ID);
        item.setQuantity(new BigDecimal("2"));
        item.setUnit("个");
        item.setUnitPrice(BigDecimal.ONE);
        assertEquals("PURCHASE_ORDER_REQUEST_ITEM_MISMATCH",
                assertThrows(BusinessException.class,
                        () -> service.saveItemsBatch(order.getId(), List.of(item))).getCode());
    }

    @Test @Transactional @DisplayName("saveItemsBatch → rejects disabled and cross-tenant materials")
    void saveItemsBatchRejectsInvalidMaterials() {
        MatPurchaseOrder order = new MatPurchaseOrder();
        order.setProjectId(PROJECT_ID);
        order.setOrderType("PURCHASE");
        order.setContractId(30001L);
        Long orderId = service.create(order);

        long disabledId = Math.abs(System.nanoTime());
        jdbcTemplate.update("""
                INSERT INTO md_material (
                    id, tenant_id, material_code, material_name, unit, status,
                    created_by, updated_by, deleted_flag
                ) VALUES (?, ?, ?, '停用材料', '个', 'DISABLE', ?, ?, 0)
                """, disabledId, TENANT_ID, "PO-DISABLED-" + disabledId, USER_ADMIN, USER_ADMIN);
        MatPurchaseOrderItem disabled = new MatPurchaseOrderItem();
        disabled.setMaterialId(disabledId);
        disabled.setQuantity(BigDecimal.ONE);
        disabled.setUnitPrice(BigDecimal.ONE);
        assertEquals("MATERIAL_INVALID",
                assertThrows(BusinessException.class,
                        () -> service.saveItemsBatch(orderId, List.of(disabled))).getCode());

        long foreignId = Math.abs(System.nanoTime());
        jdbcTemplate.update("""
                INSERT INTO md_material (
                    id, tenant_id, material_code, material_name, unit, status,
                    created_by, updated_by, deleted_flag
                ) VALUES (?, 999, ?, '跨租户材料', '个', 'ENABLE', ?, ?, 0)
                """, foreignId, "PO-FOREIGN-" + foreignId, USER_ADMIN, USER_ADMIN);
        MatPurchaseOrderItem foreign = new MatPurchaseOrderItem();
        foreign.setMaterialId(foreignId);
        foreign.setQuantity(BigDecimal.ONE);
        foreign.setUnitPrice(BigDecimal.ONE);
        assertEquals("MATERIAL_INVALID",
                assertThrows(BusinessException.class,
                        () -> service.saveItemsBatch(orderId, List.of(foreign))).getCode());
    }

    @Test @Transactional @DisplayName("saveItemsBatch → persists verified ACTUAL price provenance")
    void saveItemsBatchPersistsVerifiedActualPriceProvenance() {
        jdbcTemplate.update("UPDATE ct_contract SET pricing_mode='ACTUAL' WHERE id=30001");
        long receiptId = Math.abs(System.nanoTime());
        long receiptItemId = receiptId + 1;
        jdbcTemplate.update("""
                INSERT INTO mat_receipt (
                    id,tenant_id,project_id,contract_id,partner_id,receipt_code,receipt_date,
                    receipt_mode,total_amount,approval_status,created_by,deleted_flag)
                VALUES (?,0,10001,30001,20002,?,CURRENT_DATE,'INVENTORY',3500,'APPROVED',1,0)
                """, receiptId, "RC-PRICE-" + receiptId);
        jdbcTemplate.update("""
                INSERT INTO mat_receipt_item (
                    id,tenant_id,receipt_id,material_id,actual_quantity,qualified_quantity,
                    unqualified_quantity,unit_price,amount,created_by,deleted_flag)
                VALUES (?,0,?,1,1,1,0,3500,3500,1,0)
                """, receiptItemId, receiptId);

        MatPurchaseOrder order = new MatPurchaseOrder();
        order.setProjectId(PROJECT_ID);
        order.setContractId(30001L);
        order.setPartnerId(20002L);
        order.setOrderType("PURCHASE");
        Long orderId = service.create(order);

        MatPurchaseOrderItem suggested = new MatPurchaseOrderItem();
        suggested.setMaterialId(1L);
        suggested.setBudgetLineId(BUDGET_LINE_ID);
        suggested.setQuantity(BigDecimal.ONE);
        suggested.setUnitPrice(new BigDecimal("3500"));
        suggested.setPriceSource("RECENT_RECEIPT");
        suggested.setPriceSourceReceiptItemId(receiptItemId);
        service.saveItemsBatch(orderId, List.of(suggested));

        MatPurchaseOrderItemVO verified = service.getItems(orderId).getFirst();
        assertEquals("ACTUAL", verified.getPricingMode());
        assertEquals("RECENT_RECEIPT", verified.getPriceSource());
        assertEquals(String.valueOf(receiptItemId), verified.getPriceSourceReceiptItemId());
        assertNotNull(verified.getMaterialName());
        assertNotNull(verified.getSpecification());

        MatPurchaseOrderItem manual = new MatPurchaseOrderItem();
        manual.setMaterialId(1L);
        manual.setBudgetLineId(BUDGET_LINE_ID);
        manual.setQuantity(BigDecimal.ONE);
        manual.setUnitPrice(new BigDecimal("3600"));
        manual.setPriceSource("RECENT_RECEIPT");
        manual.setPriceSourceReceiptItemId(receiptItemId);
        service.saveItemsBatch(orderId, List.of(manual));

        MatPurchaseOrderItemVO downgraded = service.getItems(orderId).getFirst();
        assertEquals("MANUAL", downgraded.getPriceSource());
        assertNull(downgraded.getPriceSourceReceiptItemId());
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
        // 本测试必须使用真实采购语义，不能复用 V90 的“分包合同 + 普通乙方”夹具。
        jdbcTemplate.update("UPDATE md_partner SET partner_type='SUPPLIER',blacklist_flag=0,status='ENABLE' WHERE id=20002");
        jdbcTemplate.update("UPDATE ct_contract SET contract_type='PURCHASE' WHERE id=30001");
        MatPurchaseOrder order = new MatPurchaseOrder();
        order.setProjectId(PROJECT_ID);
        order.setContractId(30001L);
        order.setPartnerId(20002L);
        order.setOrderType("PURCHASE");
        order.setOrderDate(LocalDate.now());
        order.setDeliveryDate(LocalDate.now().plusDays(7));
        order.setDeliveryTerms("送达项目仓库并验收");
        order.setExceptionPurchaseFlag(1);
        order.setExceptionReason("现场紧急采购测试");
        order.setTotalAmount(new BigDecimal("35000.00"));
        Long id = service.create(order);
        attachCleanFile(id);

        MatPurchaseOrderItem item = new MatPurchaseOrderItem();
        item.setMaterialId(1L);
        item.setQuantity(new BigDecimal("10.00"));
        item.setUnitPrice(new BigDecimal("3500.00"));
        item.setAmount(new BigDecimal("35000.00"));
        item.setBudgetLineId(BUDGET_LINE_ID);
        item.setTaxRate(new BigDecimal("13.00"));
        service.saveItemsBatch(id, List.of(item));
        return id;
    }
}
