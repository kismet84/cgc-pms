package com.cgcpms.contract;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.constant.ContractStatusConstants;
import com.cgcpms.contract.dto.ContractSaveRequest;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.entity.CtContractItem;
import com.cgcpms.contract.entity.CtContractPaymentTerm;
import com.cgcpms.contract.mapper.CtContractItemMapper;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.contract.mapper.CtContractPaymentTermMapper;
import com.cgcpms.contract.service.CtContractService;
import com.cgcpms.contract.vo.ContractApprovalRecordVO;
import com.cgcpms.contract.vo.CtContractVO;
import com.cgcpms.common.util.DateTimeUtils;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import com.cgcpms.system.entity.SysUser;
import com.cgcpms.system.mapper.SysUserMapper;
import com.cgcpms.workflow.entity.WfInstance;
import com.cgcpms.workflow.mapper.WfInstanceMapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CtContractService 单元测试。
 * <p>
 * 覆盖: 分页查询、KPI 聚合、按ID查询、创建、更新、删除、复合保存、
 * 提交审批、审批记录查询等核心操作。
 * 使用 H2 内存数据库，V90 seed 提供 contract 30001/30002/30003。
 * 写入类测试通过 @BeforeEach 创建 DRAFT 合同，确保事务隔离。
 */
@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@DisplayName("CtContractService — 合同 CRUD 与审批测试")
class CtContractServiceTest {

    private static final long TENANT_ID = 0L;
    private static final long USER_ADMIN = 1L;
    private static final long USER_PROJECT_OWNER = 2L;
    private static final long USER_NO_PROJECT_ACCESS = 3L;
    private static final long PROJECT_ID = 10001L;
    private static final long PARTY_A_ID = 20001L;
    private static final long PARTY_B_ID = 20002L;

    /** V90 seed contract 30001 — PERFORMING, APPROVED (只读操作可用) */
    private static final long SEED_CONTRACT_30001 = 30001L;

    @Autowired
    private CtContractService contractService;

    @Autowired
    private CtContractMapper contractMapper;

    @Autowired
    private CtContractItemMapper itemMapper;

    @Autowired
    private CtContractPaymentTermMapper paymentTermMapper;

    @Autowired
    private PmProjectMapper projectMapper;

    @Autowired
    private MdPartnerMapper partnerMapper;

    @Autowired
    private WfInstanceMapper wfInstanceMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

    @BeforeEach
    void setupContext() {
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", TENANT_ID)
                .add("roleCodes", List.of("ADMIN"))
                .build());
        seedReferenceData();
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    /** Ensure project 10001 + partners 20001/20002 + admin user exist for foreign-key references. */
    private void seedReferenceData() {
        if (projectMapper.selectById(PROJECT_ID) == null) {
            PmProject project = new PmProject();
            project.setId(PROJECT_ID);
            project.setProjectCode("PRJ-TEST-SVC");
            project.setProjectName("合同服务测试项目");
            project.setProjectType("CONSTRUCTION");
            project.setContractAmount(new BigDecimal("5000000.00"));
            project.setTargetCost(new BigDecimal("4200000.00"));
            project.setStatus("RUNNING");
            project.setApprovalStatus("APPROVED");
            projectMapper.insert(project);
        }

        if (partnerMapper.selectById(PARTY_A_ID) == null) {
            MdPartner partyA = new MdPartner();
            partyA.setId(PARTY_A_ID);
            partyA.setPartnerCode("PT-TEST-SVC-A");
            partyA.setPartnerName("服务测试甲方");
            partyA.setPartnerType("PARTY_A");
            partyA.setBlacklistFlag(0);
            partyA.setStatus("ENABLE");
            partnerMapper.insert(partyA);
        }

        if (partnerMapper.selectById(PARTY_B_ID) == null) {
            MdPartner partyB = new MdPartner();
            partyB.setId(PARTY_B_ID);
            partyB.setPartnerCode("PT-TEST-SVC-B");
            partyB.setPartnerName("服务测试乙方");
            partyB.setPartnerType("PARTY_B");
            partyB.setBlacklistFlag(0);
            partyB.setStatus("ENABLE");
            partnerMapper.insert(partyB);
        }

        // Workflow engine (ApproverResolver) needs sys_user id=1 to match template node approverConfig
        if (sysUserMapper.selectById(USER_ADMIN) == null) {
            SysUser admin = new SysUser();
            admin.setId(USER_ADMIN);
            admin.setTenantId(TENANT_ID);
            admin.setUsername("admin");
            admin.setPassword("encoded");
            admin.setRealName("系统管理员");
            admin.setStatus("ENABLE");
            admin.setIsAdmin(1);
            sysUserMapper.insert(admin);
        }
    }

    /** Build a minimal DRAFT contract (not yet inserted). */
    private CtContract buildDraftContract(String contractName) {
        CtContract contract = new CtContract();
        contract.setProjectId(PROJECT_ID);
        contract.setContractName(contractName);
        contract.setContractType("SUB");
        contract.setPartyAId(PARTY_A_ID);
        contract.setPartyBId(PARTY_B_ID);
        contract.setContractAmount(new BigDecimal("640000.00"));
        contract.setCurrentAmount(new BigDecimal("640000.00"));
        contract.setPaidAmount(BigDecimal.ZERO);
        contract.setTaxRate(new BigDecimal("13.00"));
        contract.setTaxAmount(new BigDecimal("73628.32"));
        contract.setAmountWithoutTax(new BigDecimal("566371.68"));
        contract.setSignedDate(LocalDate.now());
        contract.setStartDate(LocalDate.of(2026, 1, 1));
        contract.setEndDate(LocalDate.of(2026, 12, 31));
        contract.setPaymentMethod("银行转账");
        contract.setSettlementMethod("按进度结算");
        contract.setContractStatus("DRAFT");
        contract.setApprovalStatus("DRAFT");
        return contract;
    }

    /** Insert a DRAFT contract and return its ID. */
    private Long insertDraftContract(String contractName) {
        CtContract contract = buildDraftContract(contractName);
        contract.setContractCode("CT-TEST-MANUAL-" + System.nanoTime());
        contract.setTenantId(TENANT_ID);
        contractMapper.insert(contract);
        return contract.getId();
    }

    /** Insert a DRAFT contract via create() (generated code). */
    private Long createDraftContract(String contractName) {
        CtContract contract = buildDraftContract(contractName);
        return contractService.create(contract);
    }

    private CtContractItem buildItem(String code, String name, BigDecimal qty, BigDecimal price) {
        CtContractItem item = new CtContractItem();
        item.setItemCode(code);
        item.setItemName(name);
        item.setItemSpec("标准规格");
        item.setUnit("m³");
        item.setQuantity(qty);
        item.setUnitPrice(price);
        item.setAmount(qty.multiply(price));
        item.setTaxRate(new BigDecimal("13.00"));
        item.setTaxAmount(qty.multiply(price).multiply(new BigDecimal("0.13")));
        item.setAmountWithoutTax(qty.multiply(price).multiply(new BigDecimal("0.87")));
        item.setSortOrder(1);
        return item;
    }

    private CtContractPaymentTerm buildTerm(String name, BigDecimal ratio, int order) {
        CtContractPaymentTerm term = new CtContractPaymentTerm();
        term.setTermName(name);
        term.setPaymentRatio(ratio);
        term.setPaymentAmount(new BigDecimal("640000.00").multiply(ratio).divide(new BigDecimal("100")));
        term.setPaymentCondition("工程进度达到约定节点");
        term.setPlannedDate(LocalDate.now().plusMonths(order));
        term.setTermStatus("PENDING");
        term.setSortOrder(order);
        return term;
    }

    private int parseCodeSuffix(String code, String prefix) {
        if (code == null || !code.startsWith(prefix)) {
            return 0;
        }
        if (code.length() <= prefix.length()) {
            return 0;
        }
        try {
            return Integer.parseInt(code.substring(prefix.length()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 分页查询
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("分页查询 — 无过滤条件返回全部合同")
    void testGetPageNoFilter() {
        IPage<CtContractVO> page = contractService.getPage(1, 20,
                null, null, null, null, null, null, null, null, null);
        assertNotNull(page);
        assertTrue(page.getTotal() >= 3, "V90 seed 至少包含 3 条合同");
    }

    @Test
    @Transactional
    @DisplayName("分页查询 — 按合同类型精确过滤")
    void testGetPageByContractType() {
        IPage<CtContractVO> page = contractService.getPage(1, 20,
                null, null, null, "SUB", null, null, null, null, null);
        assertNotNull(page);
        assertTrue(page.getTotal() >= 1, "应至少有一条 SUB 类型合同");
        for (CtContractVO vo : page.getRecords()) {
            assertEquals("SUB", vo.getContractType());
        }
    }

    @Test
    @Transactional
    @DisplayName("分页查询 — 按审批状态过滤")
    void testGetPageByApprovalStatus() {
        IPage<CtContractVO> page = contractService.getPage(1, 20,
                null, null, null, null, null, "APPROVED", null, null, null);
        assertNotNull(page);
        assertTrue(page.getTotal() >= 2, "seed 中 30001/30002/30003 均为 APPROVED");
        for (CtContractVO vo : page.getRecords()) {
            assertEquals("APPROVED", vo.getApprovalStatus());
        }
    }

    @Test
    @Transactional
    @DisplayName("分页查询 — 按 projectId 过滤")
    void testGetPageByProjectId() {
        IPage<CtContractVO> page = contractService.getPage(1, 20,
                null, null, null, null, null, null, PROJECT_ID, null, null);
        assertNotNull(page);
        assertTrue(page.getTotal() >= 3);
        for (CtContractVO vo : page.getRecords()) {
            assertEquals(String.valueOf(PROJECT_ID), vo.getProjectId());
        }
    }

    @Test
    @Transactional
    @DisplayName("分页查询 — keyword 模糊搜索（匹配合同名称）")
    void testGetPageByKeyword() {
        IPage<CtContractVO> page = contractService.getPage(1, 20,
                "主体结构", null, null, null, null, null, null, null, null);
        assertNotNull(page);
        assertTrue(page.getTotal() >= 1, "应匹配到种子合同 30001");
        assertTrue(page.getRecords().stream()
                .anyMatch(v -> "主体结构施工合同".equals(v.getContractName())));
    }

    @Test
    @Transactional
    @DisplayName("分页查询 — 返回 VO 中 projectName/partyAName/partyBName 非空")
    void testGetPageRelatedNamesPopulated() {
        IPage<CtContractVO> page = contractService.getPage(1, 20,
                null, null, null, null, null, null, PROJECT_ID, null, null);
        assertTrue(page.getTotal() >= 1);
        CtContractVO vo = page.getRecords().get(0);
        assertNotNull(vo.getProjectName(), "projectName 应通过 batch-prefetch 填充");
        assertNotNull(vo.getPartyAName(), "partyAName 应通过 batch-prefetch 填充");
        assertNotNull(vo.getPartyBName(), "partyBName 应通过 batch-prefetch 填充");
    }

    @Test
    @Transactional
    @DisplayName("分页查询 — 按合同状态过滤 DRAFT 返回空或仅 DRAFT 记录")
    void testGetPageByContractStatusDraft() {
        IPage<CtContractVO> page = contractService.getPage(1, 20,
                null, null, null, null, "DRAFT", null, null, null, null);
        assertNotNull(page);
        for (CtContractVO vo : page.getRecords()) {
            assertEquals("DRAFT", vo.getContractStatus());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // KPI 聚合
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("KPI 聚合 — 无过滤返回汇总统计")
    void testGetKpiAll() {
        Map<String, Object> kpi = contractService.getKpi(
                null, null, null, null, null, null, null, null);
        assertNotNull(kpi);
        assertTrue(((Long) kpi.get("totalCount")) >= 3, "seed 中至少 3 条合同");
        // totalAmount >= 50,000,000 + 30,000,000 + 10,000,000
        String totalAmount = (String) kpi.get("totalAmount");
        assertNotNull(totalAmount);
        assertNotNull(kpi.get("paidAmount"));
        assertNotNull(kpi.get("unpaidAmount"));
        assertTrue(((Long) kpi.get("overdueCount")) >= 0, "overdueCount 不应为 null");
    }

    @Test
    @Transactional
    @DisplayName("KPI 聚合 — projectId 过滤后正确统计")
    void testGetKpiByProject() {
        Map<String, Object> kpi = contractService.getKpi(
                null, null, null, null, null, PROJECT_ID, null, null);
        assertNotNull(kpi);
        assertTrue(((Long) kpi.get("totalCount")) >= 3,
                "project 10001 应匹配全部 3 条 seed 合同");
    }

    @Test
    @Transactional
    @DisplayName("KPI 聚合 — 无匹配条件时返回零值统计")
    void testGetKpiEmptyResult() {
        Map<String, Object> kpi = contractService.getKpi(
                null, "不存在的合同", null, null, null, -999L, null, null);
        assertNotNull(kpi);
        assertEquals(0L, kpi.get("totalCount"));
        assertEquals("0", kpi.get("totalAmount"));
        assertEquals("0", kpi.get("paidAmount"));
    }

    // ═══════════════════════════════════════════════════════════════
    // 按ID查询
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("按ID查询 — 存在时返回完整 VO（含关联名称）")
    void testGetByIdFound() {
        CtContractVO vo = contractService.getById(SEED_CONTRACT_30001);
        assertNotNull(vo);
        assertEquals("CT-2026-001", vo.getContractCode());
        assertEquals("主体结构施工合同", vo.getContractName());
        assertEquals("SUB", vo.getContractType());
        assertNotNull(vo.getProjectName(), "projectName 应非空");
        assertNotNull(vo.getPartyAName(), "partyAName 应非空");
        assertNotNull(vo.getPartyBName(), "partyBName 应非空");
        assertEquals("PERFORMING", vo.getContractStatus());
        assertEquals("APPROVED", vo.getApprovalStatus());
    }

    @Test
    @Transactional
    @DisplayName("按ID查询 — 不存在时抛 CONTRACT_NOT_FOUND")
    void testGetByIdNotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> contractService.getById(-999L));
        assertEquals("CONTRACT_NOT_FOUND", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("按ID查询 — 租户隔离: 其他租户查不到")
    void testGetByIdTenantIsolation() {
        // 切换到其他租户
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 999L)
                .add("roleCodes", List.of("ADMIN"))
                .build());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> contractService.getById(SEED_CONTRACT_30001));
        assertEquals("CONTRACT_NOT_FOUND", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("按ID查询 — SELF 数据范围: 项目创建人可查看合同详情")
    void testGetByIdSelfProjectOwnerAllowed() {
        PmProject project = projectMapper.selectById(PROJECT_ID);
        project.setCreatedBy(USER_PROJECT_OWNER);
        projectMapper.updateById(project);

        UserContext.set(Jwts.claims()
                .add("userId", USER_PROJECT_OWNER)
                .add("username", "project-owner")
                .add("tenantId", TENANT_ID)
                .add("roleCodes", List.of())
                .build());

        CtContractVO vo = contractService.getById(SEED_CONTRACT_30001);
        assertEquals("CT-2026-001", vo.getContractCode());
    }

    @Test
    @Transactional
    @DisplayName("按ID查询 — SELF 数据范围: 非项目创建人被拒绝")
    void testGetByIdSelfNonOwnerDenied() {
        PmProject project = projectMapper.selectById(PROJECT_ID);
        project.setCreatedBy(USER_PROJECT_OWNER);
        projectMapper.updateById(project);

        UserContext.set(Jwts.claims()
                .add("userId", USER_NO_PROJECT_ACCESS)
                .add("username", "no-project-access")
                .add("tenantId", TENANT_ID)
                .add("roleCodes", List.of())
                .build());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> contractService.getById(SEED_CONTRACT_30001));
        assertEquals("PROJECT_ACCESS_DENIED", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════════
    // 创建
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("创建 — 成功生成合同编号并持久化，默认 DRAFT 状态")
    void testCreateSuccess() {
        CtContract contract = buildDraftContract("创建测试合同");
        Long id = contractService.create(contract);

        assertNotNull(id);
        CtContract saved = contractMapper.selectById(id);
        assertNotNull(saved);
        assertEquals("创建测试合同", saved.getContractName());
        assertNotNull(saved.getContractCode(), "合同编号应自动生成");
        assertTrue(saved.getContractCode().startsWith("CT-"), "编号应以 CT- 开头");
        assertEquals(ContractStatusConstants.APPROVAL_DRAFT, saved.getApprovalStatus());
        assertEquals(ContractStatusConstants.STATUS_DRAFT, saved.getContractStatus());
        assertEquals(TENANT_ID, saved.getTenantId());
    }

    @Test
    @Transactional
    @DisplayName("创建 — includeDeleted=true 时应兼容软删除编号历史")
    void testCreateUsesDeletedContractCode() {
        String codePrefix = "CT-" + LocalDate.now().format(DateTimeUtils.DATE_COMPACT) + "-";

        String maxExistingCode = contractMapper.selectLastCodeByPrefix(codePrefix, TENANT_ID);
        int maxSuffix = parseCodeSuffix(maxExistingCode, codePrefix);
        int deletedSuffix = maxSuffix + 20;

        CtContract deletedContract = buildDraftContract("软删除合同");
        deletedContract.setTenantId(TENANT_ID);
        deletedContract.setContractCode(codePrefix + String.format("%03d", deletedSuffix));
        deletedContract.setDeletedFlag(1);
        contractMapper.insert(deletedContract);

        Long id = contractService.create(buildDraftContract("软删除历史命中测试"));

        CtContract saved = contractMapper.selectById(id);
        assertNotNull(saved);
        assertEquals(codePrefix + String.format("%03d", deletedSuffix + 1), saved.getContractCode(),
                "includeDeleted=true 应该基于 soft delete 历史继续递增");
    }

    // ═══════════════════════════════════════════════════════════════
    // 更新
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("更新 — DRAFT 合同允许编辑，字段正确更新")
    void testUpdateDraftAllowed() {
        Long id = insertDraftContract("更新前合同名称");

        CtContract toUpdate = new CtContract();
        toUpdate.setId(id);
        toUpdate.setContractName("更新后合同名称");
        toUpdate.setContractType("SERVICE");
        toUpdate.setProjectId(PROJECT_ID);
        toUpdate.setPartyAId(PARTY_A_ID);
        toUpdate.setPartyBId(PARTY_B_ID);
        toUpdate.setContractAmount(new BigDecimal("999000.00"));
        toUpdate.setCurrentAmount(new BigDecimal("999000.00"));
        toUpdate.setTaxRate(new BigDecimal("6.00"));
        toUpdate.setTaxAmount(new BigDecimal("56509.43"));
        toUpdate.setAmountWithoutTax(new BigDecimal("942490.57"));
        toUpdate.setSignedDate(LocalDate.of(2026, 2, 15));
        toUpdate.setStartDate(LocalDate.of(2026, 3, 1));
        toUpdate.setEndDate(LocalDate.of(2026, 9, 30));
        toUpdate.setPaymentMethod("现金支付");
        toUpdate.setSettlementMethod("一次性结算");
        toUpdate.setContractStatus("DRAFT");
        toUpdate.setRemark("测试备注");

        contractService.update(toUpdate);

        CtContract updated = contractMapper.selectById(id);
        assertEquals("更新后合同名称", updated.getContractName());
        assertEquals("SERVICE", updated.getContractType());
        assertEquals(0, new BigDecimal("999000.00").compareTo(updated.getContractAmount()));
        assertEquals("测试备注", updated.getRemark());
        // 受保护字段不应被覆盖
        assertNotNull(updated.getContractCode(), "合同编号不应被覆盖");
        assertEquals(ContractStatusConstants.APPROVAL_DRAFT, updated.getApprovalStatus());
    }

    @Test
    @Transactional
    @DisplayName("更新 — 不存在时抛 CONTRACT_NOT_FOUND")
    void testUpdateNotFound() {
        CtContract toUpdate = new CtContract();
        toUpdate.setId(-999L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> contractService.update(toUpdate));
        assertEquals("CONTRACT_NOT_FOUND", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("更新 — APPROVED 合同拒绝编辑，抛 CONTRACT_NOT_EDITABLE")
    void testUpdateApprovedContractRejected() {
        CtContract contract = buildDraftContract("将变更为已审批的合同");
        contract.setApprovalStatus(ContractStatusConstants.APPROVAL_APPROVED);
        contract.setContractCode("CT-TEST-APPROVED-UPDATE");
        contract.setTenantId(TENANT_ID);
        contractMapper.insert(contract);

        CtContract toUpdate = new CtContract();
        toUpdate.setId(contract.getId());
        toUpdate.setContractName("尝试修改已审批合同");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> contractService.update(toUpdate));
        assertEquals("CONTRACT_NOT_EDITABLE", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════════
    // 删除
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("删除 — DRAFT 合同允许软删除")
    void testDeleteDraftAllowed() {
        Long id = insertDraftContract("待删除合同");
        assertNotNull(contractMapper.selectById(id));

        contractService.delete(id);

        assertNull(contractMapper.selectById(id), "软删除后应查不到记录");
    }

    @Test
    @Transactional
    @DisplayName("删除 — 不存在时抛 CONTRACT_NOT_FOUND")
    void testDeleteNotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> contractService.delete(-999L));
        assertEquals("CONTRACT_NOT_FOUND", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("删除 — APPROVED 合同拒绝删除，抛 CONTRACT_IN_APPROVAL")
    void testDeleteApprovedContractRejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> contractService.delete(SEED_CONTRACT_30001));
        assertEquals("CONTRACT_IN_APPROVAL", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════════
    // 提交审批
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("提交审批 — DRAFT 状态可提交，审批状态变为 APPROVING，生成 wf 实例")
    void testSubmitForApprovalSuccess() {
        Long id = insertDraftContract("提交审批测试合同");

        contractService.submitForApproval(id);

        CtContract after = contractMapper.selectById(id);
        assertEquals(ContractStatusConstants.APPROVAL_APPROVING, after.getApprovalStatus());

        // 验证 wf_instance 已生成
        WfInstance instance = wfInstanceMapper.selectOne(
                new LambdaQueryWrapper<WfInstance>()
                        .eq(WfInstance::getBusinessType,
                                ContractStatusConstants.BUSINESS_TYPE_CONTRACT_APPROVAL)
                        .eq(WfInstance::getBusinessId, id));
        assertNotNull(instance, "应生成审批实例");
        assertEquals("RUNNING", instance.getInstanceStatus());
        assertEquals(USER_ADMIN, instance.getInitiatorId());
    }

    @Test
    @Transactional
    @DisplayName("提交审批 — 不存在时抛 CONTRACT_NOT_FOUND")
    void testSubmitNotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> contractService.submitForApproval(-999L));
        assertEquals("CONTRACT_NOT_FOUND", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("提交审批 — 重复提交抛 CONTRACT_ALREADY_SUBMITTED")
    void testSubmitDuplicateRejected() {
        Long id = insertDraftContract("重复提交测试合同");

        contractService.submitForApproval(id);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> contractService.submitForApproval(id));
        assertEquals("CONTRACT_ALREADY_SUBMITTED", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════════
    // 复合原子保存
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("复合保存 — 新建合同：header + items + terms 全量持久化")
    void testCompositeSaveCreateAllThreePersist() {
        CtContract contract = buildDraftContract("复合保存创建测试");
        CtContractItem item = buildItem("CI-CMP-001", "测试清单项",
                new BigDecimal("100.00"), new BigDecimal("450.00"));
        CtContractPaymentTerm term = buildTerm("预付款", new BigDecimal("30.00"), 1);

        ContractSaveRequest request = new ContractSaveRequest();
        request.setContract(contract);
        request.setItems(List.of(item));
        request.setPaymentTerms(List.of(term));

        Long contractId = contractService.compositeSave(request);
        assertNotNull(contractId);

        // 验证 header
        CtContract saved = contractMapper.selectById(contractId);
        assertNotNull(saved);
        assertEquals("DRAFT", saved.getApprovalStatus());
        assertNotNull(saved.getContractCode());

        // 验证 items
        List<CtContractItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<CtContractItem>()
                        .eq(CtContractItem::getContractId, contractId));
        assertEquals(1, items.size());
        assertEquals("CI-CMP-001", items.get(0).getItemCode());

        // 验证 terms
        List<CtContractPaymentTerm> terms = paymentTermMapper.selectList(
                new LambdaQueryWrapper<CtContractPaymentTerm>()
                        .eq(CtContractPaymentTerm::getContractId, contractId));
        assertEquals(1, terms.size());
        assertEquals("预付款", terms.get(0).getTermName());
    }

    @Test
    @Transactional
    @DisplayName("复合保存 — 更新已有 DRAFT 合同：旧 items/terms 被全量替换")
    void testCompositeSaveUpdateReplaceAll() {
        Long id = insertDraftContract("复合保存更新测试");

        CtContractItem item = buildItem("CI-CMP-UPD-001", "新清单项",
                new BigDecimal("50.00"), new BigDecimal("200.00"));
        CtContractPaymentTerm term = buildTerm("进度款", new BigDecimal("50.00"), 1);

        CtContract updateContract = new CtContract();
        updateContract.setId(id);
        updateContract.setContractName("复合保存更新后名称");
        updateContract.setContractType("SUB");
        updateContract.setProjectId(PROJECT_ID);
        updateContract.setPartyAId(PARTY_A_ID);
        updateContract.setPartyBId(PARTY_B_ID);
        updateContract.setContractAmount(new BigDecimal("500000.00"));
        updateContract.setCurrentAmount(new BigDecimal("500000.00"));
        updateContract.setPaidAmount(BigDecimal.ZERO);
        updateContract.setTaxRate(new BigDecimal("13.00"));
        updateContract.setPaymentMethod("电汇");
        updateContract.setSettlementMethod("按节点结算");

        ContractSaveRequest request = new ContractSaveRequest();
        request.setContract(updateContract);
        request.setItems(List.of(item));
        request.setPaymentTerms(List.of(term));

        Long resultId = contractService.compositeSave(request);
        assertEquals(id, resultId);

        CtContract updated = contractMapper.selectById(id);
        assertEquals("复合保存更新后名称", updated.getContractName());
        assertEquals("电汇", updated.getPaymentMethod());

        List<CtContractItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<CtContractItem>()
                        .eq(CtContractItem::getContractId, id));
        assertEquals(1, items.size());
        assertEquals("CI-CMP-UPD-001", items.get(0).getItemCode());
    }

    @Test
    @Transactional
    @DisplayName("复合保存 — APPROVED 合同拒绝编辑，抛 CONTRACT_NOT_EDITABLE")
    void testCompositeSaveApprovedContractRejected() {
        CtContract contract = buildDraftContract("已审批不可编辑");
        contract.setApprovalStatus(ContractStatusConstants.APPROVAL_APPROVED);
        contract.setContractCode("CT-TEST-CMP-APPROVED");
        contract.setTenantId(TENANT_ID);
        contractMapper.insert(contract);

        CtContract editContract = new CtContract();
        editContract.setId(contract.getId());
        editContract.setContractName("尝试修改");

        ContractSaveRequest request = new ContractSaveRequest();
        request.setContract(editContract);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> contractService.compositeSave(request));
        assertEquals("CONTRACT_NOT_EDITABLE", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════════
    // 审批记录查询
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("审批记录查询 — 提交审批后存在 SUBMIT 记录")
    void testGetApprovalRecordsAfterSubmit() {
        Long id = insertDraftContract("审批记录查询测试");
        contractService.submitForApproval(id);

        List<ContractApprovalRecordVO> records = contractService.getApprovalRecords(id);
        assertNotNull(records);
        assertFalse(records.isEmpty(), "提交后应至少有一条审批记录");

        boolean hasSubmit = records.stream()
                .anyMatch(r -> "SUBMIT".equals(r.getActionType()));
        assertTrue(hasSubmit, "应包含 SUBMIT 类型的记录");

        // 验证记录字段完整性
        ContractApprovalRecordVO firstRecord = records.get(0);
        assertNotNull(firstRecord.getActionType());
        assertNotNull(firstRecord.getOperatorName());
        assertNotNull(firstRecord.getCreatedAt());
    }

    @Test
    @Transactional
    @DisplayName("审批记录查询 — 未提交审批时返回空列表")
    void testGetApprovalRecordsWhenNone() {
        List<ContractApprovalRecordVO> records = contractService.getApprovalRecords(SEED_CONTRACT_30001);
        // seed 合同 30001 没有审批实例记录
        assertNotNull(records);
        assertEquals(0, records.size());
    }

    // ═══════════════════════════════════════════════════════════════
    // 边界和守卫
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("边界 — getPage 分页参数 pageSize=1 返回单条记录")
    void testGetPageSizeOne() {
        IPage<CtContractVO> page = contractService.getPage(1, 1,
                null, null, null, null, null, null, PROJECT_ID, null, null);
        assertNotNull(page);
        assertTrue(page.getRecords().size() <= 1, "pageSize=1 最多返回 1 条");
    }

    @Test
    @Transactional
    @DisplayName("租户隔离 — 创建时 tenantId 自动赋值为当前租户")
    void testTenantIdAutoAssigned() {
        Long id = createDraftContract("租户隔离测试合同");
        CtContract saved = contractMapper.selectById(id);
        assertEquals(TENANT_ID, saved.getTenantId(), "tenantId 应为当前租户");
    }

    @Test
    @Transactional
    @DisplayName("租户隔离 — getPage 仅返回当前租户数据")
    void testGetPageTenantIsolation() {
        // 在租户 999 下查询，不应看到 tenantId=0 的 seed 数据
        UserContext.set(Jwts.claims()
                .add("userId", USER_ADMIN)
                .add("username", "admin")
                .add("tenantId", 999L)
                .add("roleCodes", List.of("ADMIN"))
                .build());

        IPage<CtContractVO> page = contractService.getPage(1, 20,
                null, null, null, null, null, null, null, null, null);
        for (CtContractVO vo : page.getRecords()) {
            assertNotEquals("CT-2026-001", vo.getContractCode(),
                    "跨租户不应看到 seed 合同数据");
        }
    }
}
