package com.cgcpms.system;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.system.dict.entity.SysDictData;
import com.cgcpms.system.dict.entity.SysDictType;
import com.cgcpms.system.dict.mapper.SysDictDataMapper;
import com.cgcpms.system.dict.mapper.SysDictTypeMapper;
import com.cgcpms.system.dict.service.SysDictDataService;
import com.cgcpms.system.dict.vo.SysDictDataVO;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true", "spring.main.lazy-initialization=true"})
@ActiveProfiles("local")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SysDictDataServiceTest {

    private static final long USER_ADMIN = 1L;
    private static final long TENANT_0 = 0L;

    @Autowired
    private SysDictDataService dictDataService;

    @Autowired
    private SysDictDataMapper dictDataMapper;

    @Autowired
    private SysDictTypeMapper dictTypeMapper;

    private Long dictTypeId;

    /**
     * 每个测试前创建字典类型作为外键
     */
    @BeforeEach
    void setupContext() {
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", TENANT_0)
                .build());

        // 创建字典类型供字典数据关联
        SysDictType dictType = new SysDictType();
        dictType.setDictCode("test_type_" + System.nanoTime());
        dictType.setDictName("测试字典类型");
        dictType.setStatus("ENABLE");
        dictType.setTenantId(TENANT_0);
        dictTypeMapper.insert(dictType);
        dictTypeId = dictType.getId();
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
    @DisplayName("创建字典数据 — 基本创建成功返回ID")
    void testCreate_Success() {
        SysDictData entity = new SysDictData();
        entity.setDictTypeId(dictTypeId);
        entity.setDictLabel("测试标签1");
        entity.setDictValue("test_value_1");

        Long id = dictDataService.create(entity);
        assertNotNull(id, "创建后应返回ID");

        SysDictData saved = dictDataMapper.selectById(id);
        assertNotNull(saved, "应能查到刚创建的字典数据");
        assertEquals("测试标签1", saved.getDictLabel());
        assertEquals("test_value_1", saved.getDictValue());
        assertEquals(dictTypeId, saved.getDictTypeId());

        System.out.println("testCreate_Success 通过: dictValue=" + saved.getDictValue());
    }

    @Test
    @Order(2)
    @Transactional
    @DisplayName("创建字典数据 — 默认状态为ENABLE且orderNum为0")
    void testCreate_Defaults() {
        SysDictData entity = new SysDictData();
        entity.setDictTypeId(dictTypeId);
        entity.setDictLabel("默认值标签");
        entity.setDictValue("default_value");

        Long id = dictDataService.create(entity);
        SysDictData saved = dictDataMapper.selectById(id);
        assertEquals("ENABLE", saved.getStatus(), "未指定status时应默认为ENABLE");
        assertEquals(0, saved.getOrderNum(), "未指定orderNum时应默认为0");

        System.out.println("testCreate_Defaults 通过: status=" + saved.getStatus() + ", orderNum=" + saved.getOrderNum());
    }

    @Test
    @Order(3)
    @Transactional
    @DisplayName("创建字典数据 — 同字典类型下dictValue重复校验抛出 DICT_VALUE_EXISTS")
    void testCreate_DuplicateDictValue() {
        SysDictData d1 = new SysDictData();
        d1.setDictTypeId(dictTypeId);
        d1.setDictLabel("标签1");
        d1.setDictValue("dup_value");
        dictDataService.create(d1);

        SysDictData d2 = new SysDictData();
        d2.setDictTypeId(dictTypeId);
        d2.setDictLabel("标签2");
        d2.setDictValue("dup_value");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> dictDataService.create(d2),
                "同字典类型下重复dictValue应抛出BusinessException");
        assertEquals("DICT_VALUE_EXISTS", ex.getCode());

        System.out.println("testCreate_DuplicateDictValue 通过: code=" + ex.getCode());
    }

    @Test
    @Order(4)
    @Transactional
    @DisplayName("创建字典数据 — 自动设置tenantId")
    void testCreate_TenantIdAutoSet() {
        SysDictData entity = new SysDictData();
        entity.setDictTypeId(dictTypeId);
        entity.setDictLabel("租户标签");
        entity.setDictValue("tenant_value");

        Long id = dictDataService.create(entity);
        SysDictData saved = dictDataMapper.selectById(id);
        assertEquals(TENANT_0, saved.getTenantId(), "tenantId应自动从UserContext获取");

        System.out.println("testCreate_TenantIdAutoSet 通过");
    }

    @Test
    @Order(5)
    @Transactional
    @DisplayName("创建字典数据 — 含cssClass和listClass")
    void testCreate_WithCssClasses() {
        SysDictData entity = new SysDictData();
        entity.setDictTypeId(dictTypeId);
        entity.setDictLabel("样式标签");
        entity.setDictValue("style_value");
        entity.setCssClass("primary");
        entity.setListClass("default");

        Long id = dictDataService.create(entity);
        SysDictData saved = dictDataMapper.selectById(id);
        assertEquals("primary", saved.getCssClass());
        assertEquals("default", saved.getListClass());

        System.out.println("testCreate_WithCssClasses 通过: cssClass=" + saved.getCssClass()
                + ", listClass=" + saved.getListClass());
    }

    // ═══════════════════════════════════════════════════════════
    // getById tests
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(6)
    @Transactional
    @DisplayName("查询字典数据 — 正常查询返回VO含格式化时间")
    void testGetById_Success() {
        SysDictData entity = new SysDictData();
        entity.setDictTypeId(dictTypeId);
        entity.setDictLabel("详情标签");
        entity.setDictValue("detail_value");
        Long id = dictDataService.create(entity);

        SysDictDataVO vo = dictDataService.getById(id);
        assertNotNull(vo, "应能查到刚创建的字典数据");
        assertEquals("详情标签", vo.getDictLabel());
        assertEquals("detail_value", vo.getDictValue());
        assertNotNull(vo.getCreatedAt(), "createdAt应已格式化");

        System.out.println("testGetById_Success 通过: dictValue=" + vo.getDictValue());
    }

    @Test
    @Order(7)
    @Transactional
    @DisplayName("查询字典数据 — 跨租户隔离")
    void testGetById_CrossTenantIsolation() {
        SysDictData entity = new SysDictData();
        entity.setDictTypeId(dictTypeId);
        entity.setDictLabel("跨租户标签");
        entity.setDictValue("xtnt_value");
        Long id = dictDataService.create(entity);

        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 555L)
                .build());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> dictDataService.getById(id));
        assertEquals("DICT_DATA_NOT_FOUND", ex.getCode());

        System.out.println("testGetById_CrossTenantIsolation 通过: code=" + ex.getCode());
    }

    @Test
    @Order(8)
    @Transactional
    @DisplayName("查询不存在的字典数据 — 抛出 DICT_DATA_NOT_FOUND")
    void testGetById_NotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> dictDataService.getById(999999L));
        assertEquals("DICT_DATA_NOT_FOUND", ex.getCode());

        System.out.println("testGetById_NotFound 通过");
    }

    // ═══════════════════════════════════════════════════════════
    // getPage tests
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(9)
    @Transactional
    @DisplayName("分页查询 — 全量分页返回当前租户数据且按orderNum升序")
    void testGetPage_All() {
        for (int i = 0; i < 3; i++) {
            SysDictData entity = new SysDictData();
            entity.setDictTypeId(dictTypeId);
            entity.setDictLabel("分页标签" + i);
            entity.setDictValue("page_val_" + i);
            entity.setOrderNum(i);
            dictDataService.create(entity);
        }

        IPage<SysDictDataVO> page = dictDataService.getPage(1, 10, dictTypeId, null, null);
        assertTrue(page.getTotal() >= 3, "至少应有刚创建的3条数据");

        // 验证按orderNum升序
        List<SysDictDataVO> records = page.getRecords();
        for (int i = 1; i < records.size(); i++) {
            assertTrue(records.get(i - 1).getOrderNum() <= records.get(i).getOrderNum(),
                    "应按orderNum升序排列");
        }

        System.out.println("testGetPage_All 通过: total=" + page.getTotal());
    }

    @Test
    @Order(10)
    @Transactional
    @DisplayName("分页查询 — 按dictLabel模糊搜索")
    void testGetPage_FilterByDictLabel() {
        SysDictData entity = new SysDictData();
        entity.setDictTypeId(dictTypeId);
        entity.setDictLabel("唯一标签XYZ");
        entity.setDictValue("unique_label");
        dictDataService.create(entity);

        IPage<SysDictDataVO> page = dictDataService.getPage(1, 10, dictTypeId, "唯一", null);
        assertTrue(page.getTotal() >= 1, "按dictLabel模糊搜索应有结果");

        System.out.println("testGetPage_FilterByDictLabel 通过: total=" + page.getTotal());
    }

    @Test
    @Order(11)
    @Transactional
    @DisplayName("分页查询 — 按dictTypeId筛选")
    void testGetPage_FilterByDictTypeId() {
        // 创建另一个字典类型
        SysDictType otherType = new SysDictType();
        otherType.setDictCode("other_type_" + System.nanoTime());
        otherType.setDictName("其他字典类型");
        otherType.setTenantId(TENANT_0);
        otherType.setStatus("ENABLE");
        dictTypeMapper.insert(otherType);

        SysDictData data1 = new SysDictData();
        data1.setDictTypeId(dictTypeId);
        data1.setDictLabel("类型1标签");
        data1.setDictValue("type1_val");
        dictDataService.create(data1);

        SysDictData data2 = new SysDictData();
        data2.setDictTypeId(otherType.getId());
        data2.setDictLabel("类型2标签");
        data2.setDictValue("type2_val");
        dictDataService.create(data2);

        IPage<SysDictDataVO> page1 = dictDataService.getPage(1, 10, dictTypeId, null, null);
        assertTrue(page1.getTotal() >= 1);
        assertTrue(page1.getRecords().stream()
                .allMatch(v -> String.valueOf(dictTypeId).equals(v.getDictTypeId())));

        IPage<SysDictDataVO> page2 = dictDataService.getPage(1, 10, otherType.getId(), null, null);
        assertTrue(page2.getTotal() >= 1);
        assertTrue(page2.getRecords().stream()
                .allMatch(v -> String.valueOf(otherType.getId()).equals(v.getDictTypeId())));

        System.out.println("testGetPage_FilterByDictTypeId 通过: page1=" + page1.getTotal()
                + ", page2=" + page2.getTotal());
    }

    @Test
    @Order(12)
    @Transactional
    @DisplayName("分页查询 — 按status筛选")
    void testGetPage_FilterByStatus() {
        SysDictData enable = new SysDictData();
        enable.setDictTypeId(dictTypeId);
        enable.setDictLabel("启用数据");
        enable.setDictValue("enable_val");
        enable.setStatus("ENABLE");
        dictDataService.create(enable);

        SysDictData disable = new SysDictData();
        disable.setDictTypeId(dictTypeId);
        disable.setDictLabel("禁用数据");
        disable.setDictValue("disable_val");
        disable.setStatus("DISABLE");
        dictDataService.create(disable);

        IPage<SysDictDataVO> pageEnable = dictDataService.getPage(1, 10, dictTypeId, null, "ENABLE");
        assertTrue(pageEnable.getTotal() >= 1);

        IPage<SysDictDataVO> pageDisable = dictDataService.getPage(1, 10, dictTypeId, null, "DISABLE");
        assertTrue(pageDisable.getTotal() >= 1);
        assertTrue(pageDisable.getRecords().stream()
                .allMatch(v -> "DISABLE".equals(v.getStatus())));

        System.out.println("testGetPage_FilterByStatus 通过: enable="
                + pageEnable.getTotal() + ", disable=" + pageDisable.getTotal());
    }

    @Test
    @Order(13)
    @Transactional
    @DisplayName("分页查询 — 租户隔离")
    void testGetPage_CrossTenantEmpty() {
        SysDictData entity = new SysDictData();
        entity.setDictTypeId(dictTypeId);
        entity.setDictLabel("租户分页标签");
        entity.setDictValue("xtnt_page_val");
        dictDataService.create(entity);

        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 777L)
                .build());

        IPage<SysDictDataVO> page = dictDataService.getPage(1, 10, dictTypeId, null, null);
        assertEquals(0, page.getTotal(), "其他租户不应看到租户0的数据");

        System.out.println("testGetPage_CrossTenantEmpty 通过: total=" + page.getTotal());
    }

    // ═══════════════════════════════════════════════════════════
    // Update tests
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(14)
    @Transactional
    @DisplayName("更新字典数据 — 修改dictLabel和dictValue")
    void testUpdate_Success() {
        SysDictData entity = new SysDictData();
        entity.setDictTypeId(dictTypeId);
        entity.setDictLabel("原始标签");
        entity.setDictValue("old_value");
        Long id = dictDataService.create(entity);

        SysDictData update = new SysDictData();
        update.setId(id);
        update.setDictTypeId(dictTypeId);
        update.setDictLabel("新标签");
        update.setDictValue("new_value");
        dictDataService.update(update);

        SysDictData saved = dictDataMapper.selectById(id);
        assertEquals("新标签", saved.getDictLabel(), "dictLabel应已更新");
        assertEquals("new_value", saved.getDictValue(), "dictValue应已更新");

        System.out.println("testUpdate_Success 通过: dictLabel=" + saved.getDictLabel()
                + ", dictValue=" + saved.getDictValue());
    }

    @Test
    @Order(15)
    @Transactional
    @DisplayName("更新字典数据 — 租户隔离（其他租户的不让改）")
    void testUpdate_CrossTenantIsolation() {
        SysDictData entity = new SysDictData();
        entity.setDictTypeId(dictTypeId);
        entity.setDictLabel("跨租户更新标签");
        entity.setDictValue("xtnt_update_val");
        Long id = dictDataService.create(entity);

        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 888L)
                .build());

        SysDictData update = new SysDictData();
        update.setId(id);
        update.setDictTypeId(dictTypeId);
        update.setDictLabel("恶意修改");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> dictDataService.update(update));
        assertEquals("DICT_DATA_NOT_FOUND", ex.getCode());

        System.out.println("testUpdate_CrossTenantIsolation 通过: code=" + ex.getCode());
    }

    @Test
    @Order(16)
    @Transactional
    @DisplayName("更新不存在的字典数据 — 抛出 DICT_DATA_NOT_FOUND")
    void testUpdate_NotFound() {
        SysDictData update = new SysDictData();
        update.setId(999999L);
        update.setDictTypeId(dictTypeId);
        update.setDictLabel("不存在");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> dictDataService.update(update));
        assertEquals("DICT_DATA_NOT_FOUND", ex.getCode());

        System.out.println("testUpdate_NotFound 通过");
    }

    // ═══════════════════════════════════════════════════════════
    // Delete tests
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(17)
    @Transactional
    @DisplayName("删除字典数据 — 正常删除成功")
    void testDelete_Success() {
        SysDictData entity = new SysDictData();
        entity.setDictTypeId(dictTypeId);
        entity.setDictLabel("可删除标签");
        entity.setDictValue("deletable_val");
        Long id = dictDataService.create(entity);

        assertDoesNotThrow(() -> dictDataService.delete(id), "删除字典数据不应抛异常");

        // SysDictData 是物理删除（不继承BaseEntity）
        SysDictData deleted = dictDataMapper.selectById(id);
        assertNull(deleted, "物理删除后应查不到记录");

        System.out.println("testDelete_Success 通过");
    }

    @Test
    @Order(18)
    @Transactional
    @DisplayName("删除字典数据 — 租户隔离（其他租户的不能删）")
    void testDelete_CrossTenantIsolation() {
        SysDictData entity = new SysDictData();
        entity.setDictTypeId(dictTypeId);
        entity.setDictLabel("跨租户删除标签");
        entity.setDictValue("del_xtnt_val");
        Long id = dictDataService.create(entity);

        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 999L)
                .build());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> dictDataService.delete(id));
        assertEquals("DICT_DATA_NOT_FOUND", ex.getCode());

        System.out.println("testDelete_CrossTenantIsolation 通过: code=" + ex.getCode());
    }

    @Test
    @Order(19)
    @Transactional
    @DisplayName("删除不存在的字典数据 — 抛出 DICT_DATA_NOT_FOUND")
    void testDelete_NotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> dictDataService.delete(999999L));
        assertEquals("DICT_DATA_NOT_FOUND", ex.getCode());

        System.out.println("testDelete_NotFound 通过");
    }

    // ═══════════════════════════════════════════════════════════
    // getByDictCode tests
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(20)
    @Transactional
    @DisplayName("通过dictCode获取数据 — 返回启用状态的字典数据列表")
    void testGetByDictCode_Success() {
        // 用已知的dictCode查询（使用setupContext中创建的字典类型）
        SysDictType savedType = dictTypeMapper.selectById(dictTypeId);
        String dictCode = savedType.getDictCode();

        SysDictData data1 = new SysDictData();
        data1.setDictTypeId(dictTypeId);
        data1.setDictLabel("启用项1");
        data1.setDictValue("enabled_1");
        data1.setStatus("ENABLE");
        data1.setOrderNum(1);
        dictDataService.create(data1);

        SysDictData data2 = new SysDictData();
        data2.setDictTypeId(dictTypeId);
        data2.setDictLabel("禁用项1");
        data2.setDictValue("disabled_1");
        data2.setStatus("DISABLE");
        data2.setOrderNum(2);
        dictDataService.create(data2);

        List<SysDictDataVO> list = dictDataService.getByDictCode(dictCode);
        assertNotNull(list, "结果不应为null");
        // 只应返回ENABLE状态的数据
        assertTrue(list.stream().allMatch(v -> "ENABLE".equals(v.getStatus())),
                "getByDictCode应只返回ENABLE状态的数据");
        assertTrue(list.size() >= 1, "至少应有1条ENABLE数据");

        System.out.println("testGetByDictCode_Success 通过: size=" + list.size() + ", dictCode=" + dictCode);
    }

    @Test
    @Order(21)
    @Transactional
    @DisplayName("通过dictCode获取数据 — 不存在的dictCode返回空列表")
    void testGetByDictCode_NotFound() {
        List<SysDictDataVO> list = dictDataService.getByDictCode("NONEXISTENT_CODE");
        assertNotNull(list, "不存在的dictCode应返回空列表而非null");
        assertTrue(list.isEmpty(), "不存在的dictCode应返回空列表");

        System.out.println("testGetByDictCode_NotFound 通过: size=" + list.size());
    }

    @Test
    @Order(22)
    @Transactional
    @DisplayName("通过dictCode获取数据 — 租户隔离")
    void testGetByDictCode_CrossTenantEmpty() {
        SysDictType savedType = dictTypeMapper.selectById(dictTypeId);
        String dictCode = savedType.getDictCode();

        SysDictData data = new SysDictData();
        data.setDictTypeId(dictTypeId);
        data.setDictLabel("租户数据");
        data.setDictValue("tenant_data");
        data.setStatus("ENABLE");
        dictDataService.create(data);

        // 切换到其他租户
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 666L)
                .build());

        List<SysDictDataVO> list = dictDataService.getByDictCode(dictCode);
        assertTrue(list.isEmpty(), "其他租户不应看到租户0的字典数据");

        System.out.println("testGetByDictCode_CrossTenantEmpty 通过: size=" + list.size());
    }

    @Test
    @Order(23)
    @Transactional
    @DisplayName("通过dictCode获取数据 — 按orderNum升序")
    void testGetByDictCode_OrderedByOrderNum() {
        SysDictType savedType = dictTypeMapper.selectById(dictTypeId);
        String dictCode = savedType.getDictCode();

        SysDictData data1 = new SysDictData();
        data1.setDictTypeId(dictTypeId);
        data1.setDictLabel("排序项3");
        data1.setDictValue("order_3");
        data1.setStatus("ENABLE");
        data1.setOrderNum(3);
        dictDataService.create(data1);

        SysDictData data2 = new SysDictData();
        data2.setDictTypeId(dictTypeId);
        data2.setDictLabel("排序项1");
        data2.setDictValue("order_1");
        data2.setStatus("ENABLE");
        data2.setOrderNum(1);
        dictDataService.create(data2);

        SysDictData data3 = new SysDictData();
        data3.setDictTypeId(dictTypeId);
        data3.setDictLabel("排序项2");
        data3.setDictValue("order_2");
        data3.setStatus("ENABLE");
        data3.setOrderNum(2);
        dictDataService.create(data3);

        List<SysDictDataVO> list = dictDataService.getByDictCode(dictCode);
        assertTrue(list.size() >= 3);

        // 验证按orderNum升序
        for (int i = 1; i < list.size(); i++) {
            assertTrue(list.get(i - 1).getOrderNum() <= list.get(i).getOrderNum(),
                    "getByDictCode应按orderNum升序，但 " + (i - 1) + "(" + list.get(i - 1).getOrderNum()
                            + ") > " + i + "(" + list.get(i).getOrderNum() + ")");
        }

        System.out.println("testGetByDictCode_OrderedByOrderNum 通过: size=" + list.size());
    }
}
