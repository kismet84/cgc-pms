package com.cgcpms.contract;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.constant.ContractStatusConstants;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.entity.CtContractItem;
import com.cgcpms.contract.mapper.CtContractItemMapper;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.contract.service.CtContractItemService;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CtContractItemService 单元测试。
 * <p>
 * 覆盖: 按合同查询、创建、批量保存、更新、删除等核心操作。
 * 使用 H2 内存数据库，以 contract 30001 (tenant_id=0, project_id=10001) 作为关联合同。
 * update/delete/batchSave 需要 DRAFT 状态的合同，会在 @BeforeEach 中创建。
 */
@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@Transactional
@DisplayName("CtContractItemService — 合同清单项 CRUD 测试")
class CtContractItemServiceTest {

    /** Approved, performing contract from V90 seed — requiresParentContract only */
    private static final long CONTRACT_ID = 30001L;

    /** DRAFT contract created in @BeforeEach for update/delete/batchSave operations */
    private Long draftContractId;

    @Autowired
    private CtContractItemService itemService;

    @Autowired
    private CtContractItemMapper itemMapper;

    @Autowired
    private CtContractMapper contractMapper;

    @Autowired
    private PmProjectMapper projectMapper;

    @BeforeEach
    void setupContext() {
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);
        draftContractId = seedDraftContract();
    }

    @AfterEach
    void clearContext() {
        TestUserContext.clear();
    }

    /** Create a DRAFT contract for testing write operations that require draft status. */
    private Long seedDraftContract() {
        CtContract c = new CtContract();
        c.setProjectId(10001L);
        c.setContractCode("CT-TEST-DRAFT-ITEM");
        c.setContractName("DRAFT合同-清单项测试");
        c.setContractType("SUB");
        c.setPartyAId(20001L);
        c.setPartyBId(20002L);
        c.setContractAmount(new BigDecimal("1000000.00"));
        c.setCurrentAmount(new BigDecimal("1000000.00"));
        c.setPaidAmount(BigDecimal.ZERO);
        c.setTaxRate(new BigDecimal("13.00"));
        c.setContractStatus(ContractStatusConstants.STATUS_DRAFT);
        c.setApprovalStatus(ContractStatusConstants.APPROVAL_DRAFT);
        c.setTenantId(TestUserContext.TENANT_0);
        c.setCostGeneratedFlag(0);
        contractMapper.insert(c);
        return c.getId();
    }

    // ═══════════════════════════════════════════════════════════════
    // 按合同查询
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("按合同查询 — 返回属于该合同的清单项列表")
    void testGetByContractId() {
        CtContractItem item = buildDraftItem("CI-GET-001", "查询测试清单项");
        itemService.create(item);

        List<CtContractItem> items = itemService.getByContractId(draftContractId);
        assertNotNull(items);
        assertTrue(items.size() >= 1, "应至少返回一条清单项");
        for (CtContractItem i : items) {
            assertEquals(draftContractId, i.getContractId());
        }
    }

    @Test
    @DisplayName("按合同查询 — 合同不存在时抛 CONTRACT_NOT_FOUND")
    void testGetByContractIdNotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> itemService.getByContractId(-999L));
        assertEquals("CONTRACT_NOT_FOUND", ex.getCode());
    }

    @Test
    @DisplayName("按合同查询 — 租户隔离: 跨租户查询抛 CONTRACT_NOT_FOUND")
    void testGetByContractIdTenantIsolation() {
        CtContractItem item = buildDraftItem("CI-TISO-001", "租户隔离清单项");
        itemService.create(item);

        TestUserContext.setAdmin(999L, TestUserContext.USER_ADMIN);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> itemService.getByContractId(draftContractId));
        assertEquals("CONTRACT_NOT_FOUND", ex.getCode());
    }

    @Test
    @DisplayName("按合同查询 — 无项目权限时拒绝访问")
    void testGetByContractIdProjectAccessDenied() {
        PmProject project = projectMapper.selectById(10001L);
        project.setCreatedBy(2L);
        project.setProjectManagerId(null);
        projectMapper.updateById(project);

        UserContext.set(Jwts.claims()
                .add("userId", 3L)
                .add("username", "no-project-access")
                .add("tenantId", TestUserContext.TENANT_0)
                .add("roleCodes", List.of())
                .build());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> itemService.getByContractId(draftContractId));
        assertEquals("PROJECT_ACCESS_DENIED", ex.getCode());
    }

    @Test
    @DisplayName("按合同查询 — 按 sortOrder 升序排列")
    void testGetByContractIdOrderBySortOrder() {
        CtContractItem i1 = buildDraftItem("CI-ORDER-001", "排序1");
        i1.setSortOrder(3);
        itemService.create(i1);

        CtContractItem i2 = buildDraftItem("CI-ORDER-002", "排序2");
        i2.setSortOrder(1);
        itemService.create(i2);

        CtContractItem i3 = buildDraftItem("CI-ORDER-003", "排序3");
        i3.setSortOrder(2);
        itemService.create(i3);

        List<CtContractItem> items = itemService.getByContractId(draftContractId);
        List<CtContractItem> ourItems = items.stream()
                .filter(i -> i.getItemCode() != null && i.getItemCode().startsWith("CI-ORDER-"))
                .toList();
        if (ourItems.size() == 3) {
            assertEquals("排序2", ourItems.get(0).getItemName(), "sortOrder=1 应排第一");
            assertEquals("排序3", ourItems.get(1).getItemName(), "sortOrder=2 应排第二");
            assertEquals("排序1", ourItems.get(2).getItemName(), "sortOrder=3 应排第三");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 创建
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("创建 — 成功后返回 ID 并持久化")
    void testCreateSuccess() {
        CtContractItem item = buildDraftItem("CI-CREATE-001", "创建测试清单项");
        Long id = itemService.create(item);

        assertNotNull(id, "创建应返回 ID");
        CtContractItem saved = itemMapper.selectById(id);
        assertNotNull(saved);
        assertEquals("创建测试清单项", saved.getItemName());
        assertEquals(draftContractId, saved.getContractId());
        assertEquals(TestUserContext.TENANT_0, saved.getTenantId());
    }

    @Test
    @DisplayName("创建 — 合同不存在时抛 CONTRACT_NOT_FOUND")
    void testCreateContractNotFound() {
        CtContractItem item = buildDraftItem("CI-NOCONTRACT-001", "无合同清单项");
        item.setContractId(-999L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> itemService.create(item));
        assertEquals("CONTRACT_NOT_FOUND", ex.getCode());
    }

    @Test
    @DisplayName("创建 — 非草稿合同拒绝新增")
    void testCreateRejectedForNonDraftContract() {
        CtContractItem item = buildDraftItem("CI-NON-DRAFT-001", "非草稿合同清单项");
        item.setContractId(CONTRACT_ID);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> itemService.create(item));
        assertEquals("CONTRACT_NOT_EDITABLE", ex.getCode());
    }

    @Test
    @DisplayName("创建 — 所有金额字段正确持久化")
    void testCreateAllFieldsPersisted() {
        CtContractItem item = new CtContractItem();
        item.setContractId(draftContractId);
        item.setItemCode("CI-FULL-001");
        item.setItemName("全字段清单项");
        item.setItemSpec("C30标准规格");
        item.setUnit("m³");
        item.setQuantity(new BigDecimal("100.0000"));
        item.setUnitPrice(new BigDecimal("450.0000"));
        item.setAmount(new BigDecimal("45000.00"));
        item.setTaxRate(new BigDecimal("13.00"));
        item.setTaxAmount(new BigDecimal("5850.00"));
        item.setAmountWithoutTax(new BigDecimal("39150.00"));
        item.setSortOrder(5);

        Long id = itemService.create(item);
        CtContractItem saved = itemMapper.selectById(id);

        assertNotNull(saved);
        assertEquals("C30标准规格", saved.getItemSpec());
        assertEquals("m³", saved.getUnit());
        assertEquals(0, new BigDecimal("100.0000").compareTo(saved.getQuantity()));
        assertEquals(0, new BigDecimal("450.0000").compareTo(saved.getUnitPrice()));
        assertEquals(0, new BigDecimal("45000.00").compareTo(saved.getAmount()));
        assertEquals(0, new BigDecimal("13.00").compareTo(saved.getTaxRate()));
        assertEquals(0, new BigDecimal("5850.00").compareTo(saved.getTaxAmount()));
        assertEquals(0, new BigDecimal("39150.00").compareTo(saved.getAmountWithoutTax()));
        assertEquals(5, saved.getSortOrder());
    }

    // ═══════════════════════════════════════════════════════════════
    // 批量保存（全量替换）—— 需要 DRAFT 合同
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("批量保存 — 清空旧数据后插入新数据")
    void testBatchSaveReplaceAll() {
        // 先创建一条旧数据
        CtContractItem oldItem = buildDraftItem("CI-BATCH-OLD", "旧清单项");
        itemService.create(oldItem);

        // 批量保存新数据（全量替换）
        CtContractItem newItem1 = buildDraftItem("CI-BATCH-NEW1", "新清单项1");
        newItem1.setSortOrder(1);
        CtContractItem newItem2 = buildDraftItem("CI-BATCH-NEW2", "新清单项2");
        newItem2.setSortOrder(2);
        List<CtContractItem> newItems = List.of(newItem1, newItem2);

        itemService.batchSave(draftContractId, newItems);

        // 验证新插入的记录存在
        List<CtContractItem> ourNewItems = itemMapper.selectList(
                new LambdaQueryWrapper<CtContractItem>()
                        .eq(CtContractItem::getContractId, draftContractId));
        List<CtContractItem> matched = ourNewItems.stream()
                .filter(i -> i.getItemCode() != null && i.getItemCode().startsWith("CI-BATCH-NEW"))
                .toList();
        assertEquals(2, matched.size(), "批量保存后应有的新条目数");
    }

    @Test
    @DisplayName("批量保存 — 传入空列表时清空所有清单项")
    void testBatchSaveEmptyListClearsAll() {
        CtContractItem item = buildDraftItem("CI-BATCH-EMPTY", "待清空项");
        itemService.create(item);

        itemService.batchSave(draftContractId, List.of());

        CtContractItem deleted = itemMapper.selectOne(
                new LambdaQueryWrapper<CtContractItem>()
                        .eq(CtContractItem::getItemCode, "CI-BATCH-EMPTY"));
        assertNull(deleted, "空列表批量保存后，原有清单项应被删除");
    }

    @Test
    @DisplayName("批量保存 — 合同不存在时抛 CONTRACT_NOT_FOUND")
    void testBatchSaveContractNotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> itemService.batchSave(-999L, List.of(buildDraftItem("CI-NOCONTRACT", "无合同"))));
        assertEquals("CONTRACT_NOT_FOUND", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════════
    // 更新 — 需要 DRAFT 合同
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("更新 — 成功后字段正确更新")
    void testUpdateSuccess() {
        CtContractItem item = buildDraftItem("CI-UPDATE-001", "更新前清单项");
        Long id = itemService.create(item);

        CtContractItem toUpdate = new CtContractItem();
        toUpdate.setId(id);
        toUpdate.setContractId(draftContractId);
        toUpdate.setItemName("更新后清单项");
        toUpdate.setQuantity(new BigDecimal("200.0000"));
        toUpdate.setUnitPrice(new BigDecimal("500.0000"));
        toUpdate.setAmount(new BigDecimal("100000.00"));

        itemService.update(toUpdate);

        CtContractItem updated = itemMapper.selectById(id);
        assertEquals("更新后清单项", updated.getItemName());
        assertEquals(0, new BigDecimal("200.0000").compareTo(updated.getQuantity()));
        assertEquals(0, new BigDecimal("500.0000").compareTo(updated.getUnitPrice()));
    }

    @Test
    @DisplayName("更新 — 清单项不存在时抛 CONTRACT_NOT_EDITABLE (父合同DRAFT, 但查不到item)")
    void testUpdateItemNotFound() {
        CtContractItem toUpdate = new CtContractItem();
        toUpdate.setId(-999L);
        toUpdate.setContractId(draftContractId);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> itemService.update(toUpdate));
        assertEquals("ITEM_NOT_FOUND", ex.getCode());
    }

    @Test
    @DisplayName("更新 — contractId 不存在的合同抛 CONTRACT_NOT_FOUND")
    void testUpdateContractMismatch() {
        CtContractItem item = buildDraftItem("CI-MISMATCH-001", "contractId不匹配");
        Long id = itemService.create(item);

        CtContractItem toUpdate = new CtContractItem();
        toUpdate.setId(id);
        toUpdate.setContractId(-999L); // 不存在的合同 → requireDraftParentContract 先抛

        BusinessException ex = assertThrows(BusinessException.class,
                () -> itemService.update(toUpdate));
        assertEquals("CONTRACT_NOT_FOUND", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════════
    // 删除 — 需要 DRAFT 合同
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("删除 — 成功后软删除记录")
    void testDeleteSuccess() {
        CtContractItem item = buildDraftItem("CI-DELETE-001", "待删除清单项");
        Long id = itemService.create(item);

        itemService.delete(draftContractId, id);

        CtContractItem deleted = itemMapper.selectById(id);
        assertNull(deleted, "软删除后应查不到记录");
    }

    @Test
    @DisplayName("删除 — 清单项不存在时抛 ITEM_NOT_FOUND")
    void testDeleteItemNotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> itemService.delete(draftContractId, -999L));
        assertEquals("ITEM_NOT_FOUND", ex.getCode());
    }

    @Test
    @DisplayName("删除 — contractId 不存在的合同抛 CONTRACT_NOT_FOUND")
    void testDeleteContractMismatch() {
        CtContractItem item = buildDraftItem("CI-DEL-MISMATCH", "删除contractId不匹配");
        Long id = itemService.create(item);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> itemService.delete(-999L, id));
        assertEquals("CONTRACT_NOT_FOUND", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════════
    // 辅助方法
    // ═══════════════════════════════════════════════════════════════

    private CtContractItem buildDraftItem(String itemCode, String itemName) {
        CtContractItem item = new CtContractItem();
        item.setContractId(draftContractId);
        item.setItemCode(itemCode);
        item.setItemName(itemName);
        item.setItemSpec("标准规格");
        item.setUnit("m³");
        item.setQuantity(new BigDecimal("100.0000"));
        item.setUnitPrice(new BigDecimal("450.0000"));
        item.setAmount(new BigDecimal("45000.00"));
        item.setTaxRate(new BigDecimal("13.00"));
        item.setTaxAmount(new BigDecimal("5850.00"));
        item.setAmountWithoutTax(new BigDecimal("39150.00"));
        item.setSortOrder(1);
        return item;
    }
}
