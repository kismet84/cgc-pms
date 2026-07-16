package com.cgcpms.purchase;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.material.entity.MdMaterial;
import com.cgcpms.material.mapper.MdMaterialMapper;
import com.cgcpms.purchase.entity.MatPurchaseRequest;
import com.cgcpms.purchase.entity.MatPurchaseRequestItem;
import com.cgcpms.purchase.entity.MatPurchaseOrder;
import com.cgcpms.purchase.mapper.MatPurchaseOrderItemMapper;
import com.cgcpms.purchase.mapper.MatPurchaseOrderMapper;
import com.cgcpms.purchase.mapper.MatPurchaseRequestItemMapper;
import com.cgcpms.purchase.mapper.MatPurchaseRequestMapper;
import com.cgcpms.purchase.service.MatPurchaseRequestService;
import com.cgcpms.purchase.vo.MatPurchaseOrderVO;
import com.cgcpms.purchase.vo.MatPurchaseRequestItemVO;
import com.cgcpms.purchase.vo.MatPurchaseRequestVO;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
@DisplayName("采购申请服务 TDD 测试")
class PurchaseRequestServiceTest {

    private static final long USER_ADMIN = 1L;
    private static final long TENANT_ID = 0L;
    private static final long PROJECT_ID = 100L;

    @Autowired
    private MatPurchaseRequestService requestService;

    @Autowired
    private MatPurchaseRequestMapper requestMapper;

    @Autowired
    private MatPurchaseRequestItemMapper requestItemMapper;

    @Autowired
    private MatPurchaseOrderMapper orderMapper;

    @Autowired
    private MatPurchaseOrderItemMapper orderItemMapper;

    @Autowired
    private MdMaterialMapper mdMaterialMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setupContext() {
        seedWorkflowApprover();
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", TENANT_ID)
                .add("roleCodes", List.of("ADMIN"))
                .build());
        seedProject(PROJECT_ID);
        seedProject(200L);
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    private void seedWorkflowApprover() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE id = ?", Integer.class, USER_ADMIN);
        if (count != null && count == 0) {
            jdbcTemplate.update(
                    "INSERT INTO sys_user (id, tenant_id, username, password, real_name, phone, email, status, is_admin, created_by, remark) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    USER_ADMIN, TENANT_ID, "admin",
                    "$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2",
                    "系统管理员", "13800000000", "admin@cgc-pms.com",
                    "ENABLE", 1, USER_ADMIN, "采购审批测试种子数据");
        } else {
            jdbcTemplate.update(
                    "UPDATE sys_user SET tenant_id = ?, status = ?, deleted_flag = 0 WHERE id = ?",
                    TENANT_ID, "ENABLE", USER_ADMIN);
        }
    }

    private void seedProject(long projectId) {
        jdbcTemplate.update("""
                INSERT INTO pm_project (
                    id, tenant_id, project_code, project_name, project_type,
                    contract_amount, target_cost, status, approval_status,
                    created_by, updated_by, deleted_flag
                )
                SELECT ?, ?, ?, ?, '房建工程', 10000, 8000, 'ACTIVE', 'APPROVED', ?, ?, 0
                WHERE NOT EXISTS (SELECT 1 FROM pm_project WHERE id = ?)
                """,
                projectId, TENANT_ID, "PR-TDD-PRJ-" + projectId, "采购申请测试项目-" + projectId,
                USER_ADMIN, USER_ADMIN, projectId);
    }

    // ═══════════════════════════════════════════════════════════
    // RED → GREEN: Create
    // ═══════════════════════════════════════════════════════════
    @Test
    @Transactional
    @DisplayName("RED→GREEN: 创建采购申请，自动生成编号 PR-yyyyMMdd-XXX")
    void testCreateRequest() {
        MatPurchaseRequest request = new MatPurchaseRequest();
        request.setProjectId(PROJECT_ID);

        Long id = requestService.create(request);
        assertNotNull(id, "创建应返回雪花ID");

        // GREEN: verify persisted
        MatPurchaseRequestVO vo = requestService.getById(id);
        assertNotNull(vo.getRequestCode(), "应自动生成申请编号");
        assertTrue(vo.getRequestCode().startsWith("PR-"), "编号应以 PR- 开头");
        assertEquals("DRAFT", vo.getApprovalStatus());
        assertEquals("DRAFT", vo.getStatus());
        assertEquals(String.valueOf(PROJECT_ID), vo.getProjectId());
        assertEquals(String.valueOf(TENANT_ID), vo.getTenantId());
    }

    @Test
    @Transactional
    @DisplayName("创建采购申请时拒绝无项目数据范围的用户")
    void createRejectsProjectOutsideCurrentUserScope() {
        UserContext.set(Jwts.claims()
                .add("userId", 2L)
                .add("username", "limited-user")
                .add("tenantId", TENANT_ID)
                .add("roleCodes", List.of())
                .build());
        MatPurchaseRequest request = new MatPurchaseRequest();
        request.setProjectId(PROJECT_ID);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> requestService.create(request));
        assertEquals("PROJECT_ACCESS_DENIED", ex.getCode());
    }

    @Test
    @DisplayName("采购经理具备补货到采购申请闭环所需权限")
    void purchaseManagerHasReplenishmentPermissions() {
        Set<String> permissions = Set.copyOf(jdbcTemplate.queryForList("""
                SELECT m.perms
                FROM sys_role r
                JOIN sys_role_menu rm ON rm.role_id = r.id
                JOIN sys_menu m ON m.id = rm.menu_id
                WHERE r.role_code = 'PURCHASE_MANAGER' AND m.perms IS NOT NULL
                """, String.class));

        assertTrue(permissions.containsAll(Set.of(
                "inventory:warehouse:list",
                "inventory:stock:list",
                "material:dict:list",
                "contract:query",
                "system:dict:list",
                "purchase:request:list",
                "purchase:request:add",
                "purchase:request:edit",
                "purchase:request:delete",
                "purchase:request:submit")));
    }

    @Test
    @Transactional
    @DisplayName("RED→GREEN: 创建采购申请时合同必须属于同一项目")
    void createRejectsContractFromDifferentProject() {
        MatPurchaseRequest request = new MatPurchaseRequest();
        request.setProjectId(200L);
        request.setContractId(30001L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> requestService.create(request));
        assertEquals("CONTRACT_PROJECT_MISMATCH", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════
    // RED → GREEN: Request code auto-generates sequentially
    // ═══════════════════════════════════════════════════════════
    @Test
    @Transactional
    @DisplayName("RED→GREEN: 同一天创建多个申请，编号递增")
    void testRequestCodeSequence() {
        MatPurchaseRequest r1 = new MatPurchaseRequest();
        r1.setProjectId(PROJECT_ID);
        Long id1 = requestService.create(r1);
        String code1 = requestService.getById(id1).getRequestCode();

        MatPurchaseRequest r2 = new MatPurchaseRequest();
        r2.setProjectId(PROJECT_ID);
        Long id2 = requestService.create(r2);
        String code2 = requestService.getById(id2).getRequestCode();

        assertNotEquals(code1, code2, "两个申请编号应不同");
        // Extract sequence numbers
        String seq1 = code1.substring(code1.lastIndexOf("-") + 1);
        String seq2 = code2.substring(code2.lastIndexOf("-") + 1);
        assertEquals(String.format("%03d", Integer.parseInt(seq1) + 1),
                String.format("%03d", Integer.parseInt(seq2)),
                "编号应递增");
    }

    // ═══════════════════════════════════════════════════════════
    // RED → GREEN: Page query with project filter
    // ═══════════════════════════════════════════════════════════
    @Test
    @Transactional
    @DisplayName("RED→GREEN: 分页查询带项目过滤")
    void testPageQueryWithProjectFilter() {
        MatPurchaseRequest r1 = new MatPurchaseRequest();
        r1.setProjectId(PROJECT_ID);
        requestService.create(r1);

        MatPurchaseRequest r2 = new MatPurchaseRequest();
        r2.setProjectId(200L);
        requestService.create(r2);

        PageResult<MatPurchaseRequestVO> page1 = requestService.getPage(1, 20, PROJECT_ID, null, null, null);
        assertTrue(page1.getTotal() >= 1, "项目100应有至少1个申请");

        // All records in page should have PROJECT_ID
        for (MatPurchaseRequestVO vo : page1.getRecords()) {
            assertEquals(String.valueOf(PROJECT_ID), vo.getProjectId());
        }
    }

    // ═══════════════════════════════════════════════════════════
    // RED → GREEN: Status filter
    // ═══════════════════════════════════════════════════════════
    @Test
    @Transactional
    @DisplayName("RED→GREEN: 按审批状态筛选")
    void testApprovalStatusFilter() {
        MatPurchaseRequest r1 = new MatPurchaseRequest();
        r1.setProjectId(PROJECT_ID);
        requestService.create(r1);

        PageResult<MatPurchaseRequestVO> drafts = requestService.getPage(1, 20, null, "DRAFT", null, null);
        assertTrue(drafts.getTotal() >= 1, "应有草稿状态的申请");
        for (MatPurchaseRequestVO vo : drafts.getRecords()) {
            assertEquals("DRAFT", vo.getApprovalStatus());
        }
    }

    // ═══════════════════════════════════════════════════════════
    // RED → GREEN: Update request
    // ═══════════════════════════════════════════════════════════
    @Test
    @Transactional
    @DisplayName("RED→GREEN: 更新采购申请信息")
    void testUpdateRequest() {
        MatPurchaseRequest request = new MatPurchaseRequest();
        request.setProjectId(PROJECT_ID);
        Long id = requestService.create(request);

        MatPurchaseRequest update = new MatPurchaseRequest();
        update.setId(id);
        update.setProjectId(200L);
        update.setRemark("更新测试");
        requestService.update(update);

        MatPurchaseRequestVO vo = requestService.getById(id);
        assertEquals("200", vo.getProjectId());
        assertEquals("更新测试", vo.getRemark());
    }

    // ═══════════════════════════════════════════════════════════
    // RED → GREEN: Cross-tenant access denied
    // ═══════════════════════════════════════════════════════════
    @Test
    @Transactional
    @DisplayName("RED→GREEN: 跨租户访问应抛异常")
    void testCrossTenantAccessDenied() {
        MatPurchaseRequest request = new MatPurchaseRequest();
        request.setProjectId(PROJECT_ID);
        Long id = requestService.create(request);

        // Switch to tenant 999
        UserContext.clear();
        UserContext.set(Jwts.claims()
                .add("userId", 999L)
                .add("username", "other")
                .add("tenantId", 999L)
                .build());

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            requestService.getById(id);
        }, "跨租户访问应抛 BusinessException");
        assertEquals("PURCHASE_REQUEST_NOT_FOUND", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════
    // RED → GREEN: GetById throws on non-existent
    // ═══════════════════════════════════════════════════════════
    @Test
    @Transactional
    @DisplayName("RED→GREEN: 查询不存在的申请应抛异常")
    void testGetByNonExistentId() {
        BusinessException ex = assertThrows(BusinessException.class, () -> {
            requestService.getById(99999999L);
        }, "查询不存在的申请应抛 BusinessException");
        assertEquals("PURCHASE_REQUEST_NOT_FOUND", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════
    // RED → GREEN: Delete draft
    // ═══════════════════════════════════════════════════════════
    @Test
    @Transactional
    @DisplayName("RED→GREEN: 删除草稿状态的申请")
    void testDeleteDraft() {
        MatPurchaseRequest request = new MatPurchaseRequest();
        request.setProjectId(PROJECT_ID);
        Long id = requestService.create(request);

        requestService.delete(id);

        // After logical delete, getById should throw
        assertThrows(BusinessException.class, () -> {
            requestService.getById(id);
        }, "删除后查询应抛异常");
    }

    // ═══════════════════════════════════════════════════════════
    // RED → GREEN: Save items batch
    // ═══════════════════════════════════════════════════════════
    @Test
    @Transactional
    @DisplayName("RED→GREEN: 批量保存采购申请明细")
    void testSaveItemsBatch() {
        MatPurchaseRequest request = new MatPurchaseRequest();
        request.setProjectId(PROJECT_ID);
        Long requestId = requestService.create(request);

        MatPurchaseRequestItem item1 = new MatPurchaseRequestItem();
        item1.setMaterialId(1L);
        item1.setQuantity(new BigDecimal("100.00"));
        item1.setUnit("m³");
        item1.setPlannedDate(LocalDate.of(2026, 7, 1));

        MatPurchaseRequestItem item2 = new MatPurchaseRequestItem();
        item2.setMaterialId(2L);
        item2.setQuantity(new BigDecimal("50.00"));
        item2.setUnit("吨");
        item2.setPlannedDate(LocalDate.of(2026, 7, 15));

        requestService.saveItemsBatch(requestId, List.of(item1, item2));

        List<MatPurchaseRequestItemVO> items = requestService.getItems(requestId);
        assertEquals(2, items.size(), "应有2条明细");
    }

    @Test
    @Transactional
    @DisplayName("保存采购申请明细时拒绝跨租户物料")
    void saveItemsBatchRejectsMaterialFromAnotherTenant() {
        MatPurchaseRequest request = new MatPurchaseRequest();
        request.setProjectId(PROJECT_ID);
        Long requestId = requestService.create(request);

        MdMaterial foreign = new MdMaterial();
        foreign.setTenantId(999L);
        foreign.setMaterialCode("FOREIGN-MATERIAL");
        foreign.setMaterialName("跨租户物料");
        foreign.setUnit("个");
        foreign.setStatus("ENABLE");
        mdMaterialMapper.insert(foreign);

        MatPurchaseRequestItem item = new MatPurchaseRequestItem();
        item.setMaterialId(foreign.getId());
        item.setQuantity(BigDecimal.ONE);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> requestService.saveItemsBatch(requestId, List.of(item)));
        assertEquals("MATERIAL_NOT_FOUND", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("保存采购申请明细时拒绝非正数量")
    void saveItemsBatchRejectsNonPositiveQuantity() {
        MatPurchaseRequest request = new MatPurchaseRequest();
        request.setProjectId(PROJECT_ID);
        Long requestId = requestService.create(request);

        MatPurchaseRequestItem item = new MatPurchaseRequestItem();
        item.setMaterialName("零数量物料");
        item.setQuantity(BigDecimal.ZERO);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> requestService.saveItemsBatch(requestId, List.of(item)));
        assertEquals("QUANTITY_INVALID", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("RED→GREEN: 编辑草稿时明细应回填已有物料名称")
    void testGetItemsBackfillsMaterialNameForExistingMaterial() {
        MdMaterial material = new MdMaterial();
        material.setTenantId(TENANT_ID);
        material.setMaterialCode("PR-TDD-MAT-001");
        material.setMaterialName("TDD测试钢筋");
        material.setUnit("吨");
        material.setStatus("ENABLE");
        mdMaterialMapper.insert(material);

        MatPurchaseRequest request = new MatPurchaseRequest();
        request.setProjectId(PROJECT_ID);
        Long requestId = requestService.create(request);

        MatPurchaseRequestItem item = new MatPurchaseRequestItem();
        item.setMaterialId(material.getId());
        item.setQuantity(new BigDecimal("3.50"));
        item.setUnit("吨");
        item.setPlannedDate(LocalDate.of(2026, 7, 20));
        requestService.saveItemsBatch(requestId, List.of(item));

        List<MatPurchaseRequestItemVO> items = requestService.getItems(requestId);

        assertEquals(1, items.size(), "应回填1条明细");
        MatPurchaseRequestItemVO vo = items.get(0);
        assertEquals(String.valueOf(material.getId()), vo.getMaterialId());
        assertEquals("TDD测试钢筋", vo.getMaterialName(), "编辑弹窗需要物料名称回显");
        assertEquals(0, new BigDecimal("3.50").compareTo(new BigDecimal(vo.getQuantity())));
        assertEquals("吨", vo.getUnit());
        assertEquals("2026-07-20", vo.getPlannedDate());
    }

    @Test
    @Transactional
    @DisplayName("RED→GREEN: 编辑草稿重保存明细时应忽略前端回传的旧明细ID")
    void testResaveDraftItemsIgnoresClientItemIds() {
        MatPurchaseRequest request = new MatPurchaseRequest();
        request.setProjectId(PROJECT_ID);
        Long requestId = requestService.create(request);

        MatPurchaseRequestItem item = new MatPurchaseRequestItem();
        item.setMaterialId(1L);
        item.setQuantity(new BigDecimal("2.00"));
        item.setUnit("吨");
        requestService.saveItemsBatch(requestId, List.of(item));

        Long oldItemId = requestItemMapper.selectList(null).stream()
                .filter(i -> requestId.equals(i.getRequestId()))
                .findFirst()
                .orElseThrow()
                .getId();

        MatPurchaseRequestItem edited = new MatPurchaseRequestItem();
        edited.setId(oldItemId);
        edited.setRequestId(requestId);
        edited.setMaterialId(1L);
        edited.setQuantity(new BigDecimal("5.00"));
        edited.setUnit("吨");

        assertDoesNotThrow(() -> requestService.saveItemsBatch(requestId, List.of(edited)));

        List<MatPurchaseRequestItemVO> items = requestService.getItems(requestId);
        assertEquals(1, items.size(), "重保存后应只有1条有效明细");
        assertEquals(0, new BigDecimal("5.00").compareTo(new BigDecimal(items.get(0).getQuantity())));
        assertNotEquals(String.valueOf(oldItemId), items.get(0).getId(), "后端应重新生成明细ID");
    }

    // ═══════════════════════════════════════════════════════════
    // RED → GREEN: Submit for approval
    // ═══════════════════════════════════════════════════════════
    @Test
    @Transactional
    @DisplayName("RED→GREEN: 提交审批（需有明细和模板）")
    void testSubmitForApproval() {
        MatPurchaseRequest request = new MatPurchaseRequest();
        request.setProjectId(PROJECT_ID);
        Long requestId = requestService.create(request);

        // Add items (required for submit)
        MatPurchaseRequestItem item = new MatPurchaseRequestItem();
        item.setMaterialId(1L);
        item.setQuantity(new BigDecimal("100.00"));
        item.setUnit("m³");
        requestService.saveItemsBatch(requestId, List.of(item));

        // Submit
        requestService.submitForApproval(requestId);

        // Verify status changed
        MatPurchaseRequestVO vo = requestService.getById(requestId);
        assertEquals("APPROVING", vo.getApprovalStatus());
    }

    // ═══════════════════════════════════════════════════════════
    // RED → GREEN: Cannot submit without items
    // ═══════════════════════════════════════════════════════════
    @Test
    @Transactional
    @DisplayName("RED→GREEN: 无明细时提交审批应抛异常")
    void testSubmitWithoutItems() {
        MatPurchaseRequest request = new MatPurchaseRequest();
        request.setProjectId(PROJECT_ID);
        Long requestId = requestService.create(request);

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            requestService.submitForApproval(requestId);
        }, "无明细时应抛异常");
        assertEquals("PURCHASE_REQUEST_NO_ITEMS", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════
    // RED → GREEN: Cannot submit twice
    // ═══════════════════════════════════════════════════════════
    @Test
    @Transactional
    @DisplayName("RED→GREEN: 重复提交应抛异常")
    void testCannotSubmitTwice() {
        MatPurchaseRequest request = new MatPurchaseRequest();
        request.setProjectId(PROJECT_ID);
        Long requestId = requestService.create(request);

        MatPurchaseRequestItem item = new MatPurchaseRequestItem();
        item.setMaterialId(1L);
        item.setQuantity(new BigDecimal("100.00"));
        requestService.saveItemsBatch(requestId, List.of(item));

        requestService.submitForApproval(requestId);

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            requestService.submitForApproval(requestId);
        }, "重复提交应抛异常");
        assertEquals("PURCHASE_REQUEST_ALREADY_SUBMITTED", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════
    // RED → GREEN: Cannot edit when approving
    // ═══════════════════════════════════════════════════════════
    @Test
    @Transactional
    @DisplayName("RED→GREEN: 审批中不可编辑")
    void testCannotEditWhenApproving() {
        MatPurchaseRequest request = new MatPurchaseRequest();
        request.setProjectId(PROJECT_ID);
        Long requestId = requestService.create(request);

        // Manually set to APPROVING to test guard
        MatPurchaseRequest db = requestMapper.selectById(requestId);
        db.setApprovalStatus("APPROVING");
        requestMapper.updateById(db);

        MatPurchaseRequest update = new MatPurchaseRequest();
        update.setId(requestId);
        update.setRemark("尝试编辑");

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            requestService.update(update);
        }, "审批中不可编辑");
        assertEquals("REQUEST_IN_APPROVAL", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════
    // RED → GREEN: Cannot delete when approving
    // ═══════════════════════════════════════════════════════════
    @Test
    @Transactional
    @DisplayName("RED→GREEN: 审批中不可删除")
    void testCannotDeleteWhenApproving() {
        MatPurchaseRequest request = new MatPurchaseRequest();
        request.setProjectId(PROJECT_ID);
        Long requestId = requestService.create(request);

        // Manually set to APPROVING
        MatPurchaseRequest db = requestMapper.selectById(requestId);
        db.setApprovalStatus("APPROVING");
        requestMapper.updateById(db);

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            requestService.delete(requestId);
        }, "审批中不可删除");
        assertEquals("REQUEST_IN_APPROVAL", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════
    // RED → GREEN: convertToPurchaseOrder — 采购申请转采购订单
    // ═══════════════════════════════════════════════════════════
    @Test
    @Transactional
    @DisplayName("RED→GREEN: 采购申请转换为采购订单")
    void testConvertToPurchaseOrder() {
        MatPurchaseRequest request = new MatPurchaseRequest();
        request.setProjectId(PROJECT_ID);
        Long requestId = requestService.create(request);

        // Set status to APPROVED — convertToPurchaseOrder requires approved status
        MatPurchaseRequest db = requestMapper.selectById(requestId);
        db.setStatus("APPROVED");
        requestMapper.updateById(db);

        // Add items required for conversion
        MatPurchaseRequestItem item = new MatPurchaseRequestItem();
        item.setMaterialId(1L);
        item.setQuantity(new BigDecimal("100.00"));
        item.setUnit("m³");
        requestService.saveItemsBatch(requestId, java.util.List.of(item));

        requestService.convertToPurchaseOrder(requestId);

        MatPurchaseRequest converted = requestMapper.selectById(requestId);
        assertEquals("CONVERTED", converted.getStatus());

        MatPurchaseOrder order = orderMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MatPurchaseOrder>()
                .eq(MatPurchaseOrder::getRequestId, requestId)
                .eq(MatPurchaseOrder::getTenantId, TENANT_ID));
        assertNotNull(order, "转换后应生成采购订单");
        assertEquals(PROJECT_ID, order.getProjectId());
        assertEquals("DRAFT", order.getApprovalStatus(), "需求审批不得替代采购订单审批");
        assertEquals("DRAFT", order.getOrderStatus(), "转单后应等待采购人员补齐商业条件");
        assertEquals(0, BigDecimal.ZERO.compareTo(order.getTotalAmount()), "未定价订单初始金额应为 0");

        List<com.cgcpms.purchase.entity.MatPurchaseOrderItem> convertedItems = orderItemMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.cgcpms.purchase.entity.MatPurchaseOrderItem>()
                        .eq(com.cgcpms.purchase.entity.MatPurchaseOrderItem::getOrderId, order.getId()));
        assertEquals(1, convertedItems.size());
        assertEquals(item.getId(), convertedItems.get(0).getRequestItemId(), "订单明细必须保留采购申请明细来源");
        assertEquals(0, BigDecimal.ZERO.compareTo(convertedItems.get(0).getUnitPrice()));
        assertEquals(0, BigDecimal.ZERO.compareTo(convertedItems.get(0).getAmount()));
    }

    // ═══════════════════════════════════════════════════════════
    // REFACTOR: VO returns String IDs
    // ═══════════════════════════════════════════════════════════
    @Test
    @Transactional
    @DisplayName("REFACTOR: VO 返回 String 类型的 ID 字段")
    void testVoReturnsStringIds() {
        MatPurchaseRequest request = new MatPurchaseRequest();
        request.setProjectId(PROJECT_ID);
        Long id = requestService.create(request);

        MatPurchaseRequestVO vo = requestService.getById(id);
        assertNotNull(vo.getId(), "VO id 不应为空");
        assertNotNull(vo.getTenantId(), "VO tenantId 不应为空");
        assertNotNull(vo.getProjectId(), "VO projectId 不应为空");
        assertNotNull(vo.getCreatedTime(), "VO createdTime 不应为空");

        // All IDs should be valid Long-parseable strings
        Long.parseLong(vo.getId());
        Long.parseLong(vo.getTenantId());
        Long.parseLong(vo.getProjectId());
    }

    // ═══════════════════════════════════════════════════════════
    // REFACTOR: Audit fields populated
    // ═══════════════════════════════════════════════════════════
    @Test
    @Transactional
    @DisplayName("REFACTOR: 审计字段自动填充")
    void testAuditFieldsPopulated() {
        MatPurchaseRequest request = new MatPurchaseRequest();
        request.setProjectId(PROJECT_ID);
        Long id = requestService.create(request);

        MatPurchaseRequestVO vo = requestService.getById(id);
        assertNotNull(vo.getCreatedBy(), "createdBy 应由 MetaObjectHandler 填充");
        assertEquals(String.valueOf(USER_ADMIN), vo.getCreatedBy());
        assertNotNull(vo.getCreatedTime(), "createdTime 应由 MetaObjectHandler 填充");
        assertNotNull(vo.getUpdatedTime(), "updatedTime 应由 MetaObjectHandler 填充");
    }
}
