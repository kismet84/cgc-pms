package com.cgcpms.system;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.system.dict.entity.SysDictType;
import com.cgcpms.system.dict.mapper.SysDictTypeMapper;
import com.cgcpms.system.dict.service.SysDictTypeService;
import com.cgcpms.system.dict.vo.SysDictTypeVO;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true", "spring.main.lazy-initialization=true"})
@ActiveProfiles("local")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SysDictTypeServiceTest {

    private static final long USER_ADMIN = 1L;
    private static final long TENANT_0 = 0L;

    @Autowired
    private SysDictTypeService dictTypeService;

    @Autowired
    private SysDictTypeMapper dictTypeMapper;

    @BeforeEach
    void setupContext() {
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", TENANT_0)
                .build());
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    // ═══════════════════════════════════════════════════════════
    // Create tests
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @Transactional
    @DisplayName("创建字典类型 — 基本创建成功返回ID")
    void testCreate_Success() {
        SysDictType entity = new SysDictType();
        entity.setDictCode("test_status_1");
        entity.setDictName("测试状态1");

        Long id = dictTypeService.create(entity);
        assertNotNull(id, "创建后应返回ID");

        SysDictType saved = dictTypeMapper.selectById(id);
        assertNotNull(saved, "应能查到刚创建的字典类型");
        assertEquals("test_status_1", saved.getDictCode());
        assertEquals("测试状态1", saved.getDictName());

        System.out.println("testCreate_Success 通过: dictCode=" + saved.getDictCode());
    }

    @Test
    @Order(2)
    @Transactional
    @DisplayName("创建字典类型 — 默认状态为ENABLE")
    void testCreate_DefaultStatusEnable() {
        SysDictType entity = new SysDictType();
        entity.setDictCode("default_status");
        entity.setDictName("默认状态字典");

        Long id = dictTypeService.create(entity);
        SysDictType saved = dictTypeMapper.selectById(id);
        assertEquals("ENABLE", saved.getStatus(), "未指定status时应默认为ENABLE");

        System.out.println("testCreate_DefaultStatusEnable 通过");
    }

    @Test
    @Order(3)
    @Transactional
    @DisplayName("创建字典类型 — 字典编码重复校验抛出 DICT_CODE_EXISTS")
    void testCreate_DuplicateDictCode() {
        SysDictType d1 = new SysDictType();
        d1.setDictCode("dup_code");
        d1.setDictName("重复编码1");
        dictTypeService.create(d1);

        SysDictType d2 = new SysDictType();
        d2.setDictCode("dup_code");
        d2.setDictName("重复编码2");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> dictTypeService.create(d2),
                "重复字典编码应抛出BusinessException");
        assertEquals("DICT_CODE_EXISTS", ex.getCode());

        System.out.println("testCreate_DuplicateDictCode 通过: code=" + ex.getCode());
    }

    @Test
    @Order(4)
    @Transactional
    @DisplayName("创建字典类型 — 自动设置tenantId")
    void testCreate_TenantIdAutoSet() {
        SysDictType entity = new SysDictType();
        entity.setDictCode("tenant_test");
        entity.setDictName("租户字典");

        Long id = dictTypeService.create(entity);
        SysDictType saved = dictTypeMapper.selectById(id);
        assertEquals(TENANT_0, saved.getTenantId(), "tenantId应自动从UserContext获取");

        System.out.println("testCreate_TenantIdAutoSet 通过");
    }

    // ═══════════════════════════════════════════════════════════
    // getById tests
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(5)
    @Transactional
    @DisplayName("查询字典类型 — 正常查询返回VO含格式化时间")
    void testGetById_Success() {
        SysDictType entity = new SysDictType();
        entity.setDictCode("detail_dict");
        entity.setDictName("详情字典");
        Long id = dictTypeService.create(entity);

        SysDictTypeVO vo = dictTypeService.getById(id);
        assertNotNull(vo, "应能查到刚创建的字典类型");
        assertEquals("detail_dict", vo.getDictCode());
        assertEquals("详情字典", vo.getDictName());
        assertNotNull(vo.getCreatedAt(), "createdAt应已格式化");

        System.out.println("testGetById_Success 通过: dictCode=" + vo.getDictCode());
    }

    @Test
    @Order(6)
    @Transactional
    @DisplayName("查询字典类型 — 跨租户隔离")
    void testGetById_CrossTenantIsolation() {
        SysDictType entity = new SysDictType();
        entity.setDictCode("xtnt_dict");
        entity.setDictName("跨租户字典");
        Long id = dictTypeService.create(entity);

        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 555L)
                .build());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> dictTypeService.getById(id));
        assertEquals("DICT_TYPE_NOT_FOUND", ex.getCode());

        System.out.println("testGetById_CrossTenantIsolation 通过: code=" + ex.getCode());
    }

    @Test
    @Order(7)
    @Transactional
    @DisplayName("查询不存在的字典类型 — 抛出 DICT_TYPE_NOT_FOUND")
    void testGetById_NotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> dictTypeService.getById(999999L));
        assertEquals("DICT_TYPE_NOT_FOUND", ex.getCode());

        System.out.println("testGetById_NotFound 通过");
    }

    // ═══════════════════════════════════════════════════════════
    // getPage tests
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(8)
    @Transactional
    @DisplayName("分页查询 — 全量分页返回当前租户数据")
    void testGetPage_All() {
        // 创建几条数据
        for (int i = 0; i < 3; i++) {
            SysDictType entity = new SysDictType();
            entity.setDictCode("page_all_" + i);
            entity.setDictName("分页全量" + i);
            dictTypeService.create(entity);
        }

        IPage<SysDictTypeVO> page = dictTypeService.getPage(1, 10, null, null, null);
        assertTrue(page.getTotal() >= 3, "至少应有刚创建的3条数据");

        System.out.println("testGetPage_All 通过: total=" + page.getTotal());
    }

    @Test
    @Order(9)
    @Transactional
    @DisplayName("分页查询 — 按dictCode模糊搜索")
    void testGetPage_FilterByDictCode() {
        SysDictType entity = new SysDictType();
        entity.setDictCode("unique_code_xyz");
        entity.setDictName("唯编码字典");
        dictTypeService.create(entity);

        IPage<SysDictTypeVO> page = dictTypeService.getPage(1, 10, "unique_code", null, null);
        assertTrue(page.getTotal() >= 1, "按dictCode模糊搜索应有结果");

        System.out.println("testGetPage_FilterByDictCode 通过: total=" + page.getTotal());
    }

    @Test
    @Order(10)
    @Transactional
    @DisplayName("分页查询 — 按dictName模糊搜索")
    void testGetPage_FilterByDictName() {
        SysDictType entity = new SysDictType();
        entity.setDictCode("name_search");
        entity.setDictName("唯一名称XYZ字典");
        dictTypeService.create(entity);

        IPage<SysDictTypeVO> page = dictTypeService.getPage(1, 10, null, "唯一名称", null);
        assertTrue(page.getTotal() >= 1, "按dictName模糊搜索应有结果");

        System.out.println("testGetPage_FilterByDictName 通过: total=" + page.getTotal());
    }

    @Test
    @Order(11)
    @Transactional
    @DisplayName("分页查询 — 按status筛选")
    void testGetPage_FilterByStatus() {
        SysDictType enable = new SysDictType();
        enable.setDictCode("status_enable");
        enable.setDictName("启用状态");
        enable.setStatus("ENABLE");
        dictTypeService.create(enable);

        SysDictType disable = new SysDictType();
        disable.setDictCode("status_disable");
        disable.setDictName("禁用状态");
        disable.setStatus("DISABLE");
        dictTypeService.create(disable);

        IPage<SysDictTypeVO> pageEnable = dictTypeService.getPage(1, 10, null, null, "ENABLE");
        assertTrue(pageEnable.getTotal() >= 1);

        IPage<SysDictTypeVO> pageDisable = dictTypeService.getPage(1, 10, null, null, "DISABLE");
        assertTrue(pageDisable.getTotal() >= 1);
        assertTrue(pageDisable.getRecords().stream()
                .allMatch(v -> "DISABLE".equals(v.getStatus())));

        System.out.println("testGetPage_FilterByStatus 通过: enable="
                + pageEnable.getTotal() + ", disable=" + pageDisable.getTotal());
    }

    @Test
    @Order(12)
    @Transactional
    @DisplayName("分页查询 — 租户隔离")
    void testGetPage_CrossTenantEmpty() {
        // 在租户0创建数据
        SysDictType entity = new SysDictType();
        entity.setDictCode("xtnt_page");
        entity.setDictName("租户分页");
        dictTypeService.create(entity);

        // 切换到其他租户
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 777L)
                .build());

        IPage<SysDictTypeVO> page = dictTypeService.getPage(1, 10, "xtnt_page", null, null);
        assertEquals(0, page.getTotal(), "其他租户不应看到租户0的数据");

        System.out.println("testGetPage_CrossTenantEmpty 通过: total=" + page.getTotal());
    }

    // ═══════════════════════════════════════════════════════════
    // Update tests
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(13)
    @Transactional
    @DisplayName("更新字典类型 — 修改dictName")
    void testUpdate_Success() {
        SysDictType entity = new SysDictType();
        entity.setDictCode("updatable_type");
        entity.setDictName("原始名称");
        Long id = dictTypeService.create(entity);

        SysDictType update = new SysDictType();
        update.setId(id);
        update.setDictCode("updatable_type");
        update.setDictName("新名称");
        dictTypeService.update(update);

        SysDictType saved = dictTypeMapper.selectById(id);
        assertEquals("新名称", saved.getDictName(), "dictName应已更新");

        System.out.println("testUpdate_Success 通过: dictName=" + saved.getDictName());
    }

    @Test
    @Order(14)
    @Transactional
    @DisplayName("更新字典类型 — 租户隔离（其他租户的不让改）")
    void testUpdate_CrossTenantIsolation() {
        SysDictType entity = new SysDictType();
        entity.setDictCode("xtnt_update");
        entity.setDictName("跨租户更新");
        Long id = dictTypeService.create(entity);

        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 888L)
                .build());

        SysDictType update = new SysDictType();
        update.setId(id);
        update.setDictCode("xtnt_update");
        update.setDictName("恶意修改");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> dictTypeService.update(update));
        assertEquals("DICT_TYPE_NOT_FOUND", ex.getCode());

        System.out.println("testUpdate_CrossTenantIsolation 通过: code=" + ex.getCode());
    }

    @Test
    @Order(15)
    @Transactional
    @DisplayName("更新不存在的字典类型 — 抛出 DICT_TYPE_NOT_FOUND")
    void testUpdate_NotFound() {
        SysDictType update = new SysDictType();
        update.setId(999999L);
        update.setDictCode("nonexist");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> dictTypeService.update(update));
        assertEquals("DICT_TYPE_NOT_FOUND", ex.getCode());

        System.out.println("testUpdate_NotFound 通过");
    }

    // ═══════════════════════════════════════════════════════════
    // Delete tests
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(16)
    @Transactional
    @DisplayName("删除字典类型 — 正常删除成功")
    void testDelete_Success() {
        SysDictType entity = new SysDictType();
        entity.setDictCode("deletable_type");
        entity.setDictName("可删除字典");
        Long id = dictTypeService.create(entity);

        assertDoesNotThrow(() -> dictTypeService.delete(id), "删除字典类型不应抛异常");

        // SysDictType 是物理删除（不继承BaseEntity）
        SysDictType deleted = dictTypeMapper.selectById(id);
        assertNull(deleted, "物理删除后应查不到记录");

        System.out.println("testDelete_Success 通过");
    }

    @Test
    @Order(17)
    @Transactional
    @DisplayName("删除字典类型 — 租户隔离（其他租户的不能删）")
    void testDelete_CrossTenantIsolation() {
        SysDictType entity = new SysDictType();
        entity.setDictCode("del_xtnt_type");
        entity.setDictName("跨租户删除字典");
        Long id = dictTypeService.create(entity);

        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 999L)
                .build());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> dictTypeService.delete(id));
        assertEquals("DICT_TYPE_NOT_FOUND", ex.getCode());

        System.out.println("testDelete_CrossTenantIsolation 通过: code=" + ex.getCode());
    }

    @Test
    @Order(18)
    @Transactional
    @DisplayName("删除不存在的字典类型 — 抛出 DICT_TYPE_NOT_FOUND")
    void testDelete_NotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> dictTypeService.delete(999999L));
        assertEquals("DICT_TYPE_NOT_FOUND", ex.getCode());

        System.out.println("testDelete_NotFound 通过");
    }

    // ═══════════════════════════════════════════════════════════
    // Pagination edge case tests
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(19)
    @Transactional
    @DisplayName("分页查询 — 第2页能正确返回")
    void testGetPage_SecondPage() {
        // 创建3条数据，每页2条
        for (int i = 1; i <= 3; i++) {
            SysDictType entity = new SysDictType();
            entity.setDictCode("page2_test_" + i);
            entity.setDictName("分页测试" + i);
            dictTypeService.create(entity);
        }

        IPage<SysDictTypeVO> page1 = dictTypeService.getPage(1, 2, "page2_test", null, null);
        assertEquals(3, page1.getTotal(), "总数应为3");
        assertEquals(2, page1.getRecords().size(), "第1页应有2条");

        IPage<SysDictTypeVO> page2 = dictTypeService.getPage(2, 2, "page2_test", null, null);
        assertEquals(3, page2.getTotal(), "总数应为3");
        assertEquals(1, page2.getRecords().size(), "第2页应有1条");

        System.out.println("testGetPage_SecondPage 通过: page1=" + page1.getRecords().size()
                + ", page2=" + page2.getRecords().size());
    }
}
