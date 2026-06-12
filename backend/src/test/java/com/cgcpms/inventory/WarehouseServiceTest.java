package com.cgcpms.inventory;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.common.result.PageResult;
import com.cgcpms.inventory.entity.MatWarehouse;
import com.cgcpms.inventory.mapper.MatWarehouseMapper;
import com.cgcpms.inventory.service.MatWarehouseService;
import com.cgcpms.inventory.vo.MatWarehouseVO;
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
    private static final long PROJECT_ID = 100L;

    @Autowired
    private MatWarehouseService warehouseService;

    @Autowired
    private MatWarehouseMapper warehouseMapper;

    private Long createdWarehouseId;

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
        assertEquals("WH-001", vo.getWarehouseCode());
        assertEquals("一号仓库", vo.getWarehouseName());
        assertEquals("ENABLE", vo.getStatus());
        assertEquals(String.valueOf(PROJECT_ID), vo.getProjectId());
        assertEquals(String.valueOf(TENANT_ID), vo.getTenantId());

        createdWarehouseId = id;
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
        w2.setProjectId(200L);
        w2.setWarehouseCode("WH-P200-A");
        w2.setWarehouseName("项目200仓库A");
        w2.setStatus("ENABLE");
        warehouseService.create(w2);

        // Query with project filter
        PageResult<MatWarehouseVO> page1 = warehouseService.getPage(1, 20, PROJECT_ID, null, null, null);
        assertEquals(1, page1.getTotal(), "项目100应只有1个仓库");
        assertEquals("WH-P100-A", page1.getRecords().get(0).getWarehouseCode());

        // Query without project filter (all tenant warehouses)
        PageResult<MatWarehouseVO> page2 = warehouseService.getPage(1, 20, null, null, null, null);
        assertEquals(2, page2.getTotal(), "应返回租户下所有2个仓库");
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

        PageResult<MatWarehouseVO> enabled = warehouseService.getPage(1, 20, null, null, null, "ENABLE");
        assertEquals(1, enabled.getTotal(), "应只有1个启用仓库");
        assertEquals("WH-ENB", enabled.getRecords().get(0).getWarehouseCode());

        PageResult<MatWarehouseVO> disabled = warehouseService.getPage(1, 20, null, null, null, "DISABLE");
        assertEquals(1, disabled.getTotal(), "应只有1个禁用仓库");
        assertEquals("WH-DIS", disabled.getRecords().get(0).getWarehouseCode());
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

        MatWarehouse update = new MatWarehouse();
        update.setId(id);
        update.setWarehouseCode("WH-UPD-002");
        update.setWarehouseName("已更新仓库");
        update.setStatus("DISABLE");
        warehouseService.update(update);

        MatWarehouseVO vo = warehouseService.getById(id);
        assertEquals("WH-UPD-002", vo.getWarehouseCode());
        assertEquals("已更新仓库", vo.getWarehouseName());
        assertEquals("DISABLE", vo.getStatus());
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

        // Search by code fragment
        PageResult<MatWarehouseVO> byCode = warehouseService.getPage(1, 20, null, "MAIN", null, null);
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
}
