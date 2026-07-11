package com.cgcpms.cashbook;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.cashbook.constant.CashbookConstants;
import com.cgcpms.cashbook.dto.CashJournalCreateRequest;
import com.cgcpms.cashbook.dto.CashJournalQuery;
import com.cgcpms.cashbook.dto.CashJournalUpdateRequest;
import com.cgcpms.cashbook.dto.FundAccountCommand;
import com.cgcpms.cashbook.entity.CashJournalEntry;
import com.cgcpms.cashbook.mapper.CashJournalEntryMapper;
import com.cgcpms.cashbook.service.CashJournalService;
import com.cgcpms.cashbook.service.FundAccountService;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.file.entity.SysFile;
import com.cgcpms.file.mapper.SysFileMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "spring.main.allow-circular-references=true")
@ActiveProfiles("local")
@Transactional
class CashJournalServiceTest {

    private static final long TENANT_ID = 934011L;

    @Autowired
    private CashJournalService cashJournalService;

    @Autowired
    private FundAccountService fundAccountService;

    @Autowired
    private CashJournalEntryMapper entryMapper;

    @Autowired
    private SysFileMapper fileMapper;

    @BeforeEach
    void setUp() {
        TestUserContext.setAdmin(TENANT_ID, 1L);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void manualEntryStartsAsDraftWithTwentyFourHourDeadline() {
        long accountId = createCashAccount("CASH-SVC-001", "100.00");
        LocalDateTime before = LocalDateTime.now().plusHours(23).plusMinutes(59);

        var created = cashJournalService.createManual(request(accountId, "25.50"));

        assertEquals(CashbookConstants.Status.DRAFT, created.getStatus());
        assertEquals("25.50", created.getAmount());
        assertTrue(created.getClosureDueAt().isAfter(before));
        assertTrue(created.getEntryNo().matches("CJ-\\d{8}-\\d{3}"));
    }

    @Test
    void invalidAmountAndDisabledAccountAreRejected() {
        long accountId = createCashAccount("CASH-SVC-002", "100.00");
        CashJournalCreateRequest invalid = request(accountId, "0.00");
        assertEquals("CASH_JOURNAL_AMOUNT_INVALID",
                assertThrows(BusinessException.class, () -> cashJournalService.createManual(invalid)).getCode());

        fundAccountService.setEnabled(accountId, false);
        assertEquals("FUND_ACCOUNT_DISABLED",
                assertThrows(BusinessException.class,
                        () -> cashJournalService.createManual(request(accountId, "1.00"))).getCode());
    }

    @Test
    void manualEntryCannotPrecedeAccountOpeningDate() {
        long accountId = createCashAccount("CASH-SVC-OPENING", "100.00");
        CashJournalCreateRequest request = request(accountId, "1.00");
        request.setBusinessDate(LocalDate.of(2026, 6, 30));

        assertEquals("CASH_JOURNAL_BEFORE_ACCOUNT_OPENING_DATE",
                assertThrows(BusinessException.class, () -> cashJournalService.createManual(request)).getCode());
    }

    @Test
    void summaryCountsOnlyEffectiveEntriesInBalance() {
        long accountId = createCashAccount("CASH-SVC-003", "100.00");
        insertEntry(accountId, "201", CashbookConstants.Direction.IN, "50.00", CashbookConstants.Status.ARCHIVED);
        insertEntry(accountId, "202", CashbookConstants.Direction.OUT, "20.00", CashbookConstants.Status.ARCHIVED);
        insertEntry(accountId, "203", CashbookConstants.Direction.OUT, "99.00", CashbookConstants.Status.DRAFT);

        var summary = cashJournalService.summary(new CashJournalQuery());

        assertEquals("130.00", summary.getCashBalance());
        assertEquals("50.00", summary.getIncome());
        assertEquals("20.00", summary.getExpense());
        assertEquals(1L, summary.getPendingCount());
    }

    @Test
    void dateFilterDoesNotRemoveEarlierEntriesFromCurrentBalance() {
        long accountId = createCashAccount("CASH-SVC-FILTER", "100.00");
        CashJournalEntry earlier = new CashJournalEntry();
        earlier.setTenantId(TENANT_ID);
        earlier.setEntryNo("CJ-20260630-901");
        earlier.setAccountId(accountId);
        earlier.setDirection(CashbookConstants.Direction.IN);
        earlier.setAmount(new BigDecimal("50.00"));
        earlier.setBusinessDate(LocalDate.of(2026, 6, 30));
        earlier.setSummary("earlier");
        earlier.setSourceType(CashbookConstants.SourceType.MANUAL);
        earlier.setStatus(CashbookConstants.Status.ARCHIVED);
        earlier.setClosureDueAt(LocalDateTime.now());
        earlier.setVersion(0);
        entryMapper.insert(earlier);

        CashJournalQuery query = new CashJournalQuery();
        query.setBusinessDateStart(LocalDate.of(2026, 7, 1));
        assertEquals("100.00", cashJournalService.summary(query).getCashBalance());
    }

    @Test
    void accountAndPeriodFiltersKeepSelectedAccountsRealtimeBalance() {
        long selectedId = createCashAccount("CASH-SVC-SELECTED", "100.00");
        long otherId = createCashAccount("CASH-SVC-OTHER", "200.00");
        insertEntry(selectedId, "911", CashbookConstants.Direction.IN, "50.00", CashbookConstants.Status.ARCHIVED,
                LocalDate.of(2026, 6, 30));
        insertEntry(otherId, "912", CashbookConstants.Direction.IN, "25.00", CashbookConstants.Status.ARCHIVED,
                LocalDate.of(2026, 7, 10));

        CashJournalQuery query = new CashJournalQuery();
        query.setAccountId(selectedId);
        query.setDirection(CashbookConstants.Direction.OUT);
        query.setBusinessDateStart(LocalDate.of(2026, 7, 1));

        var summary = cashJournalService.summary(query);

        assertEquals("100.00", summary.getCashBalance());
        assertEquals("0.00", summary.getIncome());
        assertEquals("0.00", summary.getExpense());
    }

    @Test
    void serviceRejectsFractionalAndOversizedAmountsBeforePersistence() {
        CashJournalCreateRequest fractional = request(null, "1.001");
        assertEquals("CASH_JOURNAL_AMOUNT_INVALID",
                assertThrows(BusinessException.class, () -> cashJournalService.createManual(fractional)).getCode());

        CashJournalCreateRequest oversized = request(null, "10000000000000000.00");
        assertEquals("CASH_JOURNAL_AMOUNT_INVALID",
                assertThrows(BusinessException.class, () -> cashJournalService.createManual(oversized)).getCode());
    }

    @Test
    void updateRejectsInvalidDirectionAndOversizedTextForInternalCallers() {
        long entryId = Long.parseLong(cashJournalService.createManual(request(null, "1.00")).getId());

        CashJournalUpdateRequest invalidDirection = new CashJournalUpdateRequest();
        invalidDirection.setDirection("SIDEWAYS");
        assertEquals("CASH_JOURNAL_DIRECTION_INVALID",
                assertThrows(BusinessException.class,
                        () -> cashJournalService.updateDraft(entryId, invalidDirection)).getCode());

        CashJournalUpdateRequest oversizedCounterparty = new CashJournalUpdateRequest();
        oversizedCounterparty.setCounterpartyName("x".repeat(201));
        assertEquals("CASH_JOURNAL_COUNTERPARTY_TOO_LONG",
                assertThrows(BusinessException.class,
                        () -> cashJournalService.updateDraft(entryId, oversizedCounterparty)).getCode());

        CashJournalUpdateRequest oversizedSummary = new CashJournalUpdateRequest();
        oversizedSummary.setSummary("x".repeat(501));
        assertEquals("CASH_JOURNAL_SUMMARY_TOO_LONG",
                assertThrows(BusinessException.class,
                        () -> cashJournalService.updateDraft(entryId, oversizedSummary)).getCode());
    }

    @Test
    void csvPrefixesFormulaCellsAndKeepsQuoteEscaping() {
        for (String summary : new String[]{"=1+1", "-1", "@cmd", "safe \"quote\""}) {
            CashJournalCreateRequest request = request(null, "1.00");
            request.setSummary(summary);
            cashJournalService.createManual(request);
        }
        CashJournalCreateRequest leadingFormula = request(null, "1.00");
        leadingFormula.setSummary("placeholder");
        long leadingFormulaId = Long.parseLong(cashJournalService.createManual(leadingFormula).getId());
        CashJournalEntry stored = entryMapper.selectById(leadingFormulaId);
        stored.setSummary("  +SUM(A1:A2)");
        entryMapper.updateById(stored);

        String csv = new String(cashJournalService.exportCsv(new CashJournalQuery()), java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(csv.contains("\"'=1+1\""));
        assertTrue(csv.contains("\"'  +SUM(A1:A2)\""));
        assertTrue(csv.contains("\"'-1\""));
        assertTrue(csv.contains("\"'@cmd\""));
        assertTrue(csv.contains("\"safe \"\"quote\"\"\""));
    }

    @Test
    void anotherTenantCannotReadEntry() {
        var created = cashJournalService.createManual(request(null, "1.00"));
        TestUserContext.setAdmin(TENANT_ID + 1, 2L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> cashJournalService.getById(Long.valueOf(created.getId())));
        assertEquals("CASH_JOURNAL_NOT_FOUND", error.getCode());
    }

    @Test
    void detailBackfillsSelectedAccountNameAndType() {
        long accountId = createCashAccount("CASH-SVC-DETAIL", "100.00");
        long entryId = Long.parseLong(cashJournalService.createManual(request(accountId, "1.00")).getId());

        var detail = cashJournalService.getById(entryId);

        assertEquals("CASH-SVC-DETAIL", detail.getAccountName());
        assertEquals(CashbookConstants.AccountType.CASH, detail.getAccountType());
    }

    @Test
    void pageFiltersBySourceIdAndAttachmentState() {
        long accountId = createCashAccount("CASH-SVC-004", "100.00");
        long attachedId = insertEntry(accountId, "204", CashbookConstants.Direction.OUT,
                "10.00", CashbookConstants.Status.PENDING_ARCHIVE);
        CashJournalEntry attached = entryMapper.selectById(attachedId);
        attached.setSourceType(CashbookConstants.SourceType.PAY_RECORD);
        attached.setSourceId(401L);
        entryMapper.updateById(attached);

        long noAttachmentId = insertEntry(accountId, "205", CashbookConstants.Direction.OUT,
                "11.00", CashbookConstants.Status.PENDING_ARCHIVE);
        CashJournalEntry noAttachment = entryMapper.selectById(noAttachmentId);
        noAttachment.setSourceType(CashbookConstants.SourceType.PAY_RECORD);
        noAttachment.setSourceId(402L);
        entryMapper.updateById(noAttachment);

        SysFile file = new SysFile();
        file.setTenantId(TENANT_ID);
        file.setBusinessType("CASH_JOURNAL");
        file.setBusinessId(attachedId);
        file.setFileName("voucher.pdf");
        file.setOriginalName("voucher.pdf");
        file.setFileSize(10L);
        file.setContentType("application/pdf");
        file.setStoragePath("CASH_JOURNAL/" + attachedId + "/voucher.pdf");
        file.setBucketName("test");
        fileMapper.insert(file);

        CashJournalQuery sourceQuery = new CashJournalQuery();
        sourceQuery.setSourceType(CashbookConstants.SourceType.PAY_RECORD);
        sourceQuery.setSourceId(401L);
        assertEquals(attachedId, Long.parseLong(cashJournalService.page(sourceQuery).getRecords().getFirst().getId()));

        CashJournalQuery attachedQuery = new CashJournalQuery();
        attachedQuery.setHasAttachment(true);
        assertEquals(1L, cashJournalService.page(attachedQuery).getTotal());

        CashJournalQuery missingQuery = new CashJournalQuery();
        missingQuery.setHasAttachment(false);
        assertTrue(cashJournalService.page(missingQuery).getRecords().stream()
                .anyMatch(item -> item.getId().equals(Long.toString(noAttachmentId))));
    }

    private long createCashAccount(String code, String openingBalance) {
        FundAccountCommand command = new FundAccountCommand();
        command.setAccountCode(code);
        command.setAccountName(code);
        command.setAccountType(CashbookConstants.AccountType.CASH);
        command.setOpeningDate(LocalDate.of(2026, 7, 1));
        command.setOpeningBalance(new BigDecimal(openingBalance));
        return Long.parseLong(fundAccountService.createFundAccount(command).getId());
    }

    private CashJournalCreateRequest request(Long accountId, String amount) {
        CashJournalCreateRequest request = new CashJournalCreateRequest();
        request.setAccountId(accountId);
        request.setDirection(CashbookConstants.Direction.IN);
        request.setAmount(new BigDecimal(amount));
        request.setBusinessDate(LocalDate.of(2026, 7, 10));
        request.setSummary("manual service test");
        return request;
    }

    private long insertEntry(long accountId, String suffix, String direction, String amount, String status) {
        return insertEntry(accountId, suffix, direction, amount, status, LocalDate.of(2026, 7, 10));
    }

    private long insertEntry(long accountId, String suffix, String direction, String amount, String status,
                             LocalDate businessDate) {
        CashJournalEntry entry = new CashJournalEntry();
        entry.setTenantId(TENANT_ID);
        entry.setEntryNo("CJ-20260710-" + suffix);
        entry.setAccountId(accountId);
        entry.setDirection(direction);
        entry.setAmount(new BigDecimal(amount));
        entry.setBusinessDate(businessDate);
        entry.setSummary("summary test");
        entry.setSourceType(CashbookConstants.SourceType.MANUAL);
        entry.setStatus(status);
        entry.setClosureDueAt(LocalDateTime.now().plusHours(24));
        if (CashbookConstants.Status.ARCHIVED.equals(status)) {
            entry.setArchivedBy(1L);
            entry.setArchivedAt(LocalDateTime.now());
        }
        entry.setVersion(0);
        entryMapper.insert(entry);
        return entry.getId();
    }
}
