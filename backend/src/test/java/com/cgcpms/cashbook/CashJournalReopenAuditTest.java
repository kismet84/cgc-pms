package com.cgcpms.cashbook;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.cashbook.constant.CashbookConstants;
import com.cgcpms.cashbook.dto.CashJournalCreateRequest;
import com.cgcpms.cashbook.dto.CashJournalUpdateRequest;
import com.cgcpms.cashbook.dto.FundAccountCommand;
import com.cgcpms.cashbook.entity.CashJournalChangeLog;
import com.cgcpms.cashbook.mapper.CashJournalChangeLogMapper;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "spring.main.allow-circular-references=true")
@ActiveProfiles("local")
@Transactional
class CashJournalReopenAuditTest {

    private static final long TENANT_ID = 934032L;

    @Autowired CashJournalService journalService;
    @Autowired FundAccountService accountService;
    @Autowired SysFileMapper fileMapper;
    @Autowired CashJournalChangeLogMapper changeLogMapper;

    @BeforeEach void setUp() { TestUserContext.setUser(TENANT_ID, 1L, "root", List.of("SUPER_ADMIN")); }
    @AfterEach void tearDown() { TestUserContext.clear(); }

    @Test
    void reopenUpdateAndRearchiveAppendCompleteSnapshots() {
        long entryId = archivedIncome();

        var reopened = journalService.reopen(entryId, "correct counterparty");
        assertEquals(CashbookConstants.Status.DRAFT, reopened.getStatus());

        CashJournalUpdateRequest update = new CashJournalUpdateRequest();
        update.setCounterpartyName("Corrected Co");
        journalService.updateDraft(entryId, update);
        journalService.archive(entryId);

        List<CashJournalChangeLog> logs = changeLogMapper.selectList(
                new LambdaQueryWrapper<CashJournalChangeLog>()
                        .eq(CashJournalChangeLog::getTenantId, TENANT_ID)
                        .eq(CashJournalChangeLog::getJournalEntryId, entryId)
                        .orderByAsc(CashJournalChangeLog::getCreatedAt));
        assertEquals(List.of(
                CashbookConstants.ChangeAction.REOPEN,
                CashbookConstants.ChangeAction.UPDATE_AFTER_REOPEN,
                CashbookConstants.ChangeAction.REARCHIVE),
                logs.stream().map(CashJournalChangeLog::getAction).toList());
        assertTrue(logs.get(0).getBeforeSnapshot().contains("ARCHIVED"));
        assertTrue(logs.get(0).getAfterSnapshot().contains("DRAFT"));
        assertTrue(logs.get(1).getAfterSnapshot().contains("Corrected Co"));
    }

    @Test
    void ordinaryAdminCannotReopenArchivedEntry() {
        long entryId = archivedIncome();
        TestUserContext.setAdmin(TENANT_ID, 2L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> journalService.reopen(entryId, "not allowed"));
        assertEquals("CASH_JOURNAL_REOPEN_FORBIDDEN", error.getCode());
    }

    @Test
    void reopenedEntryCannotMoveBeforeAccountOpeningDate() {
        long entryId = archivedIncome();
        journalService.reopen(entryId, "correct date");
        CashJournalUpdateRequest update = new CashJournalUpdateRequest();
        update.setBusinessDate(LocalDate.of(2026, 6, 30));

        BusinessException error = assertThrows(BusinessException.class,
                () -> journalService.updateDraft(entryId, update));

        assertEquals("CASH_JOURNAL_BEFORE_ACCOUNT_OPENING_DATE", error.getCode());
    }

    private long archivedIncome() {
        FundAccountCommand command = new FundAccountCommand();
        command.setAccountCode("REOPEN-" + System.nanoTime());
        command.setAccountName("Reopen Cash");
        command.setAccountType(CashbookConstants.AccountType.CASH);
        command.setOpeningDate(LocalDate.of(2026, 7, 1));
        command.setOpeningBalance(new BigDecimal("100.00"));
        long accountId = Long.parseLong(accountService.createFundAccount(command).getId());

        CashJournalCreateRequest request = new CashJournalCreateRequest();
        request.setAccountId(accountId);
        request.setDirection(CashbookConstants.Direction.IN);
        request.setAmount(new BigDecimal("50.00"));
        request.setBusinessDate(LocalDate.of(2026, 7, 10));
        request.setSummary("reopen test");
        long entryId = Long.parseLong(journalService.createManual(request).getId());

        SysFile file = new SysFile();
        file.setTenantId(TENANT_ID);
        file.setBusinessType("CASH_JOURNAL");
        file.setBusinessId(entryId);
        file.setFileName(entryId + ".pdf");
        file.setOriginalName("proof.pdf");
        file.setFileSize(10L);
        file.setContentType("application/pdf");
        file.setStoragePath("CASH_JOURNAL/" + entryId + "/proof.pdf");
        file.setBucketName("test");
        fileMapper.insert(file);
        journalService.archive(entryId);
        return entryId;
    }
}
