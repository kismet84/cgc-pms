package com.cgcpms.inventory;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.inventory.entity.MatStock;
import com.cgcpms.inventory.entity.MatWarehouse;
import com.cgcpms.inventory.mapper.MatStockMapper;
import com.cgcpms.inventory.service.MatWarehouseService;
import com.cgcpms.inventory.vo.MatWarehouseVO;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("local")
@DisplayName("仓库服务 TDD 测试")
class WarehouseServiceTest {

    private static final long USER_ADMIN = 1L;
    private static final long TENANT_ID = 0L;
    private static final long PROJECT_ID = 98100100L;
    private static final long SECOND_PROJECT_ID = 98100200L;

    @Autowired
    private MatWarehouseService warehouseService;

    @Autowired
    private MatStockMapper stockMapper;

    @Autowired
    private PmProjectMapper projectMapper;

    @BeforeEach
    void setupContext() {
        PmProject project = new PmProject();
        project.setId(PROJECT_ID);
        project.setTenantId(TENANT_ID);
        project.setProjectCode("P-WH-100");
        project.setProjectName("仓库测试项目");
        project.setStatus("ACTIVE");
        projectMapper.insertOrUpdate(project);
        PmProject secondProject = new PmProject();
        secondProject.setId(SECOND_PROJECT_ID);
        secondProject.setTenantId(TENANT_ID);
        secondProject.setProjectCode("P-WH-200");
        secondProject.setProjectName("仓库测试项目二");
        secondProject.setStatus("ACTIVE");
        projectMapper.insertOrUpdate(secondProject);
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", TENANT_ID)
                .add("roleCodes", java.util.List.of("ADMIN"))
                .build());
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    // ═══════════════════════════════════════════════════════════
    // RED → GREEN → REFACTOR: Create
    // ═══════════════════════════════════════════════════════════
    @Test
    @Transactional
    @DisplayName("RED→GREEN: 创建仓库，返回雪花ID")
    void testCreateWarehouse() {
        MatWarehouse warehouse = new MatWarehouse();
        warehouse.setProjectId(PROJECT_ID);
        warehouse.setWarehouseCode("WH-001");
        warehouse.setWarehouseName("一号仓库");
        warehouse.setStatus("ENABLE");

        Long id = warehouseService.create(warehouse);
        assertNotNull(id, "创建应返回雪花ID");

        // GREEN: verify persisted
        MatWarehouseVO vo = warehouseService.getById(id);
        assertTrue(vo.getWarehouseCode().matches("WH-\\d{8}-\\d{3}"));
        assertEquals("一号仓库", vo.getWarehouseName());
        assertEquals("ENABLE", vo.getStatus());
        assertEquals(String.valueOf(PROJECT_ID), vo.getProjectId());
        assertEquals("仓库测试项目", vo.getProjectName());
        assertEquals(String.valueOf(TENANT_ID), vo.getTenantId());

    }

    // ═══════════════════════════════════════════════════════════
    // RED → GREEN: Page query with project filter
    // ═══════════════════════════════════════════════════════════
    @Test
    @Transactional
    @DisplayName("RED→GREEN: 分页查询带项目过滤")
    void testPageQueryWithProjectFilter() {
        // Create warehouse for PROJECT_ID
        MatWarehouse w1 = new MatWarehouse();
        w1.setProjectId(PROJECT_ID);
        w1.setWarehouseCode("WH-P100-A");
        w1.setWarehouseName("项目100仓库A");
        w1.setStatus("ENABLE");
        warehouseService.create(w1);

        MatWarehouse w2 = new MatWarehouse();
        w2.setProjectId(SECOND_PROJECT_ID);
        w2.setWarehouseCode("WH-P200-A");
        w2.setWarehouseName("项目200仓库A");
        w2.setStatus("ENABLE");
        warehouseService.create(w2);

        // Query with project filter
        PageResult<MatWarehouseVO> page1 = warehouseService.getPage(1, 20, PROJECT_ID, null, null, null);
        assertEquals(1, page1.getTotal(), "项目100应只有1个仓库");
        assertTrue(page1.getRecords().get(0).getWarehouseCode().startsWith("WH-"));

        // Query without project filter (all tenant warehouses)
        PageResult<MatWarehouseVO> page2 = warehouseService.getPage(1, 20, null, "WH-", null, null);
        assertTrue(page2.getTotal() >= 2, "应返回本次创建的两个仓库");
        assertTrue(page2.getRecords().stream().anyMatch(row -> "项目100仓库A".equals(row.getWarehouseName())));
        assertTrue(page2.getRecords().stream().anyMatch(row -> "项目200仓库A".equals(row.getWarehouseName())));
    }

    @Test
    @Transactional
    @DisplayName("仓库列表对无项目访问权用户 fail-close")
    void testPageQueryFailsClosedWithoutProjectAccess() {
        MatWarehouse warehouse = new MatWarehouse();
        warehouse.setProjectId(PROJECT_ID);
        warehouse.setWarehouseCode("WH-SCOPED");
        warehouse.setWarehouseName("范围仓库");
        warehouse.setStatus("ENABLE");
        warehouseService.create(warehouse);

        UserContext.set(Jwts.claims()
                .add("userId", 99999L)
                .add("username", "no-project-access")
                .add("tenantId", TENANT_ID)
                .build());

        assertEquals(0, warehouseService.getPage(1, 20, null, null, null, null).getTotal());
    }

    // ═══════════════════════════════════════════════════════════
    // RED → GREEN: Status filter
    // ═══════════════════════════════════════════════════════════
    @Test
    @Transactional
    @DisplayName("RED→GREEN: 按状态筛选仓库")
    void testStatusFilter() {
        MatWarehouse w1 = new MatWarehouse();
        w1.setProjectId(PROJECT_ID);
        w1.setWarehouseCode("WH-ENB");
        w1.setWarehouseName("启用仓库");
        w1.setStatus("ENABLE");
        warehouseService.create(w1);

        MatWarehouse w2 = new MatWarehouse();
        w2.setProjectId(PROJECT_ID);
        w2.setWarehouseCode("WH-DIS");
        w2.setWarehouseName("禁用仓库");
        w2.setStatus("DISABLE");
        warehouseService.create(w2);

        PageResult<MatWarehouseVO> enabled = warehouseService.getPage(1, 20, null, null, "启用仓库", "ENABLE");
        assertEquals(1, enabled.getTotal(), "应只有1个启用仓库");
        assertEquals("启用仓库", enabled.getRecords().get(0).getWarehouseName());

        PageResult<MatWarehouseVO> disabled = warehouseService.getPage(1, 20, null, null, "禁用仓库", "DISABLE");
        assertEquals(1, disabled.getTotal(), "应只有1个禁用仓库");
        assertEquals("禁用仓库", disabled.getRecords().get(0).getWarehouseName());
    }

    // ═══════════════════════════════════════════════════════════
    // RED → GREEN: Update warehouse
    // ═══════════════════════════════════════════════════════════
    @Test
    @Transactional
    @DisplayName("RED→GREEN: 更新仓库信息")
    void testUpdateWarehouse() {
        MatWarehouse warehouse = new MatWarehouse();
        warehouse.setProjectId(PROJECT_ID);
        warehouse.setWarehouseCode("WH-UPD-001");
        warehouse.setWarehouseName("待更新仓库");
        warehouse.setStatus("ENABLE");
        Long id = warehouseService.create(warehouse);
        String generatedCode = warehouseService.getById(id).getWarehouseCode();

        MatWarehouse update = new MatWarehouse();
        update.setId(id);
        update.setTenantId(999L);
        update.setProjectId(SECOND_PROJECT_ID);
        update.setWarehouseCode("WH-UPD-002");
        update.setWarehouseName("已更新仓库");
        update.setStatus("DISABLE");
        warehouseService.update(update);

        MatWarehouseVO vo = warehouseService.getById(id);
        assertEquals(generatedCode, vo.getWarehouseCode());
        assertEquals(String.valueOf(TENANT_ID), vo.getTenantId());
        assertEquals(String.valueOf(PROJECT_ID), vo.getProjectId());
        assertEquals("已更新仓库", vo.getWarehouseName());
        assertEquals("DISABLE", vo.getStatus());
    }

    @Test
    @Transactional
    @DisplayName("仓库详情和写入口对无项目访问权用户 fail-close")
    void testWarehouseEntriesFailClosedWithoutProjectAccess() {
        MatWarehouse warehouse = new MatWarehouse();
        warehouse.setProjectId(PROJECT_ID);
        warehouse.setWarehouseName("项目范围仓库");
        warehouse.setStatus("ENABLE");
        Long id = warehouseService.create(warehouse);

        UserContext.set(Jwts.claims()
                .add("userId", 99999L)
                .add("username", "no-project-access")
                .add("tenantId", TENANT_ID)
                .build());

        MatWarehouse create = new MatWarehouse();
        create.setProjectId(PROJECT_ID);
        create.setWarehouseName("越权创建");
        create.setStatus("ENABLE");
        assertEquals("PROJECT_ACCESS_DENIED",
                assertThrows(BusinessException.class, () -> warehouseService.create(create)).getCode());
        assertEquals("PROJECT_ACCESS_DENIED",
                assertThrows(BusinessException.class, () -> warehouseService.getById(id)).getCode());

        MatWarehouse update = new MatWarehouse();
        update.setId(id);
        update.setWarehouseName("越权更新");
        update.setStatus("DISABLE");
        assertEquals("PROJECT_ACCESS_DENIED",
                assertThrows(BusinessException.class, () -> warehouseService.update(update)).getCode());
        assertEquals("PROJECT_ACCESS_DENIED",
                assertThrows(BusinessException.class, () -> warehouseService.updateStatus(id, "DISABLE")).getCode());
        assertEquals("PROJECT_ACCESS_DENIED",
                assertThrows(BusinessException.class, () -> warehouseService.delete(id)).getCode());
    }

    // ═══════════════════════════════════════════════════════════
    // RED → GREEN: Update status only
    // ═══════════════════════════════════════════════════════════
    @Test
    @Transactional
    @DisplayName("RED→GREEN: 仅更新仓库状态")
    void testUpdateStatus() {
        MatWarehouse warehouse = new MatWarehouse();
        warehouse.setProjectId(PROJECT_ID);
        warehouse.setWarehouseCode("WH-STAT");
        warehouse.setWarehouseName("状态切换仓库");
        warehouse.setStatus("ENABLE");
        Long id = warehouseService.create(warehouse);

        warehouseService.updateStatus(id, "DISABLE");
        MatWarehouseVO vo = warehouseService.getById(id);
        assertEquals("DISABLE", vo.getStatus());

        warehouseService.updateStatus(id, "ENABLE");
        vo = warehouseService.getById(id);
        assertEquals("ENABLE", vo.getStatus());
    }

    // ═══════════════════════════════════════════════════════════
    // RED → GREEN: GetById throws on wrong tenant
    // ═══════════════════════════════════════════════════════════
    @Test
    @Transactional
    @DisplayName("RED→GREEN: 跨租户访问应抛异常")
    void testCrossTenantAccessDenied() {
        // Create with tenant 0
        MatWarehouse warehouse = new MatWarehouse();
        warehouse.setProjectId(PROJECT_ID);
        warehouse.setWarehouseCode("WH-TENANT");
        warehouse.setWarehouseName("租户隔离仓库");
        warehouse.setStatus("ENABLE");
        Long id = warehouseService.create(warehouse);

        // Switch to tenant 999
        UserContext.clear();
        UserContext.set(Jwts.claims()
                .add("userId", 999L)
                .add("username", "other")
                .add("tenantId", 999L)
                .build());

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            warehouseService.getById(id);
        }, "跨租户访问应抛 BusinessException");
        assertEquals("WAREHOUSE_NOT_FOUND", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════
    // RED → GREEN: Not found throws BusinessException
    // ═══════════════════════════════════════════════════════════
    @Test
    @Transactional
    @DisplayName("RED→GREEN: 查询不存在的仓库应抛异常")
    void testGetByNonExistentId() {
        BusinessException ex = assertThrows(BusinessException.class, () -> {
            warehouseService.getById(99999999L);
        }, "查询不存在的仓库应抛 BusinessException");
        assertEquals("WAREHOUSE_NOT_FOUND", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════
    // RED → GREEN: VO returns String IDs
    // ═══════════════════════════════════════════════════════════
    @Test
    @Transactional
    @DisplayName("RED→GREEN: VO 返回 String 类型的 ID 字段")
    void testVoReturnsStringIds() {
        MatWarehouse warehouse = new MatWarehouse();
        warehouse.setProjectId(PROJECT_ID);
        warehouse.setWarehouseCode("WH-VO");
        warehouse.setWarehouseName("VO测试仓库");
        warehouse.setStatus("ENABLE");
        Long id = warehouseService.create(warehouse);

        MatWarehouseVO vo = warehouseService.getById(id);
        assertNotNull(vo.getId(), "VO id 不应为空");
        assertNotNull(vo.getTenantId(), "VO tenantId 不应为空");
        assertNotNull(vo.getProjectId(), "VO projectId 不应为空");
        assertNotNull(vo.getCreatedAt(), "VO createdAt 不应为空");
        assertNotNull(vo.getUpdatedAt(), "VO updatedAt 不应为空");

        // All IDs should be valid Long-parseable strings
        Long.parseLong(vo.getId());
        Long.parseLong(vo.getTenantId());
        Long.parseLong(vo.getProjectId());
    }

    // ═══════════════════════════════════════════════════════════
    // RED → GREEN: Code/Name fuzzy search
    // ═══════════════════════════════════════════════════════════
    @Test
    @Transactional
    @DisplayName("RED→GREEN: 按编码/名称模糊搜索")
    void testFuzzySearch() {
        MatWarehouse w1 = new MatWarehouse();
        w1.setProjectId(PROJECT_ID);
        w1.setWarehouseCode("MAIN-WH-01");
        w1.setWarehouseName("主仓库一号");
        w1.setStatus("ENABLE");
        warehouseService.create(w1);

        MatWarehouse w2 = new MatWarehouse();
        w2.setProjectId(PROJECT_ID);
        w2.setWarehouseCode("SUB-WH-02");
        w2.setWarehouseName("副仓库二号");
        w2.setStatus("ENABLE");
        warehouseService.create(w2);

        // Search by generated code
        String generatedCode = warehouseService.getById(w1.getId()).getWarehouseCode();
        PageResult<MatWarehouseVO> byCode = warehouseService.getPage(1, 20, null, generatedCode, null, null);
        assertEquals(1, byCode.getTotal());
        assertEquals("主仓库一号", byCode.getRecords().get(0).getWarehouseName());

        // Search by name fragment
        PageResult<MatWarehouseVO> byName = warehouseService.getPage(1, 20, null, null, "副", null);
        assertEquals(1, byName.getTotal());
        assertEquals("副仓库二号", byName.getRecords().get(0).getWarehouseName());
    }

    // ═══════════════════════════════════════════════════════════
    // REFACTOR: audit fields populated
    // ═══════════════════════════════════════════════════════════
    @Test
    @Transactional
    @DisplayName("REFACTOR: 审计字段自动填充")
    void testAuditFieldsPopulated() {
        MatWarehouse warehouse = new MatWarehouse();
        warehouse.setProjectId(PROJECT_ID);
        warehouse.setWarehouseCode("WH-AUDIT");
        warehouse.setWarehouseName("审计字段测试");
        warehouse.setStatus("ENABLE");
        Long id = warehouseService.create(warehouse);

        MatWarehouseVO vo = warehouseService.getById(id);
        assertNotNull(vo.getCreatedBy(), "createdBy 应由 MetaObjectHandler 填充");
        assertEquals(String.valueOf(USER_ADMIN), vo.getCreatedBy());
        assertNotNull(vo.getCreatedAt(), "createdAt 应由 MetaObjectHandler 填充");
        assertNotNull(vo.getUpdatedAt(), "updatedAt 应由 MetaObjectHandler 填充");
    }

    // ═══════════════════════════════════════════════════════════
    // EDGE: 服务层单元测试可隔离构造同编码仓库；数据库唯一约束由 Flyway 兼容测试验证
    // ═══════════════════════════════════════════════════════════
    @Test
    @Transactional
    @DisplayName("EDGE: 客户端重复编码被忽略并生成不同编号")
    void testClientWarehouseCodeIgnored() {
        MatWarehouse w1 = new MatWarehouse();
        w1.setProjectId(PROJECT_ID);
        w1.setWarehouseCode("WH-DUP");
        w1.setWarehouseName("仓库A");
        w1.setStatus("ENABLE");
        Long id1 = warehouseService.create(w1);
        assertNotNull(id1);

        MatWarehouse w2 = new MatWarehouse();
        w2.setProjectId(PROJECT_ID);
        w2.setWarehouseCode("WH-DUP");
        w2.setWarehouseName("仓库B");
        w2.setStatus("ENABLE");

        Long id2 = warehouseService.create(w2);
        assertNotEquals(warehouseService.getById(id1).getWarehouseCode(),
                warehouseService.getById(id2).getWarehouseCode());
    }

    // ═══════════════════════════════════════════════════════════
    // EDGE: 不同租户边界测试已在 crossTenant 覆盖，添加空仓库名创建
    // ═══════════════════════════════════════════════════════════
    @Test
    @Transactional
    @DisplayName("EDGE: 创建空名称仓库应成功")
    void testCreateWarehouseWithEmptyName() {
        MatWarehouse warehouse = new MatWarehouse();
        warehouse.setProjectId(PROJECT_ID);
        warehouse.setWarehouseCode("WH-EMPTY-NAME");
        warehouse.setWarehouseName("");
        warehouse.setStatus("ENABLE");
        Long id = warehouseService.create(warehouse);
        assertNotNull(id, "空名称仓库仍应创建成功");

        MatWarehouseVO vo = warehouseService.getById(id);
        assertEquals("", vo.getWarehouseName(), "仓库名称应为空字符串");
        assertTrue(vo.getWarehouseCode().matches("WH-\\d{8}-\\d{3}"));
    }

    // ═══════════════════════════════════════════════════════════
    // RED → GREEN: Delete warehouse — no stock → success
    // ═══════════════════════════════════════════════════════════
    @Test
    @Transactional
    @DisplayName("RED→GREEN: 删除无库存仓库，软删除成功")
    void testDeleteWarehouseWithNoStock() {
        MatWarehouse warehouse = new MatWarehouse();
        warehouse.setProjectId(PROJECT_ID);
        warehouse.setWarehouseCode("WH-DEL-001");
        warehouse.setWarehouseName("待删除仓库");
        warehouse.setStatus("ENABLE");
        Long id = warehouseService.create(warehouse);

        // Verify exists before delete
        assertNotNull(warehouseService.getById(id));

        // Delete
        warehouseService.delete(id);

        // Verify soft-deleted: getById should throw WAREHOUSE_NOT_FOUND
        BusinessException ex = assertThrows(BusinessException.class, () -> {
            warehouseService.getById(id);
        }, "软删除后查询应抛 BusinessException");
        assertEquals("WAREHOUSE_NOT_FOUND", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════
    // RED → GREEN: Delete warehouse — has stock → 400
    // ═══════════════════════════════════════════════════════════
    @Test
    @Transactional
    @DisplayName("RED→GREEN: 删除含库存仓库应抛400")
    void testDeleteWarehouseWithStock() {
        MatWarehouse warehouse = new MatWarehouse();
        warehouse.setProjectId(PROJECT_ID);
        warehouse.setWarehouseCode("WH-DEL-STK");
        warehouse.setWarehouseName("有库存的仓库");
        warehouse.setStatus("ENABLE");
        Long id = warehouseService.create(warehouse);

        // Insert stock for this warehouse
        MatStock stock = new MatStock();
        stock.setTenantId(TENANT_ID);
        stock.setWarehouseId(id);
        stock.setMaterialId(1L);
        stock.setAvailableQty(new java.math.BigDecimal("100.0000"));
        stockMapper.insert(stock);

        // Delete should fail
        BusinessException ex = assertThrows(BusinessException.class, () -> {
            warehouseService.delete(id);
        }, "删除含库存仓库应抛 BusinessException");
        assertEquals("WAREHOUSE_HAS_STOCK", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════
    // RED → GREEN: Delete non-existent warehouse → 404
    // ═══════════════════════════════════════════════════════════
    @Test
    @Transactional
    @DisplayName("RED→GREEN: 删除不存在的仓库应抛异常")
    void testDeleteNonExistentWarehouse() {
        BusinessException ex = assertThrows(BusinessException.class, () -> {
            warehouseService.delete(99999999L);
        }, "删除不存在的仓库应抛 BusinessException");
        assertEquals("WAREHOUSE_NOT_FOUND", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════
    // RED → GREEN: Delete cross-tenant → denied
    // ═══════════════════════════════════════════════════════════
    @Test
    @Transactional
    @DisplayName("RED→GREEN: 跨租户删除应抛异常")
    void testDeleteCrossTenantWarehouse() {
        MatWarehouse warehouse = new MatWarehouse();
        warehouse.setProjectId(PROJECT_ID);
        warehouse.setWarehouseCode("WH-DEL-XTNT");
        warehouse.setWarehouseName("跨租户删除仓库");
        warehouse.setStatus("ENABLE");
        Long id = warehouseService.create(warehouse);

        // Switch to tenant 999
        UserContext.clear();
        UserContext.set(Jwts.claims()
                .add("userId", 999L)
                .add("username", "other")
                .add("tenantId", 999L)
                .build());

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            warehouseService.delete(id);
        }, "跨租户删除应抛 BusinessException");
        assertEquals("WAREHOUSE_NOT_FOUND", ex.getCode());
    }
}
