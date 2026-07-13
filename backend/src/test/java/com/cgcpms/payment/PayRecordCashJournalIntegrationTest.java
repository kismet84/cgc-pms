package com.cgcpms.payment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.cashbook.constant.CashbookConstants;
import com.cgcpms.cashbook.entity.CashJournalEntry;
import com.cgcpms.cashbook.mapper.CashJournalEntryMapper;
import com.cgcpms.cashbook.service.CashJournalService;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.contract.entity.CtContract;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.partner.entity.MdPartner;
import com.cgcpms.partner.mapper.MdPartnerMapper;
import com.cgcpms.payment.entity.PayApplication;
import com.cgcpms.payment.entity.PayRecord;
import com.cgcpms.payment.mapper.PayApplicationMapper;
import com.cgcpms.payment.mapper.PayRecordMapper;
import com.cgcpms.payment.service.PayRecordService;
import com.cgcpms.project.entity.PmProject;
import com.cgcpms.project.mapper.PmProjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true", "spring.main.lazy-initialization=true"})
@ActiveProfiles("local")
@Transactional
class PayRecordCashJournalIntegrationTest {

    private static final long TENANT_ID = 934020L;
    private static final long PROJECT_ID = 93402001L;
    private static final long PARTY_A_ID = 93402002L;
    private static final long PARTY_B_ID = 93402003L;
    private static final long CONTRACT_ID = 93402004L;
    private static final long PAY_APP_ID = 93402005L;
    private static final long SECOND_PAY_APP_ID = 93402006L;

    @Autowired private PayRecordService payRecordService;
    @Autowired private PayRecordMapper payRecordMapper;
    @Autowired private CashJournalEntryMapper entryMapper;
    @Autowired private PayApplicationMapper payApplicationMapper;
    @Autowired private PmProjectMapper projectMapper;
    @Autowired private CtContractMapper contractMapper;
    @Autowired private MdPartnerMapper partnerMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private CashJournalService cashJournalService;

    @BeforeEach
    void setUp() {
        TestUserContext.setAdmin(TENANT_ID, 1L);
        seedProject();
        seedPartner(PARTY_A_ID, "CJ-PA", "PARTY_A");
        seedPartner(PARTY_B_ID, "CJ-PB", "PARTY_B");
        seedContract();
        seedPayApplication(PAY_APP_ID, "CJ-PAY-APP");
        seedPayApplication(SECOND_PAY_APP_ID, "CJ-PAY-APP-2");
    }

    @AfterEach
    void tearDown() {
        Mockito.reset(cashJournalService);
        jdbcTemplate.update("DELETE FROM cash_journal_entry WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM pay_record WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM cost_summary WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM pay_application WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM ct_contract WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM md_partner WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM pm_project WHERE tenant_id = ?", TENANT_ID);
        TestUserContext.clear();
    }

    @Test
    void paymentWritebackCreatesOnePendingJournalAndDuplicateCallbackDoesNotRepeatIt() {
        PayRecord first = input("CJ-TXN-001", "123.45");
        var firstResult = payRecordService.writeback(first);
        var duplicateResult = payRecordService.writeback(input("CJ-TXN-001", "123.45"));

        assertEquals(firstResult.getId(), duplicateResult.getId());
        CashJournalEntry journal = findByPayRecord(Long.valueOf(firstResult.getId()));
        assertNotNull(journal);
        assertEquals(CashbookConstants.Direction.OUT, journal.getDirection());
        assertEquals(CashbookConstants.SourceType.PAY_RECORD, journal.getSourceType());
        assertEquals(CashbookConstants.Status.PENDING_ARCHIVE, journal.getStatus());
        assertEquals(0, new BigDecimal("123.45").compareTo(journal.getAmount()));
        assertEquals(1, entryMapper.selectCount(new LambdaQueryWrapper<CashJournalEntry>()
                .eq(CashJournalEntry::getTenantId, TENANT_ID)
                .eq(CashJournalEntry::getSourceType, CashbookConstants.SourceType.PAY_RECORD)
                .eq(CashJournalEntry::getSourceId, Long.valueOf(firstResult.getId()))));

        var conflict = assertThrows(com.cgcpms.common.exception.BusinessException.class,
                () -> payRecordService.writeback(input("CJ-TXN-001", "999.00")));
        assertEquals("PAY_WRITEBACK_IDEMPOTENCY_CONFLICT", conflict.getCode());

        PayRecord changedDate = input("CJ-TXN-001", "123.45");
        changedDate.setPayDate(LocalDate.of(2026, 7, 11));
        var dateConflict = assertThrows(com.cgcpms.common.exception.BusinessException.class,
                () -> payRecordService.writeback(changedDate));
        assertEquals("PAY_WRITEBACK_IDEMPOTENCY_CONFLICT", dateConflict.getCode());
        assertEquals(1, journalCount(Long.valueOf(firstResult.getId())));
    }

    @Test
    void duplicateExternalTransactionCannotBeReusedByAnotherPayApplication() {
        var firstResult = payRecordService.writeback(input("CJ-TXN-CROSS-APP", "123.45"));
        PayRecord otherApplication = input("CJ-TXN-CROSS-APP", "123.45");
        otherApplication.setPayApplicationId(SECOND_PAY_APP_ID);

        var conflict = assertThrows(com.cgcpms.common.exception.BusinessException.class,
                () -> payRecordService.writeback(otherApplication));

        assertEquals("PAY_WRITEBACK_IDEMPOTENCY_CONFLICT", conflict.getCode());
        assertEquals(1, payRecordMapper.selectCount(new LambdaQueryWrapper<PayRecord>()
                .eq(PayRecord::getTenantId, TENANT_ID)
                .eq(PayRecord::getExternalTxnNo, "CJ-TXN-CROSS-APP")));
        assertEquals(1, journalCount(Long.valueOf(firstResult.getId())));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void journalFailureRollsBackPayRecord() {
        doThrow(new IllegalStateException("forced journal failure"))
                .when(cashJournalService)
                .createPendingFromPayRecord(argThat(record -> "CJ-TXN-ROLLBACK".equals(record.getExternalTxnNo())));

        assertThrows(IllegalStateException.class,
                () -> payRecordService.writeback(input("CJ-TXN-ROLLBACK", "10.00")));

        assertEquals(0, payRecordMapper.selectCount(new LambdaQueryWrapper<PayRecord>()
                .eq(PayRecord::getTenantId, TENANT_ID)
                .eq(PayRecord::getExternalTxnNo, "CJ-TXN-ROLLBACK")));
    }

    private CashJournalEntry findByPayRecord(Long payRecordId) {
        return entryMapper.selectOne(new LambdaQueryWrapper<CashJournalEntry>()
                .eq(CashJournalEntry::getTenantId, TENANT_ID)
                .eq(CashJournalEntry::getSourceType, CashbookConstants.SourceType.PAY_RECORD)
                .eq(CashJournalEntry::getSourceId, payRecordId));
    }

    private long journalCount(Long payRecordId) {
        return entryMapper.selectCount(new LambdaQueryWrapper<CashJournalEntry>()
                .eq(CashJournalEntry::getTenantId, TENANT_ID)
                .eq(CashJournalEntry::getSourceType, CashbookConstants.SourceType.PAY_RECORD)
                .eq(CashJournalEntry::getSourceId, payRecordId));
    }

    private PayRecord input(String externalTxnNo, String amount) {
        PayRecord input = new PayRecord();
        input.setPayApplicationId(PAY_APP_ID);
        input.setPayAmount(new BigDecimal(amount));
        input.setPayDate(LocalDate.of(2026, 7, 10));
        input.setPayMethod("BANK_TRANSFER");
        input.setExternalTxnNo(externalTxnNo);
        return input;
    }

    private void seedProject() {
        PmProject project = new PmProject();
        project.setId(PROJECT_ID);
        project.setTenantId(TENANT_ID);
        project.setProjectCode("CJ-PROJECT");
        project.setProjectName("Cash Journal Payment Project");
        project.setProjectType("CONSTRUCTION");
        project.setContractAmount(new BigDecimal("1000000.00"));
        project.setTargetCost(new BigDecimal("800000.00"));
        project.setStatus("ACTIVE");
        project.setApprovalStatus("APPROVED");
        projectMapper.insert(project);
    }

    private void seedPartner(long id, String code, String type) {
        MdPartner partner = new MdPartner();
        partner.setId(id);
        partner.setTenantId(TENANT_ID);
        partner.setPartnerCode(code);
        partner.setPartnerName(code);
        partner.setPartnerType(type);
        partner.setStatus("ENABLE");
        partnerMapper.insert(partner);
    }

    private void seedContract() {
        CtContract contract = new CtContract();
        contract.setId(CONTRACT_ID);
        contract.setTenantId(TENANT_ID);
        contract.setProjectId(PROJECT_ID);
        contract.setContractCode("CJ-CONTRACT");
        contract.setContractName("Cash Journal Contract");
        contract.setContractType("SUB");
        contract.setPartyAId(PARTY_A_ID);
        contract.setPartyBId(PARTY_B_ID);
        contract.setContractAmount(new BigDecimal("1000000.00"));
        contract.setCurrentAmount(new BigDecimal("1000000.00"));
        contract.setPaidAmount(BigDecimal.ZERO);
        contract.setContractStatus("PERFORMING");
        contract.setApprovalStatus("APPROVED");
        contract.setVersion(0);
        contractMapper.insert(contract);
    }

    private void seedPayApplication(long id, String applyCode) {
        PayApplication app = new PayApplication();
        app.setId(id);
        app.setTenantId(TENANT_ID);
        app.setProjectId(PROJECT_ID);
        app.setContractId(CONTRACT_ID);
        app.setPartnerId(PARTY_B_ID);
        app.setApplyCode(applyCode);
        app.setApplyAmount(new BigDecimal("1000000.00"));
        app.setApprovedAmount(new BigDecimal("1000000.00"));
        app.setActualPayAmount(BigDecimal.ZERO);
        app.setPayType("PROGRESS");
        app.setPayStatus("APPROVED");
        app.setApprovalStatus("APPROVED");
        payApplicationMapper.insert(app);
    }
}
