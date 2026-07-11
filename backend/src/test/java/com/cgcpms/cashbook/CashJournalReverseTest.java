package com.cgcpms.cashbook;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.cashbook.constant.CashbookConstants;
import com.cgcpms.cashbook.dto.CashJournalCreateRequest;
import com.cgcpms.cashbook.dto.CashJournalQuery;
import com.cgcpms.cashbook.dto.FundAccountCommand;
import com.cgcpms.cashbook.entity.CashJournalChangeLog;
import com.cgcpms.cashbook.mapper.CashJournalChangeLogMapper;
import com.cgcpms.cashbook.service.CashJournalService;
import com.cgcpms.cashbook.service.FundAccountService;
import com.cgcpms.common.TestUserContext;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "spring.main.allow-circular-references=true")
@ActiveProfiles("local")
@Transactional
class CashJournalReverseTest {

    private static final long TENANT_ID = 934031L;

    @Autowired CashJournalService journalService;
    @Autowired FundAccountService accountService;
    @Autowired SysFileMapper fileMapper;
    @Autowired CashJournalChangeLogMapper changeLogMapper;

    @BeforeEach void setUp() { TestUserContext.setAdmin(TENANT_ID, 1L); }
    @AfterEach void tearDown() { TestUserContext.clear(); }

    @Test
    void reversalKeepsOriginalAndCreatesOppositeArchivedEntryWithZeroNetEffect() {
        long accountId = account();
        CashJournalCreateRequest request = new CashJournalCreateRequest();
        request.setAccountId(accountId);
        request.setDirection(CashbookConstants.Direction.OUT);
        request.setAmount(new BigDecimal("30.00"));
        request.setBusinessDate(LocalDate.of(2026, 7, 10));
        request.setSummary("reverse test");
        long originalId = Long.parseLong(journalService.createManual(request).getId());
        attach(originalId);
        journalService.archive(originalId);

        var reversal = journalService.reverse(originalId, "wrong amount");
        var original = journalService.getById(originalId);

        assertEquals(CashbookConstants.Status.REVERSED, original.getStatus());
        assertEquals(CashbookConstants.Status.ARCHIVED, reversal.getStatus());
        assertEquals(CashbookConstants.Direction.IN, reversal.getDirection());
        assertEquals("30.00", reversal.getAmount());
        assertEquals(String.valueOf(originalId), reversal.getReverseOfEntryId());
        assertEquals("100.00", journalService.summary(new CashJournalQuery()).getCashBalance());
        assertTrue(changeLogMapper.selectCount(new LambdaQueryWrapper<CashJournalChangeLog>()
                .eq(CashJournalChangeLog::getTenantId, TENANT_ID)
                .eq(CashJournalChangeLog::getJournalEntryId, originalId)
                .eq(CashJournalChangeLog::getAction, CashbookConstants.ChangeAction.REVERSE)) > 0);
    }

    @Test
    void reversingConsumedIncomeCannotMakeBalanceNegative() {
        long accountId = account();
        CashJournalCreateRequest income = new CashJournalCreateRequest();
        income.setAccountId(accountId);
        income.setDirection(CashbookConstants.Direction.IN);
        income.setAmount(new BigDecimal("10.00"));
        income.setBusinessDate(LocalDate.of(2026, 7, 10));
        income.setSummary("income");
        long incomeId = Long.parseLong(journalService.createManual(income).getId());
        attach(incomeId);
        journalService.archive(incomeId);

        CashJournalCreateRequest expense = new CashJournalCreateRequest();
        expense.setAccountId(accountId);
        expense.setDirection(CashbookConstants.Direction.OUT);
        expense.setAmount(new BigDecimal("105.00"));
        expense.setBusinessDate(LocalDate.of(2026, 7, 10));
        expense.setSummary("expense");
        long expenseId = Long.parseLong(journalService.createManual(expense).getId());
        attach(expenseId);
        journalService.archive(expenseId);

        var error = assertThrows(com.cgcpms.common.exception.BusinessException.class,
                () -> journalService.reverse(incomeId, "cannot remove consumed income"));
        assertEquals("FUND_ACCOUNT_INSUFFICIENT_BALANCE", error.getCode());
    }

    private long account() {
        FundAccountCommand command = new FundAccountCommand();
        command.setAccountCode("REVERSE-001");
        command.setAccountName("Reverse Cash");
        command.setAccountType(CashbookConstants.AccountType.CASH);
        command.setOpeningDate(LocalDate.of(2026, 7, 1));
        command.setOpeningBalance(new BigDecimal("100.00"));
        return Long.parseLong(accountService.createFundAccount(command).getId());
    }

    private void attach(long entryId) {
        SysFile file = new SysFile();
        file.setTenantId(TENANT_ID);
        file.setBusinessType("CASH_JOURNAL");
        file.setBusinessId(entryId);
        file.setFileName("reverse.pdf");
        file.setOriginalName("reverse.pdf");
        file.setFileSize(10L);
        file.setContentType("application/pdf");
        file.setStoragePath("CASH_JOURNAL/" + entryId + "/reverse.pdf");
        file.setBucketName("test");
        fileMapper.insert(file);
    }
}
