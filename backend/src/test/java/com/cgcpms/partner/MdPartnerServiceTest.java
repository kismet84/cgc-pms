package com.cgcpms.partner;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.partner.service.MdPartnerService;
import com.cgcpms.partner.vo.MdPartnerVO;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
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

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true", "spring.main.lazy-initialization=true"})
@ActiveProfiles("local")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MdPartnerServiceTest {

    private static final long USER_ADMIN = 1L;
    private static final long TENANT_0 = 0L;

    @Autowired
    private MdPartnerService partnerService;

    @Autowired
    private MdPartnerMapper partnerMapper;

    @Autowired
    private CtContractMapper ctContractMapper;

    @Autowired
    private PmProjectMapper projectMapper;

    private Long createdPartnerId;

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

    /** 创建一个虚拟项目用于合同关联测试 */
    private Long createDummyProject() {
        PmProject project = new PmProject();
        project.setProjectCode("PRJ-TEST-" + System.nanoTime());
        project.setProjectName("测试项目");
        project.setTenantId(TENANT_0);
        project.setContractAmount(BigDecimal.ZERO);
        project.setStatus("DRAFT");
        projectMapper.insert(project);
        return project.getId();
    }

    // ═══════════════════════════════════════════════════════════
    // Create tests
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @Transactional
    @DisplayName("创建合作方 — 自动生成 PTN-yyyyMMdd-XXX 编号")
    void testCreate_AutoGenerateCode() {
        MdPartner partner = new MdPartner();
        partner.setPartnerName("测试合作方A");
        partner.setPartnerType("SUPPLIER");
        partner.setTenantId(TENANT_0);

        Long id = partnerService.create(partner);
        assertNotNull(id, "创建后应返回ID");
        createdPartnerId = id;

        MdPartner saved = partnerMapper.selectById(id);
        assertNotNull(saved, "应能查询到刚创建的合作方");
        assertNotNull(saved.getPartnerCode(), "编号应自动生成");
        assertTrue(saved.getPartnerCode().startsWith("PTN-"), "编号应以PTN-开头");
        assertEquals("ENABLE", saved.getStatus(), "默认状态应为ENABLE");
        assertEquals("测试合作方A", saved.getPartnerName());

        System.out.println("✅ testCreate_AutoGenerateCode 通过: partnerCode=" + saved.getPartnerCode());
    }

    @Test
    @Order(2)
    @Transactional
    @DisplayName("创建合作方 — 编码重复校验抛出 PARTNER_CODE_EXISTS")
    void testCreate_DuplicateCode() {
        // 第一个，手动指定编码
        MdPartner p1 = new MdPartner();
        p1.setPartnerCode("PTN-DUP-001");
        p1.setPartnerName("重复测试1");
        p1.setPartnerType("SUPPLIER");
        partnerService.create(p1);

        // 第二个，相同编码
        MdPartner p2 = new MdPartner();
        p2.setPartnerCode("PTN-DUP-001");
        p2.setPartnerName("重复测试2");
        p2.setPartnerType("SUPPLIER");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> partnerService.create(p2),
                "重复编码应抛出BusinessException");
        assertEquals("PARTNER_CODE_EXISTS", ex.getCode());

        System.out.println("✅ testCreate_DuplicateCode 通过: code=" + ex.getCode());
    }

    @Test
    @Order(3)
    @Transactional
    @DisplayName("创建合作方 — 未指定status时默认为ENABLE")
    void testCreate_DefaultStatusEnable() {
        MdPartner partner = new MdPartner();
        partner.setPartnerName("默认状态合作方");
        partner.setPartnerType("CUSTOMER");

        Long id = partnerService.create(partner);
        MdPartner saved = partnerMapper.selectById(id);
        assertEquals("ENABLE", saved.getStatus(), "未指定status时应默认为ENABLE");

        System.out.println("✅ testCreate_DefaultStatusEnable 通过: status=" + saved.getStatus());
    }

    @Test
    @Order(4)
    @Transactional
    @DisplayName("创建合作方 — 编号序列自增")
    void testCreate_CodeSequenceIncrement() {
        // 第一个不指定编码，自动生成 PTN-yyyyMMdd-001
        MdPartner p1 = new MdPartner();
        p1.setPartnerName("序列测试1");
        p1.setPartnerType("SUPPLIER");
        partnerService.create(p1);

        MdPartner saved1 = partnerMapper.selectById(p1.getId());
        assertNotNull(saved1.getPartnerCode());
        assertTrue(saved1.getPartnerCode().endsWith("001"), "第一个编号应以001结尾");

        // 第二个也不指定编码，应生成 *-002
        MdPartner p2 = new MdPartner();
        p2.setPartnerName("序列测试2");
        p2.setPartnerType("SUPPLIER");
        partnerService.create(p2);

        MdPartner saved2 = partnerMapper.selectById(p2.getId());
        assertNotNull(saved2.getPartnerCode());
        assertTrue(saved2.getPartnerCode().endsWith("002"), "第二个编号应以002结尾");

        System.out.println("✅ testCreate_CodeSequenceIncrement 通过: code1="
                + saved1.getPartnerCode() + ", code2=" + saved2.getPartnerCode());
    }

    // ═══════════════════════════════════════════════════════════
    // Delete tests
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(5)
    @Transactional
    @DisplayName("删除合作方 — 无合同关联时成功删除")
    void testDelete_NoContracts_Success() {
        MdPartner partner = new MdPartner();
        partner.setPartnerName("待删除合作方");
        partner.setPartnerType("SUPPLIER");
        Long id = partnerService.create(partner);

        assertDoesNotThrow(() -> partnerService.delete(id), "无合同关联时删除不应抛异常");

        // 验证被逻辑删除
        MdPartner deleted = partnerMapper.selectById(id);
        assertNull(deleted, "逻辑删除后应查不到记录");

        System.out.println("✅ testDelete_NoContracts_Success 通过");
    }

    @Test
    @Order(6)
    @Transactional
    @DisplayName("删除合作方 — 有合同关联时拒绝删除")
    void testDelete_HasContracts_Rejected() {
        // 创建合作方
        MdPartner partner = new MdPartner();
        partner.setPartnerName("有合同合作方");
        partner.setPartnerType("SUPPLIER");
        Long partnerId = partnerService.create(partner);

        // 创建一个虚拟项目
        Long projectId = createDummyProject();

        // 创建一个关联合同（作为 partyA）
        CtContract contract = new CtContract();
        contract.setContractCode("CT-REJ-" + System.nanoTime());
        contract.setContractName("测试合同");
        contract.setContractType("GENERAL_CONTRACT");
        contract.setPartyAId(partnerId);
        contract.setProjectId(projectId);
        contract.setTenantId(TENANT_0);
        contract.setContractAmount(BigDecimal.ZERO);
        ctContractMapper.insert(contract);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> partnerService.delete(partnerId),
                "有合同关联时删除应抛出BusinessException");
        assertEquals("PARTNER_HAS_CONTRACTS", ex.getCode());

        System.out.println("✅ testDelete_HasContracts_Rejected 通过: code=" + ex.getCode());
    }

    @Test
    @Order(7)
    @Transactional
    @DisplayName("删除合作方 — 作为partyB关联合同时也拒绝删除")
    void testDelete_HasContractsAsPartyB_Rejected() {
        MdPartner partner = new MdPartner();
        partner.setPartnerName("partyB合作方");
        partner.setPartnerType("CUSTOMER");
        Long partnerId = partnerService.create(partner);

        Long projectId = createDummyProject();

        CtContract contract = new CtContract();
        contract.setContractCode("CT-REJB-" + System.nanoTime());
        contract.setContractName("测试合同B");
        contract.setContractType("GENERAL_CONTRACT");
        contract.setPartyBId(partnerId);
        contract.setProjectId(projectId);
        contract.setTenantId(TENANT_0);
        contract.setContractAmount(BigDecimal.ZERO);
        ctContractMapper.insert(contract);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> partnerService.delete(partnerId));
        assertEquals("PARTNER_HAS_CONTRACTS", ex.getCode());

        System.out.println("✅ testDelete_HasContractsAsPartyB_Rejected 通过");
    }

    @Test
    @Order(8)
    @Transactional
    @DisplayName("删除不存在的合作方 — 抛出 PARTNER_NOT_FOUND")
    void testDelete_NotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> partnerService.delete(999999L));
        assertEquals("PARTNER_NOT_FOUND", ex.getCode());

        System.out.println("✅ testDelete_NotFound 通过");
    }

    // ═══════════════════════════════════════════════════════════
    // Update tests
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(9)
    @Transactional
    @DisplayName("更新合作方 — 正常更新名称和状态")
    void testUpdate_Success() {
        MdPartner partner = new MdPartner();
        partner.setPartnerName("原始名称");
        partner.setPartnerType("SUPPLIER");
        Long id = partnerService.create(partner);

        MdPartner update = new MdPartner();
        update.setId(id);
        update.setPartnerName("更新后名称");
        update.setPartnerType("SUPPLIER");
        update.setPartnerCode(partner.getPartnerCode());
        update.setStatus("DISABLE");
        partnerService.update(update);

        MdPartnerVO vo = partnerService.getById(id);
        assertEquals("更新后名称", vo.getPartnerName());
        assertEquals("DISABLE", vo.getStatus());

        System.out.println("✅ testUpdate_Success 通过: name=" + vo.getPartnerName() + ", status=" + vo.getStatus());
    }

    @Test
    @Order(10)
    @Transactional
    @DisplayName("更新合作方 — 租户隔离（其他租户的不让改）")
    void testUpdate_CrossTenantIsolation() {
        // 在 tenant 0 下创建
        MdPartner partner = new MdPartner();
        partner.setPartnerName("跨租户测试合作方");
        partner.setPartnerType("SUPPLIER");
        Long id = partnerService.create(partner);

        // 切换到 tenant 999
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 999L)
                .build());

        MdPartner update = new MdPartner();
        update.setId(id);
        update.setPartnerName("恶意修改名称");
        update.setPartnerType("SUPPLIER");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> partnerService.update(update),
                "其他租户更新应抛出BusinessException");
        assertEquals("PARTNER_NOT_FOUND", ex.getCode());

        System.out.println("✅ testUpdate_CrossTenantIsolation 通过: code=" + ex.getCode());
    }

    @Test
    @Order(11)
    @Transactional
    @DisplayName("更新不存在的合作方 — 抛出 PARTNER_NOT_FOUND")
    void testUpdate_NotFound() {
        MdPartner update = new MdPartner();
        update.setId(999999L);
        update.setPartnerName("不存在的合作方");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> partnerService.update(update));
        assertEquals("PARTNER_NOT_FOUND", ex.getCode());

        System.out.println("✅ testUpdate_NotFound 通过");
    }

    @Test
    @Order(11)
    @Transactional
    @DisplayName("供应商默认提前期 — 创建、旧客户端省略、显式清空与非供应商归零")
    void testSupplierDefaultLeadDaysLifecycle() {
        MdPartner supplier = new MdPartner();
        supplier.setPartnerName("提前期供应商");
        supplier.setPartnerType("SUPPLIER");
        supplier.setDefaultLeadDays(BigDecimal.valueOf(7));
        Long id = partnerService.create(supplier);
        assertEquals(7, partnerService.getById(id).getDefaultLeadDays());

        MdPartner legacyUpdate = new MdPartner();
        legacyUpdate.setId(id);
        legacyUpdate.setPartnerName("提前期供应商-旧客户端更新");
        legacyUpdate.setPartnerType("SUPPLIER");
        partnerService.update(legacyUpdate);
        assertEquals(7, partnerService.getById(id).getDefaultLeadDays(), "省略新字段必须保留旧值");

        MdPartner clearUpdate = new MdPartner();
        clearUpdate.setId(id);
        clearUpdate.setPartnerName("提前期供应商-清空");
        clearUpdate.setPartnerType("SUPPLIER");
        clearUpdate.setDefaultLeadDays(null);
        partnerService.update(clearUpdate);
        assertNull(partnerService.getById(id).getDefaultLeadDays(), "显式 null 必须清空默认提前期");

        MdPartner customer = new MdPartner();
        customer.setPartnerName("非供应商");
        customer.setPartnerType("CUSTOMER");
        customer.setDefaultLeadDays(BigDecimal.TEN);
        Long customerId = partnerService.create(customer);
        assertNull(partnerService.getById(customerId).getDefaultLeadDays(), "非供应商不得保留默认提前期");
    }

    @Test
    @Order(11)
    @Transactional
    @DisplayName("供应商默认提前期 — 服务层拒绝小数、负数与越界")
    void testSupplierDefaultLeadDaysRejectsInvalidValues() {
        for (BigDecimal invalid : List.of(new BigDecimal("1.5"), BigDecimal.valueOf(-1), BigDecimal.valueOf(3651))) {
            MdPartner supplier = new MdPartner();
            supplier.setPartnerName("非法提前期" + invalid);
            supplier.setPartnerType("SUPPLIER");
            supplier.setDefaultLeadDays(invalid);
            BusinessException error = assertThrows(BusinessException.class, () -> partnerService.create(supplier));
            assertEquals("INVALID_PARTNER_DEFAULT_LEAD_DAYS", error.getCode());
        }
    }

    // ═══════════════════════════════════════════════════════════
    // getPage tests
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(12)
    @Transactional
    @DisplayName("分页查询 — 全量分页")
    void testGetPage_All() {
        MdPartner p1 = new MdPartner();
        p1.setPartnerName("分页测试A");
        p1.setPartnerType("SUPPLIER");
        partnerService.create(p1);

        MdPartner p2 = new MdPartner();
        p2.setPartnerName("分页测试B");
        p2.setPartnerType("CUSTOMER");
        partnerService.create(p2);

        IPage<MdPartnerVO> page = partnerService.getPage(1, 10, null, null, null, null);
        assertTrue(page.getTotal() >= 2, "至少应有2条记录");
        assertTrue(page.getRecords().size() >= 2, "记录数应 >= 2");

        System.out.println("✅ testGetPage_All 通过: total=" + page.getTotal());
    }

    @Test
    @Order(13)
    @Transactional
    @DisplayName("分页查询 — 按名称模糊搜索")
    void testGetPage_FilterByName() {
        MdPartner p1 = new MdPartner();
        p1.setPartnerName("唯一搜索名称XYZ");
        p1.setPartnerType("SUPPLIER");
        partnerService.create(p1);

        MdPartner p2 = new MdPartner();
        p2.setPartnerName("其他合作方");
        p2.setPartnerType("CUSTOMER");
        partnerService.create(p2);

        IPage<MdPartnerVO> page = partnerService.getPage(1, 10, null, "唯一搜索", null, null);
        assertTrue(page.getTotal() >= 1, "按名称模糊搜索应有结果");
        assertTrue(page.getRecords().stream()
                .anyMatch(v -> v.getPartnerName().contains("唯一搜索")),
                "搜索结果应包含匹配的记录");

        System.out.println("✅ testGetPage_FilterByName 通过: total=" + page.getTotal());
    }

    @Test
    @Order(14)
    @Transactional
    @DisplayName("分页查询 — 按编码模糊搜索")
    void testGetPage_FilterByCode() {
        MdPartner p1 = new MdPartner();
        p1.setPartnerCode("FLT-CODE-ABC");
        p1.setPartnerName("编码过滤测试");
        p1.setPartnerType("SUPPLIER");
        partnerService.create(p1);

        IPage<MdPartnerVO> page = partnerService.getPage(1, 10, "FLT-CODE", null, null, null);
        assertTrue(page.getTotal() >= 1, "按编码模糊搜索应有结果");

        System.out.println("✅ testGetPage_FilterByCode 通过: total=" + page.getTotal());
    }

    @Test
    @Order(15)
    @Transactional
    @DisplayName("分页查询 — 按类型筛选")
    void testGetPage_FilterByType() {
        MdPartner supplier = new MdPartner();
        supplier.setPartnerName("类型筛选-供应商");
        supplier.setPartnerType("SUPPLIER");
        partnerService.create(supplier);

        MdPartner customer = new MdPartner();
        customer.setPartnerName("类型筛选-客户");
        customer.setPartnerType("CUSTOMER");
        partnerService.create(customer);

        IPage<MdPartnerVO> page = partnerService.getPage(1, 10, null, null, "SUPPLIER", null);
        assertTrue(page.getTotal() >= 1, "按SUPPLIER筛选应有结果");
        assertTrue(page.getRecords().stream()
                .allMatch(v -> "SUPPLIER".equals(v.getPartnerType())),
                "所有结果都应是SUPPLIER类型");

        System.out.println("✅ testGetPage_FilterByType 通过: total=" + page.getTotal());
    }

    @Test
    @Order(16)
    @Transactional
    @DisplayName("分页查询 — 按状态筛选")
    void testGetPage_FilterByStatus() {
        MdPartner enable = new MdPartner();
        enable.setPartnerName("状态筛选-启用");
        enable.setPartnerType("SUPPLIER");
        enable.setStatus("ENABLE");
        partnerService.create(enable);

        MdPartner disable = new MdPartner();
        disable.setPartnerName("状态筛选-禁用");
        disable.setPartnerType("SUPPLIER");
        disable.setStatus("DISABLE");
        partnerService.create(disable);

        IPage<MdPartnerVO> pageEnable = partnerService.getPage(1, 10, null, null, null, "ENABLE");
        assertTrue(pageEnable.getTotal() >= 1, "按ENABLE筛选应有结果");
        assertTrue(pageEnable.getRecords().stream()
                .allMatch(v -> "ENABLE".equals(v.getStatus())));

        IPage<MdPartnerVO> pageDisable = partnerService.getPage(1, 10, null, null, null, "DISABLE");
        assertTrue(pageDisable.getTotal() >= 1, "按DISABLE筛选应有结果");
        assertTrue(pageDisable.getRecords().stream()
                .allMatch(v -> "DISABLE".equals(v.getStatus())));

        System.out.println("✅ testGetPage_FilterByStatus 通过: enable="
                + pageEnable.getTotal() + ", disable=" + pageDisable.getTotal());
    }

    // ═══════════════════════════════════════════════════════════
    // getById / 跨租户隔离
    // ═══════════════════════════════════════════════════════════

    @Test
    @Order(17)
    @Transactional
    @DisplayName("查询详情 — 跨租户隔离（其他租户的查不到）")
    void testGetById_CrossTenantIsolation() {
        MdPartner partner = new MdPartner();
        partner.setPartnerName("隔离查询合作方");
        partner.setPartnerType("SUPPLIER");
        Long id = partnerService.create(partner);

        // 切换到 tenant 777
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 777L)
                .build());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> partnerService.getById(id));
        assertEquals("PARTNER_NOT_FOUND", ex.getCode());

        System.out.println("✅ testGetById_CrossTenantIsolation 通过");
    }

    @Test
    @Order(18)
    @Transactional
    @DisplayName("查询不存在的合作方 — 抛出 PARTNER_NOT_FOUND")
    void testGetById_NotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> partnerService.getById(999999L));
        assertEquals("PARTNER_NOT_FOUND", ex.getCode());

        System.out.println("✅ testGetById_NotFound 通过");
    }
}
