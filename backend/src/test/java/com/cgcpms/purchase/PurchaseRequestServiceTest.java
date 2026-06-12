package com.cgcpms.purchase;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.purchase.entity.MatPurchaseRequest;
import com.cgcpms.purchase.entity.MatPurchaseRequestItem;
import com.cgcpms.purchase.mapper.MatPurchaseRequestItemMapper;
import com.cgcpms.purchase.mapper.MatPurchaseRequestMapper;
import com.cgcpms.purchase.service.MatPurchaseRequestService;
import com.cgcpms.purchase.vo.MatPurchaseRequestItemVO;
import com.cgcpms.purchase.vo.MatPurchaseRequestVO;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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

    @BeforeEach
    void setupContext() {
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", TENANT_ID)
                .build());
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
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
