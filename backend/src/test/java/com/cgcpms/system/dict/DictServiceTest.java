package com.cgcpms.system.dict;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.system.dict.entity.SysDictData;
import com.cgcpms.system.dict.entity.SysDictType;
import com.cgcpms.system.dict.service.SysDictDataService;
import com.cgcpms.system.dict.service.SysDictTypeService;
import com.cgcpms.system.dict.vo.SysDictDataVO;
import com.cgcpms.system.dict.vo.SysDictTypeVO;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DictServiceTest {

    private static final long USER_ADMIN = 1L;
    private static final long TENANT_SYSTEM = 0L;
    private static final long TENANT_OTHER = 9999L;

    @Autowired
    private SysDictTypeService dictTypeService;

    @Autowired
    private SysDictDataService dictDataService;

    @Autowired
    private JdbcTemplate jdbc;

    // Tracks IDs created during tests for cleanup assertions
    private static Long createdTypeId;
    private static Long createdDataId;

    @BeforeEach
    void setupContext() {
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", TENANT_SYSTEM)
                .build());
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    // ═══════════════════════════════════════════════════════════
    // SysDictType 测试
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("T1: 分页查询字典类型—验证种子数据可见")
    void test01_listDictTypes_seedData() {
        IPage<SysDictTypeVO> page = dictTypeService.getPage(1, 20, null, null, null);
        assertTrue(page.getTotal() >= 7, "应至少有7条种子数据");
        assertFalse(page.getRecords().isEmpty());
        // 验证种子数据包含 project_status
        boolean hasProjectStatus = page.getRecords().stream()
                .anyMatch(vo -> "project_status".equals(vo.getDictCode()));
        assertTrue(hasProjectStatus, "应包含 project_status 字典类型");
    }

    @Test
    @Order(2)
    @DisplayName("T2: 按ID获取字典类型")
    void test02_getDictTypeById() {
        // 种子数据 project_status id=1001
        SysDictTypeVO vo = dictTypeService.getById(1001L);
        assertNotNull(vo);
        assertEquals("project_status", vo.getDictCode());
        assertEquals("项目状态", vo.getDictName());
    }

    @Test
    @Order(3)
    @DisplayName("T3: 创建字典类型—成功")
    @Transactional
    void test03_createDictType_success() {
        SysDictType entity = new SysDictType();
        entity.setDictCode("test_status");
        entity.setDictName("测试状态");

        Long id = dictTypeService.create(entity);
        assertNotNull(id);
        createdTypeId = id;

        SysDictTypeVO saved = dictTypeService.getById(id);
        assertEquals("test_status", saved.getDictCode());
        assertEquals("ENABLE", saved.getStatus());
    }

    @Test
    @Order(4)
    @DisplayName("T4: 创建字典类型—编码重复应抛异常")
    @Transactional
    void test04_createDictType_duplicateCode() {
        SysDictType entity = new SysDictType();
        entity.setDictCode("project_status"); // 种子数据已存在
        entity.setDictName("重复编码");

        assertThrows(BusinessException.class, () -> dictTypeService.create(entity));
    }

    @Test
    @Order(5)
    @DisplayName("T5: 更新字典类型")
    @Transactional
    void test05_updateDictType() {
        // 使用种子数据 contract_type id=1002
        SysDictType entity = new SysDictType();
        entity.setId(1002L);
        entity.setDictCode("contract_type");
        entity.setDictName("合同类型（已修改）");
        entity.setStatus("DISABLE");

        dictTypeService.update(entity);

        SysDictTypeVO updated = dictTypeService.getById(1002L);
        assertEquals("合同类型（已修改）", updated.getDictName());
        assertEquals("DISABLE", updated.getStatus());
    }

    @Test
    @Order(6)
    @DisplayName("T6: 删除字典类型")
    @Transactional
    void test06_deleteDictType() {
        // 先创建一个待删除的类型
        SysDictType entity = new SysDictType();
        entity.setDictCode("to_delete_type");
        entity.setDictName("待删除类型");
        Long id = dictTypeService.create(entity);

        assertDoesNotThrow(() -> dictTypeService.delete(id));
        assertThrows(BusinessException.class, () -> dictTypeService.getById(id));
    }

    @Test
    @Order(7)
    @DisplayName("T7: 租户隔离—其他租户数据不可见")
    @Transactional
    void test07_tenantIsolation_type() {
        // 在 other tenant 下创建类型
        UserContext.clear();
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", TENANT_OTHER)
                .build());

        SysDictType entity = new SysDictType();
        entity.setDictCode("other_tenant_type");
        entity.setDictName("其他租户类型");
        Long otherId = dictTypeService.create(entity);
        assertNotNull(otherId);

        // 切回 system tenant
        UserContext.clear();
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", TENANT_SYSTEM)
                .build());

        // system tenant 不应看到 other tenant 的数据
        IPage<SysDictTypeVO> page = dictTypeService.getPage(1, 100, "other_tenant_type", null, null);
        assertEquals(0, page.getTotal(), "system tenant 不应看到其他租户的数据");

        // getById 也应被拒绝
        assertThrows(BusinessException.class, () -> dictTypeService.getById(otherId));
    }

    // ═══════════════════════════════════════════════════════════
    // SysDictData 测试
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(8)
    @DisplayName("T8: 按字典类型分页查询字典数据—验证种子数据")
    void test08_listDictData_byDictType() {
        // 项目状态 dict_type_id=1001，种子数据应有5条
        IPage<SysDictDataVO> page = dictDataService.getPage(1, 20, 1001L, null, null);
        assertEquals(5, page.getTotal(), "project_status 应有5条种子数据");
        // local profile uses baseline fixtures; V216 normalization is covered by Flyway smoke tests.
        assertEquals("草稿", page.getRecords().get(0).getDictLabel());
    }

    @Test
    @Order(9)
    @DisplayName("T9: 按ID获取字典数据")
    void test09_getDictDataById() {
        // local profile seed data id=100101
        SysDictDataVO vo = dictDataService.getById(100101L);
        assertNotNull(vo);
        assertEquals("草稿", vo.getDictLabel());
        assertEquals("DRAFT", vo.getDictValue());
    }

    @Test
    @Order(10)
    @DisplayName("T10: 创建字典数据—成功")
    @Transactional
    void test10_createDictData_success() {
        SysDictData entity = new SysDictData();
        entity.setDictTypeId(1001L); // project_status
        entity.setDictLabel("测试中");
        entity.setDictValue("TESTING");
        entity.setOrderNum(99);

        Long id = dictDataService.create(entity);
        assertNotNull(id);
        createdDataId = id;

        SysDictDataVO saved = dictDataService.getById(id);
        assertEquals("测试中", saved.getDictLabel());
        assertEquals("ENABLE", saved.getStatus());
    }

    @Test
    @Order(11)
    @DisplayName("T11: 创建字典数据—同类型下键值重复应抛异常")
    @Transactional
    void test11_createDictData_duplicateValue() {
        SysDictData entity = new SysDictData();
        entity.setDictTypeId(1001L);
        entity.setDictValue("DRAFT"); // 种子数据已存在
        entity.setDictLabel("重复草稿");

        assertThrows(BusinessException.class, () -> dictDataService.create(entity));
    }

    @Test
    @Order(12)
    @DisplayName("T12: 更新字典数据")
    @Transactional
    void test12_updateDictData() {
        SysDictType type = new SysDictType();
        type.setDictCode("test_update_status");
        type.setDictName("测试更新状态");
        Long typeId = dictTypeService.create(type);

        SysDictData initial = new SysDictData();
        initial.setDictTypeId(typeId);
        initial.setDictLabel("原标签");
        initial.setDictValue("CLOSED");
        Long dataId = dictDataService.create(initial);

        SysDictData entity = new SysDictData();
        entity.setId(dataId);
        entity.setDictTypeId(typeId);
        entity.setDictLabel("已关闭（已修改）");
        entity.setDictValue("CLOSED");
        entity.setStatus("DISABLE");

        dictDataService.update(entity);

        SysDictDataVO updated = dictDataService.getById(dataId);
        assertEquals("已关闭（已修改）", updated.getDictLabel());
        assertEquals("DISABLE", updated.getStatus());
    }

    @Test
    @Order(13)
    @DisplayName("T13: 删除字典数据")
    @Transactional
    void test13_deleteDictData() {
        SysDictType type = new SysDictType();
        type.setDictCode("test_delete_status");
        type.setDictName("测试删除状态");
        Long typeId = dictTypeService.create(type);

        SysDictData entity = new SysDictData();
        entity.setDictTypeId(typeId);
        entity.setDictLabel("待删除项");
        entity.setDictValue("TO_DELETE");
        Long id = dictDataService.create(entity);

        assertDoesNotThrow(() -> dictDataService.delete(id));
        assertThrows(BusinessException.class, () -> dictDataService.getById(id));
    }

    @Test
    @Order(14)
    @DisplayName("T14: 租户隔离—禁止跨租户挂载字典数据")
    @Transactional
    void test14_tenantIsolation_data() {
        // other tenant 不能把数据挂到 system tenant 的类型下
        UserContext.clear();
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", TENANT_OTHER)
                .build());

        SysDictData entity = new SysDictData();
        entity.setDictTypeId(1001L);
        entity.setDictLabel("其他租户数据");
        entity.setDictValue("OTHER_TENANT");
        assertThrows(BusinessException.class, () -> dictDataService.create(entity));
        assertFalse(dictDataService.getByDictCode("project_status").isEmpty(),
                "租户未覆盖字典时应只读回退到系统字典");
    }

    @Test
    @Order(15)
    @DisplayName("T15: 字典类型按名称模糊搜索")
    void test15_searchDictTypeByName() {
        IPage<SysDictTypeVO> page = dictTypeService.getPage(1, 20, null, "合同", null);
        assertTrue(page.getTotal() >= 1, "搜索'合同'应至少匹配 contract_type 和 contract_status");
    }

    @Test
    @Order(16)
    @DisplayName("T16: 字典类型按状态筛选")
    void test16_filterDictTypeByStatus() {
        IPage<SysDictTypeVO> page = dictTypeService.getPage(1, 20, null, null, "ENABLE");
        assertTrue(page.getTotal() >= 7, "ENABLE 状态应至少有7条");
    }

    @Test
    @Order(17)
    @DisplayName("T17: 字典编码和值创建后不可修改")
    @Transactional
    void test17_immutableKeys() {
        SysDictType type = new SysDictType();
        type.setDictCode("immutable_type");
        type.setDictName("不可变类型");
        Long typeId = dictTypeService.create(type);

        SysDictType changedType = new SysDictType();
        changedType.setId(typeId);
        changedType.setDictCode("changed_type");
        changedType.setDictName("已修改");
        assertThrows(BusinessException.class, () -> dictTypeService.update(changedType));

        SysDictData data = new SysDictData();
        data.setDictTypeId(typeId);
        data.setDictLabel("固定值");
        data.setDictValue("FIXED");
        Long dataId = dictDataService.create(data);

        SysDictData changedData = new SysDictData();
        changedData.setId(dataId);
        changedData.setDictTypeId(typeId);
        changedData.setDictLabel("修改标签允许");
        changedData.setDictValue("CHANGED");
        assertThrows(BusinessException.class, () -> dictDataService.update(changedData));
        assertThrows(BusinessException.class, () -> dictTypeService.delete(typeId));
    }

    @Test
    @Order(18)
    @DisplayName("T18: 系统字典变更后清除所有租户的只读回退缓存")
    @Transactional
    void test18_systemDictionaryCacheInvalidation() {
        SysDictType type = new SysDictType();
        type.setDictCode("cache_invalidation_status");
        type.setDictName("缓存失效状态");
        Long typeId = dictTypeService.create(type);

        SysDictData data = new SysDictData();
        data.setDictTypeId(typeId);
        data.setDictLabel("旧标签");
        data.setDictValue("FIXED");
        Long dataId = dictDataService.create(data);

        setTenant(TENANT_OTHER);
        assertEquals("旧标签", dictDataService.getByDictCodeCached(type.getDictCode()).getFirst().getDictLabel());

        setTenant(TENANT_SYSTEM);
        SysDictData updated = new SysDictData();
        updated.setId(dataId);
        updated.setDictTypeId(typeId);
        updated.setDictLabel("新标签");
        updated.setDictValue("FIXED");
        dictDataService.update(updated);

        setTenant(TENANT_OTHER);
        assertEquals("新标签", dictDataService.getByDictCodeCached(type.getDictCode()).getFirst().getDictLabel());

        setTenant(TENANT_SYSTEM);
        SysDictType disabled = new SysDictType();
        disabled.setId(typeId);
        disabled.setDictCode(type.getDictCode());
        disabled.setDictName(type.getDictName());
        disabled.setStatus("DISABLE");
        dictTypeService.update(disabled);

        setTenant(TENANT_OTHER);
        assertTrue(dictDataService.getByDictCodeCached(type.getDictCode()).isEmpty());
    }

    @Test
    @Order(19)
    @DisplayName("T19: 核心字典忽略租户同名影子且禁止新建覆盖")
    @Transactional
    void test19_coreDictionaryUsesSystemAuthority() {
        jdbc.update("INSERT INTO sys_dict_type(id,tenant_id,dict_code,dict_name,status) VALUES(990101,?,'project_type','租户伪项目类型','ENABLE')", TENANT_OTHER);
        jdbc.update("INSERT INTO sys_dict_data(id,tenant_id,dict_type_id,dict_label,dict_value,order_num,status) VALUES(990102,?,990101,'租户伪值','TENANT_FAKE',1,'ENABLE')", TENANT_OTHER);

        setTenant(TENANT_OTHER);
        var values = dictDataService.getByDictCodeCached("project_type");
        assertTrue(values.stream().anyMatch(row -> "CONSTRUCTION".equals(row.getDictValue())));
        assertTrue(values.stream().noneMatch(row -> "TENANT_FAKE".equals(row.getDictValue())));

        SysDictType duplicate = new SysDictType();
        duplicate.setDictCode("contract_type");
        duplicate.setDictName("租户合同类型");
        BusinessException error = assertThrows(BusinessException.class, () -> dictTypeService.create(duplicate));
        assertEquals("DICT_CORE_TYPE_TENANT_OVERRIDE_FORBIDDEN", error.getCode());
    }

    @Test
    @Order(20)
    @DisplayName("T20: 核心字典值禁止停用和删除")
    @Transactional
    void test20_coreDictionaryValueProtected() {
        SysDictData disabled = new SysDictData();
        disabled.setId(132001L);
        disabled.setDictTypeId(132000L);
        disabled.setDictLabel("施工总承包");
        disabled.setDictValue("CONSTRUCTION");
        disabled.setStatus("DISABLE");
        assertEquals("DICT_CORE_VALUE_DISABLE_FORBIDDEN",
                assertThrows(BusinessException.class, () -> dictDataService.update(disabled)).getCode());
        assertEquals("DICT_CORE_VALUE_DELETE_FORBIDDEN",
                assertThrows(BusinessException.class, () -> dictDataService.delete(132001L)).getCode());
    }

    private void setTenant(long tenantId) {
        UserContext.clear();
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", tenantId)
                .build());
    }
}
