package com.cgcpms.cashbook;

import com.cgcpms.cashbook.constant.CashbookConstants;
import com.cgcpms.cashbook.dto.CashJournalCreateRequest;
import com.cgcpms.cashbook.dto.CashJournalQuery;
import com.cgcpms.cashbook.dto.FundAccountCommand;
import com.cgcpms.cashbook.dto.CashJournalUpdateRequest;
import com.cgcpms.cashbook.entity.CashJournalEntry;
import com.cgcpms.cashbook.mapper.CashJournalEntryMapper;
import com.cgcpms.payment.entity.PayRecord;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = "spring.main.allow-circular-references=true")
@ActiveProfiles("local")
@Transactional
class CashJournalArchiveTest {

    private static final long TENANT_ID = 934030L;

    @Autowired CashJournalService journalService;
    @Autowired FundAccountService accountService;
    @Autowired SysFileMapper fileMapper;
    @Autowired CashJournalEntryMapper entryMapper;

    @BeforeEach
    void setUp() {
        TestUserContext.setAdmin(TENANT_ID, 1L);
    }

    @AfterEach
    void tearDown() {
        TestUserContext.clear();
    }

    @Test
    void attachmentIsRequiredAndArchivedOutflowReducesBalance() {
        long accountId = createAccount("ARCHIVE-001", "100.00");
        long entryId = createOutflow(accountId, "40.00");

        BusinessException missing = assertThrows(BusinessException.class, () -> journalService.archive(entryId));
        assertEquals("CASH_JOURNAL_ATTACHMENT_REQUIRED", missing.getCode());

        attach(entryId, 93403001L);
        var archived = journalService.archive(entryId);

        assertEquals(CashbookConstants.Status.ARCHIVED, archived.getStatus());
        assertEquals("60.00", journalService.summary(new CashJournalQuery()).getCashBalance());
    }

    @Test
    void disabledAccountAndNegativeBalanceBlockArchive() {
        long accountId = createAccount("ARCHIVE-002", "30.00");
        long disabledEntry = createOutflow(accountId, "10.00");
        attach(disabledEntry, 93403002L);
        accountService.setEnabled(accountId, false);
        assertEquals("FUND_ACCOUNT_DISABLED",
                assertThrows(BusinessException.class, () -> journalService.archive(disabledEntry)).getCode());

        accountService.setEnabled(accountId, true);
        long excessEntry = createOutflow(accountId, "31.00");
        attach(excessEntry, 93403003L);
        assertEquals("FUND_ACCOUNT_INSUFFICIENT_BALANCE",
                assertThrows(BusinessException.class, () -> journalService.archive(excessEntry)).getCode());
    }

    @Test
    void payRecordCannotBindOrArchiveBeforeAccountOpeningDate() {
        long accountId = createAccount("ARCHIVE-OPENING", "100.00");
        PayRecord record = new PayRecord();
        record.setId(93403020L);
        record.setTenantId(TENANT_ID);
        record.setPayAmount(new BigDecimal("10.00"));
        record.setPayDate(LocalDate.of(2026, 6, 30));
        long entryId = Long.parseLong(journalService.createPendingFromPayRecord(record).getId());
        CashJournalUpdateRequest bind = new CashJournalUpdateRequest();
        bind.setAccountId(accountId);

        assertEquals("CASH_JOURNAL_BEFORE_ACCOUNT_OPENING_DATE",
                assertThrows(BusinessException.class, () -> journalService.updateDraft(entryId, bind)).getCode());

        CashJournalEntry legacy = entryMapper.selectById(entryId);
        legacy.setAccountId(accountId);
        entryMapper.updateById(legacy);
        attach(entryId, 93403020L);
        assertEquals("CASH_JOURNAL_BEFORE_ACCOUNT_OPENING_DATE",
                assertThrows(BusinessException.class, () -> journalService.archive(entryId)).getCode());
    }

    private long createAccount(String code, String opening) {
        FundAccountCommand command = new FundAccountCommand();
        command.setAccountCode(code);
        command.setAccountName(code);
        command.setAccountType(CashbookConstants.AccountType.CASH);
        command.setOpeningDate(LocalDate.of(2026, 7, 1));
        command.setOpeningBalance(new BigDecimal(opening));
        return Long.parseLong(accountService.createFundAccount(command).getId());
    }

    private long createOutflow(long accountId, String amount) {
        CashJournalCreateRequest request = new CashJournalCreateRequest();
        request.setAccountId(accountId);
        request.setDirection(CashbookConstants.Direction.OUT);
        request.setAmount(new BigDecimal(amount));
        request.setBusinessDate(LocalDate.of(2026, 7, 10));
        request.setSummary("archive test");
        return Long.parseLong(journalService.createManual(request).getId());
    }

    private void attach(long entryId, long fileId) {
        SysFile file = new SysFile();
        file.setId(fileId);
        file.setTenantId(TENANT_ID);
        file.setBusinessType("CASH_JOURNAL");
        file.setBusinessId(entryId);
        file.setFileName(fileId + ".pdf");
        file.setOriginalName("proof.pdf");
        file.setFileSize(10L);
        file.setContentType("application/pdf");
        file.setStoragePath("CASH_JOURNAL/" + entryId + "/" + fileId + ".pdf");
        file.setBucketName("test");
        fileMapper.insert(file);
    }
}
