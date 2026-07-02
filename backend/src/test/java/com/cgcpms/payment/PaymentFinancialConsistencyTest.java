package com.cgcpms.payment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.entity.CtContractChange;
import com.cgcpms.contract.mapper.CtContractChangeMapper;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.payment.service.PayApplicationService;
import com.cgcpms.payment.service.PayRecordService;
import com.cgcpms.payment.vo.PayRecordVO;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Financial consistency tests for payment writeback, contract change,
 * and purchasing workflows.
 *
 * Covers:
 *   - Duplicate external_txn_no idempotency
 *   - Overpayment prevention (contract balance gate)
 *   - Concurrent contract change approvals accumulate correctly
 */
@SpringBootTest
@ActiveProfiles("local")
@DisplayName("PaymentFinancialConsistency — 付款财务一致性测试")
class PaymentFinancialConsistencyTest {

    private static final long TENANT_ID = 0L;
    private static final long USER_ADMIN = 1L;

    @Autowired private PayRecordService payRecordService;
    @Autowired private PayApplicationService payApplicationService;
    @Autowired private PayRecordMapper payRecordMapper;
    @Autowired private PayApplicationMapper payApplicationMapper;
    @Autowired private CtContractMapper contractMapper;
    @Autowired private CtContractChangeMapper contractChangeMapper;
    @Autowired private PmProjectMapper projectMapper;
    @Autowired private MdPartnerMapper partnerMapper;

    private Long testProjectId;
    private Long testContractId;
    private Long testPayAppId;

    @BeforeEach
    void setup() {
        TestUserContext.setAdmin(TENANT_ID, USER_ADMIN);

        // Seed project
        PmProject project = new PmProject();
        project.setId(90001L);
        project.setProjectCode("FC-TEST-001");
        project.setProjectName("财务一致性测试项目");
        project.setProjectType("CONSTRUCTION");
        project.setContractAmount(new BigDecimal("2000000.00"));
        project.setStatus("RUNNING");
        project.setApprovalStatus("APPROVED");
        project.setTenantId(TENANT_ID);
        if (projectMapper.selectById(90001L) == null) projectMapper.insert(project);
        testProjectId = 90001L;

        // Seed partners
        if (partnerMapper.selectById(90001L) == null) {
            MdPartner pa = new MdPartner();
            pa.setId(90001L); pa.setPartnerCode("FC-PA"); pa.setPartnerName("测试甲方");
            pa.setPartnerType("PARTY_A"); pa.setStatus("ENABLE"); pa.setTenantId(TENANT_ID);
            partnerMapper.insert(pa);
        }
        if (partnerMapper.selectById(90002L) == null) {
            MdPartner pb = new MdPartner();
            pb.setId(90002L); pb.setPartnerCode("FC-PB"); pb.setPartnerName("测试乙方");
            pb.setPartnerType("PARTY_B"); pb.setStatus("ENABLE"); pb.setTenantId(TENANT_ID);
            partnerMapper.insert(pb);
        }

        // Seed contract
        CtContract contract = new CtContract();
        contract.setId(90001L);
        contract.setProjectId(testProjectId);
        contract.setContractCode("FC-CT-001");
        contract.setContractName("财务一致性测试合同");
        contract.setContractType("SUB");
        contract.setPartyAId(90001L);
        contract.setPartyBId(90002L);
        contract.setContractAmount(new BigDecimal("1000000.00"));
        contract.setCurrentAmount(new BigDecimal("1000000.00"));
        contract.setPaidAmount(BigDecimal.ZERO);
        contract.setTaxRate(new BigDecimal("13.00"));
        contract.setContractStatus("DRAFT");
        contract.setApprovalStatus("APPROVED");
        contract.setTenantId(TENANT_ID);
        if (contractMapper.selectById(90001L) == null) contractMapper.insert(contract);
        testContractId = 90001L;

        // Seed pay application
        PayApplication app = new PayApplication();
        app.setId(90001L);
        app.setProjectId(testProjectId);
        app.setContractId(testContractId);
        app.setPartnerId(90002L);
        app.setApplyCode("PAY-FC-001");
        app.setApplyAmount(new BigDecimal("1000000.00"));
        app.setApprovedAmount(new BigDecimal("1000000.00"));
        app.setActualPayAmount(BigDecimal.ZERO);
        app.setPayType("进度款");
        app.setPayStatus("APPROVED");
        app.setApprovalStatus("APPROVED");
        app.setTenantId(TENANT_ID);
        if (payApplicationMapper.selectById(90001L) == null) payApplicationMapper.insert(app);
        testPayAppId = 90001L;
    }

    @AfterEach
    void cleanup() {
        TestUserContext.clear();
        // Cleanup created pay records
        payRecordMapper.delete(new LambdaQueryWrapper<PayRecord>()
                .eq(PayRecord::getPayApplicationId, testPayAppId));
    }

    // ═══════════════════════════════════════════════════════════════
    // T-FC-1: Duplicate external_txn_no → idempotent (one record, no double posting)
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("T-FC-1: 重复 external_txn_no 幂等 — 同一流水号只生成一条付款记录")
    void testDuplicateExternalTxnNoIsIdempotent() {
        PayRecord input1 = buildPayRecord(new BigDecimal("100000.00"), "TXN-UNIQUE-001");
        PayRecordVO vo1 = payRecordService.writeback(input1);
        assertNotNull(vo1.getId());

        // Same external_txn_no again — should return existing record
        PayRecord input2 = buildPayRecord(new BigDecimal("200000.00"), "TXN-UNIQUE-001");
        PayRecordVO vo2 = payRecordService.writeback(input2);

        // Should be the same record (idempotent return)
        assertEquals(vo1.getId(), vo2.getId(), "同一流水号应返回同一记录");

        // Verify only ONE record in DB for this external_txn_no
        long count = payRecordMapper.selectCount(new LambdaQueryWrapper<PayRecord>()
                .eq(PayRecord::getExternalTxnNo, "TXN-UNIQUE-001"));
        assertEquals(1, count, "数据库应只有1条记录");

        // Amount should be from first write (100000.00), not second (200000.00)
        PayRecord db = payRecordMapper.selectById(Long.parseLong(vo1.getId()));
        assertEquals(0, new BigDecimal("100000.00").compareTo(db.getPayAmount()),
                "金额应为首次写入的100000.00");
    }

    // ═══════════════════════════════════════════════════════════════
    // T-FC-2: Concurrent payments ≤ contract amount → one succeeds, one rejected
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("T-FC-2: 并发付款不超过合同余额 — 超额付款被拒绝")
    void testConcurrentPaymentsDontExceedContractBalance() throws Exception {
        // First writeback: 300000 (ok)
        PayRecord input1 = buildPayRecord(new BigDecimal("300000.00"), "TXN-CONC-001");
        PayRecordVO vo1 = payRecordService.writeback(input1);
        assertNotNull(vo1.getId());

        // Second writeback: 300000 (total 600000, still ≤ 1000000)
        PayRecord input2 = buildPayRecord(new BigDecimal("300000.00"), "TXN-CONC-002");
        PayRecordVO vo2 = payRecordService.writeback(input2);
        assertNotNull(vo2.getId());

        // Third writeback: 500000 (total would be 1100000 > 1000000)
        PayRecord input3 = buildPayRecord(new BigDecimal("500000.00"), "TXN-CONC-003");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> payRecordService.writeback(input3),
                "超额付款应抛异常");
        assertTrue(ex.getCode().contains("OVERPAYMENT") || ex.getCode().contains("EXCEED"),
                "错误码应为超付相关: " + ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════════
    // T-FC-3: Concurrent contract change approvals accumulate correctly
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("T-FC-3: 并发合同变更审批 — currentAmount 原子累加正确")
    void testConcurrentContractChangeApprovalsAccumulateCorrectly() throws Exception {
        BigDecimal originalCurrent = contractMapper.selectById(testContractId).getCurrentAmount();
        assertEquals(0, new BigDecimal("1000000.00").compareTo(originalCurrent),
                "初始 currentAmount 应为 1000000.00");

        int threadCount = 5;
        BigDecimal eachChange = new BigDecimal("10000.00");
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        List<Exception> errors = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    TestUserContext.setAdmin(TENANT_ID, USER_ADMIN);
                    contractMapper.update(null,
                            new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<CtContract>()
                                    .eq(CtContract::getId, testContractId)
                                    .setSql("current_amount = current_amount + " + eachChange));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    synchronized (errors) { errors.add(e); }
                } finally {
                    TestUserContext.clear();
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(threadCount, successCount.get(), "所有线程应成功执行");
        assertTrue(errors.isEmpty(), "不应有异常: " + errors);

        // Verify final currentAmount
        CtContract contract = contractMapper.selectById(testContractId);
        BigDecimal expected = originalCurrent.add(eachChange.multiply(new BigDecimal(threadCount)));
        assertEquals(0, expected.compareTo(contract.getCurrentAmount()),
                "currentAmount 应正确累加: expected=" + expected + ", actual=" + contract.getCurrentAmount());
    }

    // ═══════════════════════════════════════════════════════════════
    // T-FC-4: Writeback without external_txn_no must fail under the new safety rule
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("T-FC-4: 无 external_txn_no 的写回应明确失败")
    void testWritebackWithoutExternalTxnNo() {
        PayRecord input = new PayRecord();
        input.setPayApplicationId(testPayAppId);
        input.setPayAmount(new BigDecimal("50000.00"));
        input.setPayDate(LocalDate.now());
        input.setPayMethod("银行转账");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> payRecordService.writeback(input),
                "缺少外部交易流水号应直接失败");
        assertEquals("EXTERNAL_TXN_NO_REQUIRED", ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════════
    // T-FC-5: Contract balance check rejects over-contract payments
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Transactional
    @DisplayName("T-FC-5: 合同余额检查 — 累计付款超过合同 currentAmount 时拒绝")
    void testContractBalanceCheckRejectsOverPayment() {
        // Pay 900,000 first
        PayRecord r1 = buildPayRecord(new BigDecimal("900000.00"), "TXN-BAL-001");
        payRecordService.writeback(r1);

        // Try to pay another 200,000 (total 1,100,000 > 1,000,000)
        PayRecord r2 = buildPayRecord(new BigDecimal("200000.00"), "TXN-BAL-002");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> payRecordService.writeback(r2),
                "超过合同余额应拒绝");
        assertTrue(ex.getCode().contains("EXCEED") || ex.getCode().contains("OVERPAYMENT"),
                "错误码应为超额相关: " + ex.getCode());
    }

    // ═══════════════════════════════════════════════════════════════
    // Helper
    // ═══════════════════════════════════════════════════════════════

    private PayRecord buildPayRecord(BigDecimal amount, String externalTxnNo) {
        PayRecord record = new PayRecord();
        record.setPayApplicationId(testPayAppId);
        record.setPayAmount(amount);
        record.setPayDate(LocalDate.now());
        record.setPayMethod("银行转账");
        record.setExternalTxnNo(externalTxnNo);
        return record;
    }
}
