package com.cgcpms.material;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.material.entity.MdMaterial;
import com.cgcpms.material.entity.MdMaterialCategory;
import com.cgcpms.material.mapper.MdMaterialCategoryMapper;
import com.cgcpms.material.mapper.MdMaterialMapper;
import com.cgcpms.material.service.MdMaterialService;
import com.cgcpms.material.vo.MdMaterialVO;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@DisplayName("MdMaterialService — CRUD 基础测试")
class MdMaterialServiceTest {

    private static final long USER_ID = 1L;
    private static final long TENANT_ID = 0L;

    @Autowired
    private MdMaterialService mdMaterialService;

    @Autowired
    private MdMaterialMapper mdMaterialMapper;
    @Autowired
    private MdMaterialCategoryMapper categoryMapper;

    @BeforeEach
    void setUp() {
        setAdminContext();
        category(100L, "TEST-DEFAULT");
        category(101L, "TEST-101");
        category(102L, "TEST-102");
        category(200L, "TEST-200");
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @Transactional
    @DisplayName("创建材料并查询验证")
    void testCreateMaterial() {
        MdMaterial material = new MdMaterial();
        material.setMaterialCode("TEST-MAT-001");
        material.setMaterialName("测试材料");
        material.setUnit("个");
        material.setCategoryId(100L);
        material.setStatus("ENABLE");

        Long id = mdMaterialService.create(material);
        assertNotNull(id, "创建后应返回 ID");

        MdMaterialVO saved = mdMaterialService.getById(id);
        assertNotNull(saved, "应能查询到创建的材料");
        assertEquals("TEST-MAT-001", saved.getMaterialCode());
        assertEquals("测试材料", saved.getMaterialName());
    }

    @Test
    @Transactional
    @DisplayName("分页查询材料列表")
    void testGetPage() {
        MdMaterial material = new MdMaterial();
        material.setMaterialCode("PAGE-TEST");
        material.setMaterialName("分页测试材料");
        material.setUnit("个");
        material.setCategoryId(100L);
        material.setStatus("ENABLE");
        mdMaterialService.create(material);

        var page = mdMaterialService.getPage(1, 10, null, null, null, null);
        assertNotNull(page);
        assertTrue(page.getTotal() > 0, "应能查到数据");
    }

    @Test
    @Transactional
    @DisplayName("分页查询可按 code/name/category/status 过滤")
    void testGetPage_WithFilters() {
        MdMaterial enabled = new MdMaterial();
        enabled.setMaterialCode("FILTER-CODE-001");
        enabled.setMaterialName("过滤目标材料");
        enabled.setCategoryId(101L);
        enabled.setUnit("件");
        enabled.setStatus("ENABLE");
        mdMaterialService.create(enabled);

        MdMaterial disabled = new MdMaterial();
        disabled.setMaterialCode("FILTER-CODE-002");
        disabled.setMaterialName("其他材料");
        disabled.setCategoryId(102L);
        disabled.setUnit("件");
        disabled.setStatus("DISABLE");
        mdMaterialService.create(disabled);

        var page = mdMaterialService.getPage(1, 10, "FILTER-CODE-001", "过滤目标", 101L, "ENABLE");

        assertEquals(1, page.getRecords().size());
        assertEquals("FILTER-CODE-001", page.getRecords().get(0).getMaterialCode());
        assertEquals("过滤目标材料", page.getRecords().get(0).getMaterialName());
    }

    @Test
    @Transactional
    @DisplayName("getById → 不存在时抛 MATERIAL_NOT_FOUND")
    void testGetById_NotFound() {
        BusinessException ex = assertThrows(BusinessException.class, () -> mdMaterialService.getById(99999999L));
        assertEquals("MATERIAL_NOT_FOUND", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("getById → 跨租户材料不可见")
    void testGetById_TenantIsolation() {
        MdMaterial material = new MdMaterial();
        material.setTenantId(999L);
        material.setMaterialCode("TENANT-MAT-001");
        material.setMaterialName("跨租户材料");
        material.setUnit("个");
        material.setStatus("ENABLE");
        mdMaterialMapper.insert(material);

        BusinessException ex = assertThrows(BusinessException.class, () -> mdMaterialService.getById(material.getId()));
        assertEquals("MATERIAL_NOT_FOUND", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("update → 成功更新材料基础信息")
    void testUpdate_Success() {
        MdMaterial material = new MdMaterial();
        material.setMaterialCode("UPD-MAT-001");
        material.setMaterialName("待更新材料");
        material.setUnit("个");
        material.setCategoryId(100L);
        material.setStatus("ENABLE");
        Long id = mdMaterialService.create(material);

        MdMaterial updated = new MdMaterial();
        updated.setId(id);
        updated.setMaterialCode("UPD-MAT-001-A");
        updated.setMaterialName("更新后材料");
        updated.setCategoryId(200L);
        updated.setSpecification("M12");
        updated.setUnit("箱");
        updated.setBrand("测试品牌");
        updated.setStatus("DISABLE");
        mdMaterialService.update(updated);

        MdMaterialVO saved = mdMaterialService.getById(id);
        assertEquals("UPD-MAT-001-A", saved.getMaterialCode());
        assertEquals("更新后材料", saved.getMaterialName());
        assertEquals("200", saved.getCategoryId());
        assertEquals("DISABLE", saved.getStatus());
    }

    @Test
    @Transactional
    @DisplayName("update → 不存在时抛 MATERIAL_NOT_FOUND")
    void testUpdate_NotFound() {
        MdMaterial updated = new MdMaterial();
        updated.setId(99999999L);
        updated.setMaterialName("不存在");

        BusinessException ex = assertThrows(BusinessException.class, () -> mdMaterialService.update(updated));
        assertEquals("MATERIAL_NOT_FOUND", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("update → 跨租户材料不可编辑")
    void testUpdate_TenantIsolation() {
        MdMaterial material = new MdMaterial();
        material.setTenantId(999L);
        material.setMaterialCode("TENANT-UPD-001");
        material.setMaterialName("跨租户待更新材料");
        material.setUnit("个");
        material.setStatus("ENABLE");
        mdMaterialMapper.insert(material);

        MdMaterial updated = new MdMaterial();
        updated.setId(material.getId());
        updated.setMaterialName("非法更新");

        BusinessException ex = assertThrows(BusinessException.class, () -> mdMaterialService.update(updated));
        assertEquals("MATERIAL_NOT_FOUND", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("updateStatus → 成功切换状态")
    void testUpdateStatus_Success() {
        MdMaterial material = new MdMaterial();
        material.setMaterialCode("STATUS-MAT-001");
        material.setMaterialName("状态测试材料");
        material.setUnit("个");
        material.setCategoryId(100L);
        material.setStatus("ENABLE");
        Long id = mdMaterialService.create(material);

        mdMaterialService.updateStatus(id, "DISABLE");

        MdMaterialVO saved = mdMaterialService.getById(id);
        assertEquals("DISABLE", saved.getStatus());
    }

    @Test
    @Transactional
    @DisplayName("updateStatus → 不存在时抛 MATERIAL_NOT_FOUND")
    void testUpdateStatus_NotFound() {
        BusinessException ex = assertThrows(BusinessException.class, () -> mdMaterialService.updateStatus(99999999L, "DISABLE"));
        assertEquals("MATERIAL_NOT_FOUND", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("updateStatus → 跨租户材料不可改状态")
    void testUpdateStatus_TenantIsolation() {
        MdMaterial material = new MdMaterial();
        material.setTenantId(999L);
        material.setMaterialCode("TENANT-STATUS-001");
        material.setMaterialName("跨租户状态材料");
        material.setUnit("个");
        material.setStatus("ENABLE");
        mdMaterialMapper.insert(material);

        BusinessException ex = assertThrows(BusinessException.class, () -> mdMaterialService.updateStatus(material.getId(), "DISABLE"));
        assertEquals("MATERIAL_NOT_FOUND", ex.getCode());
    }

    private void setAdminContext() {
        var claims = Jwts.claims()
                .subject("admin")
                .add("userId", USER_ID)
                .add("username", "admin")
                .add("tenantId", TENANT_ID)
                .add("roleCodes", List.of("ADMIN"))
                .build();
        UserContext.set(claims);
    }

    private void category(Long id, String code) {
        MdMaterialCategory category = new MdMaterialCategory();
        category.setId(id); category.setTenantId(TENANT_ID); category.setCategoryCode(code);
        category.setCategoryName(code); category.setLevelNo(1); category.setOrderNum(0); category.setStatus("ENABLE");
        categoryMapper.insert(category);
    }
}
