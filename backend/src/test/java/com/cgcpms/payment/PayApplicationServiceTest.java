package com.cgcpms.payment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.entity.PayApplicationBasis;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayApplicationBasisMapper;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.payment.service.PayApplicationService;
import com.cgcpms.payment.vo.PayApplicationVO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PayApplicationService 单元测试 -- 覆盖 core write path、query、approval、basis、payStatus。
 * <p>
 * 使用 H2 内存库（"local" profile）+ 种子数据（V90__h2_integration_test_seed_data.sql）：
 * contract 30001 / partner 20001,20002 / project 10001。
 */
@SpringBootTest(properties = {"spring.main.allow-circular-references=true", "spring.main.lazy-initialization=true"})
@ActiveProfiles("local")
@DisplayName("PayApplicationService -- 核心方法测试")
class PayApplicationServiceTest {

    private static final long TENANT_ID = 0L;
    private static final long USER_ADMIN = 1L;

    @Autowired
    private PayApplicationService payApplicationService;
    @Autowired
    private PayApplicationMapper payApplicationMapper;
    @Autowired
    private PayApplicationBasisMapper payApplicationBasisMapper;
    @Autowired
    private PayRecordMapper payRecordMapper;

    private Long testAppId;
    private PayApplication testApp;

    @BeforeEach
    void setup() {
        TestUserContext.setAdmin(TENANT_ID, USER_ADMIN);

        // 使用 create() 方法创建 DRAFT 状态的付款申请，自动生成 applyCode 和默认状态
        testApp = new PayApplication();
        testApp.setProjectId(10001L);
        testApp.setContractId(30001L);
        testApp.setPartnerId(20002L);
        testApp.setApplyAmount(new BigDecimal("500000.00"));
        testApp.setPayType("进度款");
        testApp.setApplyReason("集成测试");

        testAppId = payApplicationService.create(testApp);
        testApp = payApplicationMapper.selectById(testAppId);
    }

    @AfterEach
    void cleanup() {
        // 清理 basis
        if (testAppId != null) {
            payApplicationBasisMapper.delete(new LambdaQueryWrapper<PayApplicationBasis>()
                    .eq(PayApplicationBasis::getPayApplicationId, testAppId));
        }
        // 清理 pay_record
        payRecordMapper.delete(new LambdaQueryWrapper<PayRecord>()
                .eq(PayRecord::getPayApplicationId, testAppId));
        TestUserContext.clear();
    }

    // ═══════════════════════════════════════════════════════════════
    // create
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("create -- 自动生成 applyCode、默认状态 DRAFT/PENDING")
    void testCreate_AutoGeneratesApplyCodeAndDefaults() {
        PayApplication app = new PayApplication();
        app.setProjectId(10001L);
        app.setContractId(30001L);
        app.setPartnerId(20002L);
        app.setApplyAmount(new BigDecimal("100000.00"));
        app.setPayType("进度款");

        Long id = payApplicationService.create(app);
        assertNotNull(id, "create 应返回 ID");

        PayApplication saved = payApplicationMapper.selectById(id);
        assertNotNull(saved.getApplyCode(), "applyCode 应自动生成");
        assertTrue(saved.getApplyCode().startsWith("PAY-"), "applyCode 应以 PAY- 开头");
        assertEquals("PENDING", saved.getPayStatus(), "默认 payStatus 应为 PENDING");
        assertEquals("DRAFT", saved.getApprovalStatus(), "默认 approvalStatus 应为 DRAFT");
        assertEquals(TENANT_ID, saved.getTenantId(), "tenantId 应设置为当前租户");
    }

    @Test
    @Transactional
    @DisplayName("create -- 传入 applyCode 时保留原值")
    void testCreate_KeepsProvidedApplyCode() {
        PayApplication app = new PayApplication();
        app.setProjectId(10001L);
        app.setContractId(30001L);
        app.setPartnerId(20002L);
        app.setApplyCode("CUSTOM-APPLY-001");
        app.setApplyAmount(new BigDecimal("100000.00"));
        app.setPayType("进度款");

        Long id = payApplicationService.create(app);

        PayApplication saved = payApplicationMapper.selectById(id);
        assertEquals("CUSTOM-APPLY-001", saved.getApplyCode(), "显式传入的 applyCode 不应被覆盖");
        assertEquals("PENDING", saved.getPayStatus(), "默认 payStatus 应为 PENDING");
        assertEquals("DRAFT", saved.getApprovalStatus(), "默认 approvalStatus 应为 DRAFT");
    }

    @Test
    @Transactional
    @DisplayName("create -- applyCode 序列号递增")
    void testCreate_ApplyCodeSequenceNumber() {
        String lastSuffix = null;
        for (int i = 0; i < 3; i++) {
            PayApplication app = new PayApplication();
            app.setProjectId(10001L);
            app.setContractId(30001L);
            app.setApplyAmount(new BigDecimal("10000.00"));
            app.setPayType("进度款");
            Long id = payApplicationService.create(app);

            PayApplication saved = payApplicationMapper.selectById(id);
            String code = saved.getApplyCode();
            // 提取后缀数字
            String suffix = code.substring(code.lastIndexOf('-') + 1);
            int seqNum = Integer.parseInt(suffix);
            assertTrue(seqNum > 0, "序列号应 > 0");
            if (lastSuffix != null) {
                assertTrue(Integer.parseInt(suffix) > Integer.parseInt(lastSuffix),
                        "序列号应递增: " + lastSuffix + " -> " + suffix);
            }
            lastSuffix = suffix;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // update
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("update -- DRAFT 状态可更新")
    void testUpdate_DraftCanBeUpdated() {
        testApp.setApplyReason("更新后的理由");
        testApp.setApplyAmount(new BigDecimal("600000.00"));
        payApplicationService.update(testApp);

        PayApplication updated = payApplicationMapper.selectById(testAppId);
        assertEquals("更新后的理由", updated.getApplyReason());
        assertEquals(0, new BigDecimal("600000.00").compareTo(updated.getApplyAmount()));
    }

    @Test
    @Transactional
    @DisplayName("update -- 非 DRAFT 状态不可编辑")
    void testUpdate_NonDraftCannotBeUpdated() {
        testApp.setApprovalStatus("APPROVING");
        payApplicationMapper.updateById(testApp);

        testApp.setApplyReason("试图修改");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> payApplicationService.update(testApp),
                "非 DRAFT 状态应抛异常");
        assertEquals("PAY_APP_IN_APPROVAL", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("update -- 不存在的申请抛异常")
    void testUpdate_NotFound() {
        PayApplication ghost = new PayApplication();
        ghost.setId(9999999L);
        ghost.setContractId(30001L);
        ghost.setApplyAmount(BigDecimal.ONE);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> payApplicationService.update(ghost));
        assertEquals("PAY_APP_NOT_FOUND", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════════
    // delete
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("delete -- DRAFT 状态可删除，同时删除 basis")
    void testDelete_DraftCanBeDeleted() {
        // 先添加一条 basis
        PayApplicationBasis basis = new PayApplicationBasis();
        basis.setPayApplicationId(testAppId);
        basis.setBasisType("MAT_RECEIPT");
        basis.setBasisId(99999L);
        basis.setBasisAmount(new BigDecimal("500000.00"));
        basis.setTenantId(TENANT_ID);
        payApplicationBasisMapper.insert(basis);

        payApplicationService.delete(testAppId);

        // 付款申请已删除
        assertNull(payApplicationMapper.selectById(testAppId));
        // basis 也一起删除
        List<PayApplicationBasis> remaining = payApplicationBasisMapper.selectList(
                new LambdaQueryWrapper<PayApplicationBasis>()
                        .eq(PayApplicationBasis::getPayApplicationId, testAppId));
        assertTrue(remaining.isEmpty(), "basis 应同时删除");
    }

    @Test
    @Transactional
    @DisplayName("delete -- 非 DRAFT 状态不可删除")
    void testDelete_NonDraftCannotBeDeleted() {
        testApp.setApprovalStatus("APPROVING");
        payApplicationMapper.updateById(testApp);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> payApplicationService.delete(testAppId));
        assertEquals("PAY_APP_IN_APPROVAL", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("delete -- 不存在的申请抛异常")
    void testDelete_NotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> payApplicationService.delete(9999999L));
        assertEquals("PAY_APP_NOT_FOUND", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════════
    // getById
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("getById -- 返回含 project/contract/partner 名称的 VO")
    void testGetById_ReturnsVOWithNames() {
        PayApplicationVO vo = payApplicationService.getById(testAppId);
        assertNotNull(vo);
        assertEquals("麓谷科技产业园一期", vo.getProjectName());
        assertEquals("主体结构施工合同", vo.getContractName());
        assertEquals("中建三局", vo.getPartnerName());
        assertEquals("500000.00", vo.getApplyAmount());
        assertEquals("DRAFT", vo.getApprovalStatus());
    }

    @Test
    @Transactional
    @DisplayName("getById -- 含 basis 列表")
    void testGetById_WithBasisList() {
        PayApplicationBasis basis = new PayApplicationBasis();
        basis.setPayApplicationId(testAppId);
        basis.setBasisType("MAT_RECEIPT");
        basis.setBasisId(99999L);
        basis.setBasisAmount(new BigDecimal("500000.00"));
        basis.setTenantId(TENANT_ID);
        payApplicationBasisMapper.insert(basis);

        PayApplicationVO vo = payApplicationService.getById(testAppId);
        assertNotNull(vo.getBasis());
        assertEquals(1, vo.getBasis().size());
        assertEquals("MAT_RECEIPT", vo.getBasis().get(0).getBasisType());
    }

    @Test
    @Transactional
    @DisplayName("getById -- 不存在的申请抛异常")
    void testGetById_NotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> payApplicationService.getById(9999999L));
        assertEquals("PAY_APP_NOT_FOUND", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("M2: getById -- 同租户无项目权限不可查看付款申请")
    void testGetById_NoProjectAccess() {
        TestUserContext.setUser(TENANT_ID, 999L, "no-project", List.of());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> payApplicationService.getById(testAppId));
        assertEquals("PROJECT_ACCESS_DENIED", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════════
    // getPage
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("getPage -- 返回分页列表，含 VO 名称")
    void testGetPage_ReturnsPaginatedVOList() {
        IPage<PayApplicationVO> page = payApplicationService.getPage(1, 10,
                null, null, null, null, null, null);
        assertTrue(page.getTotal() > 0, "应有记录");
        assertFalse(page.getRecords().isEmpty());

        PayApplicationVO vo = page.getRecords().get(0);
        assertNotNull(vo.getApplyCode());
    }

    @Test
    @Transactional
    @DisplayName("getPage -- 按 projectId 过滤")
    void testGetPage_FilterByProjectId() {
        IPage<PayApplicationVO> page = payApplicationService.getPage(1, 10,
                10001L, null, null, null, null, null);
        assertTrue(page.getRecords().stream()
                .allMatch(r -> "10001".equals(r.getProjectId())));
    }

    @Test
    @Transactional
    @DisplayName("getPage -- 按 contractId 过滤")
    void testGetPage_FilterByContractId() {
        IPage<PayApplicationVO> page = payApplicationService.getPage(1, 10,
                null, 30001L, null, null, null, null);
        assertTrue(page.getRecords().stream()
                .allMatch(r -> "30001".equals(r.getContractId())));
    }

    // ═══════════════════════════════════════════════════════════════
    // saveBasis
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("saveBasis -- 正常保存 basis 批量数据")
    void testSaveBasis_NormalSave() {
        List<PayApplicationBasis> basisList = new ArrayList<>();
        PayApplicationBasis b1 = new PayApplicationBasis();
        b1.setBasisType("MAT_RECEIPT");
        b1.setBasisId(99991L);
        b1.setBasisAmount(new BigDecimal("300000.00"));
        basisList.add(b1);

        PayApplicationBasis b2 = new PayApplicationBasis();
        b2.setBasisType("MAT_RECEIPT");
        b2.setBasisId(99992L);
        b2.setBasisAmount(new BigDecimal("200000.00"));
        basisList.add(b2);

        payApplicationService.saveBasis(testAppId, basisList);

        List<PayApplicationBasis> saved = payApplicationBasisMapper.selectList(
                new LambdaQueryWrapper<PayApplicationBasis>()
                        .eq(PayApplicationBasis::getPayApplicationId, testAppId));
        assertEquals(2, saved.size());
    }

    @Test
    @Transactional
    @DisplayName("saveBasis -- 金额不匹配抛异常")
    void testSaveBasis_AmountMismatch() {
        List<PayApplicationBasis> basisList = new ArrayList<>();
        PayApplicationBasis b = new PayApplicationBasis();
        b.setBasisType("MAT_RECEIPT");
        b.setBasisId(99991L);
        b.setBasisAmount(new BigDecimal("100000.00")); // header 是 500000
        basisList.add(b);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> payApplicationService.saveBasis(testAppId, basisList));
        assertEquals("AMOUNT_MISMATCH", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("saveBasis -- 重复 basis 抛异常")
    void testSaveBasis_DuplicateBasis() {
        List<PayApplicationBasis> basisList = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            PayApplicationBasis b = new PayApplicationBasis();
            b.setBasisType("MAT_RECEIPT");
            b.setBasisId(99991L);
            b.setBasisAmount(new BigDecimal("250000.00"));
            basisList.add(b);
        }

        BusinessException ex = assertThrows(BusinessException.class,
                () -> payApplicationService.saveBasis(testAppId, basisList));
        assertEquals("DUPLICATE_BASIS", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("saveBasis -- 非 DRAFT 状态不可编辑")
    void testSaveBasis_NonDraftNotAllowed() {
        testApp.setApprovalStatus("APPROVING");
        payApplicationMapper.updateById(testApp);

        List<PayApplicationBasis> basisList = new ArrayList<>();
        PayApplicationBasis b = new PayApplicationBasis();
        b.setBasisType("MAT_RECEIPT");
        b.setBasisId(99991L);
        b.setBasisAmount(new BigDecimal("500000.00"));
        basisList.add(b);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> payApplicationService.saveBasis(testAppId, basisList));
        assertEquals("PAY_APP_IN_APPROVAL", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════════
    // updatePayStatus
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("updatePayStatus -- 无 SUCCESS 记录时状态为 APPROVED")
    void testUpdatePayStatus_NoSuccessRecords_StatusApproved() {
        payApplicationService.updatePayStatus(testAppId);

        PayApplication app = payApplicationMapper.selectById(testAppId);
        assertEquals("APPROVED", app.getPayStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(
                app.getActualPayAmount() != null ? app.getActualPayAmount() : BigDecimal.ZERO));
    }

    @Test
    @Transactional
    @DisplayName("updatePayStatus -- 部分付款时状态为 PARTIALLY_PAID")
    void testUpdatePayStatus_PartiallyPaid() {
        PayRecord record = new PayRecord();
        record.setPayApplicationId(testAppId);
        record.setPayAmount(new BigDecimal("300000.00"));
        record.setPayDate(LocalDate.now());
        record.setPayStatus("SUCCESS");
        record.setTenantId(TENANT_ID);
        payRecordMapper.insert(record);

        payApplicationService.updatePayStatus(testAppId);

        PayApplication app = payApplicationMapper.selectById(testAppId);
        assertEquals("PARTIALLY_PAID", app.getPayStatus());
        assertEquals(0, new BigDecimal("300000.00").compareTo(
                app.getActualPayAmount() != null ? app.getActualPayAmount() : BigDecimal.ZERO));
    }

    @Test
    @Transactional
    @DisplayName("updatePayStatus -- 全额付款时状态为 PAID")
    void testUpdatePayStatus_FullyPaid() {
        PayRecord record = new PayRecord();
        record.setPayApplicationId(testAppId);
        record.setPayAmount(new BigDecimal("500000.00"));
        record.setPayDate(LocalDate.now());
        record.setPayStatus("SUCCESS");
        record.setTenantId(TENANT_ID);
        payRecordMapper.insert(record);

        payApplicationService.updatePayStatus(testAppId);

        PayApplication app = payApplicationMapper.selectById(testAppId);
        assertEquals("PAID", app.getPayStatus());
    }

    // ═══════════════════════════════════════════════════════════════
    // validatePaymentAmount
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("validatePaymentAmount -- 金额 <= 0 抛异常")
    void testValidatePaymentAmount_InvalidAmount() {
        PayApplication app = new PayApplication();
        app.setId(9999L);
        app.setApplyAmount(BigDecimal.ZERO);
        app.setContractId(30001L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> payApplicationService.validatePaymentAmount(app));
        assertEquals("INVALID_AMOUNT", ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("validatePaymentAmount -- 无合同抛异常")
    void testValidatePaymentAmount_MissingContract() {
        PayApplication app = new PayApplication();
        app.setId(9999L);
        app.setApplyAmount(new BigDecimal("100000.00"));
        app.setContractId(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> payApplicationService.validatePaymentAmount(app));
        assertEquals("MISSING_CONTRACT", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════════
    // getBasisList
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("getBasisList -- 返回 basis 列表")
    void testGetBasisList() {
        PayApplicationBasis basis = new PayApplicationBasis();
        basis.setPayApplicationId(testAppId);
        basis.setBasisType("MAT_RECEIPT");
        basis.setBasisId(99999L);
        basis.setBasisAmount(new BigDecimal("500000.00"));
        basis.setTenantId(TENANT_ID);
        payApplicationBasisMapper.insert(basis);

        var list = payApplicationService.getBasisList(testAppId);
        assertEquals(1, list.size());
        assertEquals("MAT_RECEIPT", list.get(0).getBasisType());
    }

    @Test
    @Transactional
    @DisplayName("getBasisList -- 无 basis 时返回空列表")
    void testGetBasisList_Empty() {
        var list = payApplicationService.getBasisList(testAppId);
        assertTrue(list.isEmpty());
    }

    // ═══════════════════════════════════════════════════════════════
    // checkContractBalance
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("checkContractBalance -- 合同余额充足时不抛异常")
    void testCheckContractBalance_SufficientBalance() {
        // contract 30001 currentAmount = 50,000,000, 无 SUCCESS 记录 --> 余额充足
        assertDoesNotThrow(() ->
                payApplicationService.checkContractBalance(testApp, new BigDecimal("100000.00")));
    }

    @Test
    @Transactional
    @DisplayName("checkContractBalance -- 忽略其他租户 SUCCESS 付款记录")
    void testCheckContractBalance_IgnoresOtherTenantPayRecords() {
        PayRecord otherTenantRecord = new PayRecord();
        otherTenantRecord.setPayApplicationId(testAppId);
        otherTenantRecord.setContractId(testApp.getContractId());
        otherTenantRecord.setPayAmount(new BigDecimal("60000000.00"));
        otherTenantRecord.setPayDate(LocalDate.now());
        otherTenantRecord.setPayStatus("SUCCESS");
        otherTenantRecord.setTenantId(999L);
        payRecordMapper.insert(otherTenantRecord);

        assertDoesNotThrow(() ->
                payApplicationService.checkContractBalance(testApp, new BigDecimal("100000.00")));
    }

    @Test
    @Transactional
    @DisplayName("checkContractBalance -- 总额超合同余额时抛异常")
    void testCheckContractBalance_ExceedsBalance() {
        // contract 30001 currentAmount = 50,000,000
        BusinessException ex = assertThrows(BusinessException.class, () ->
                payApplicationService.checkContractBalance(testApp, new BigDecimal("51000000.00")));
        assertTrue(ex.getCode().contains("EXCEED"));
    }

    // ═══════════════════════════════════════════════════════════════
    // submitForApproval -- 核心审批提交
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("submitForApproval -- 非 DRAFT 不可提交")
    void testSubmitForApproval_NonDraftNotAllowed() {
        testApp.setApprovalStatus("APPROVING");
        payApplicationMapper.updateById(testApp);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> payApplicationService.submitForApproval(testAppId));
        assertEquals("INVALID_STATUS", ex.getCode());
    }
}
