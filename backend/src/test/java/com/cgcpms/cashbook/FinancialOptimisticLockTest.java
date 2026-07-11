package com.cgcpms.cashbook;

import com.cgcpms.cashbook.constant.CashbookConstants;
import com.cgcpms.cashbook.dto.CashJournalUpdateRequest;
import com.cgcpms.cashbook.dto.FundAccountCommand;
import com.cgcpms.cashbook.entity.CashJournalEntry;
import com.cgcpms.cashbook.entity.FundAccount;
import com.cgcpms.cashbook.mapper.CashJournalChangeLogMapper;
import com.cgcpms.cashbook.mapper.CashJournalEntryMapper;
import com.cgcpms.cashbook.mapper.FundAccountMapper;
import com.cgcpms.cashbook.service.CashJournalAlertService;
import com.cgcpms.cashbook.service.CashJournalService;
import com.cgcpms.cashbook.service.FundAccountService;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.contract.mapper.CtContractMapper;
import com.cgcpms.file.mapper.SysFileMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinancialOptimisticLockTest {

    @AfterEach
    void tearDown() {
        TestUserContext.clear();
    }

    @Test
    void journalUpdateConflictStopsBeforeAuditSnapshot() {
        TestUserContext.setAdmin(934091L, 1L);
        CashJournalEntryMapper entryMapper = mock(CashJournalEntryMapper.class);
        CashJournalChangeLogMapper changeLogMapper = mock(CashJournalChangeLogMapper.class);
        CashJournalEntry entry = new CashJournalEntry();
        entry.setId(1L);
        entry.setTenantId(934091L);
        entry.setStatus(CashbookConstants.Status.DRAFT);
        entry.setSourceType(CashbookConstants.SourceType.MANUAL);
        entry.setAmount(new BigDecimal("1.00"));
        when(entryMapper.selectById(1L)).thenReturn(entry);
        when(entryMapper.selectByIdForUpdate(1L, 934091L)).thenReturn(entry);
        when(entryMapper.updateById(any(CashJournalEntry.class))).thenReturn(0);
        CashJournalService service = new CashJournalService(entryMapper, mock(FundAccountMapper.class),
                mock(FundAccountService.class), mock(CtContractMapper.class), mock(ProjectAccessChecker.class),
                changeLogMapper, mock(SysFileMapper.class), new ObjectMapper(), mock(CashJournalAlertService.class));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateDraft(1L, new CashJournalUpdateRequest()));

        assertEquals("CASH_JOURNAL_CONCURRENT_MODIFICATION", error.getCode());
        verify(changeLogMapper, never()).insert(any(com.cgcpms.cashbook.entity.CashJournalChangeLog.class));
    }

    @Test
    void accountUpdateConflictIsExplicit() {
        TestUserContext.setAdmin(934091L, 1L);
        FundAccountMapper accountMapper = mock(FundAccountMapper.class);
        CashJournalEntryMapper entryMapper = mock(CashJournalEntryMapper.class);
        FundAccount account = new FundAccount();
        account.setId(2L);
        account.setTenantId(934091L);
        account.setOpeningDate(LocalDate.of(2026, 7, 1));
        account.setOpeningBalance(new BigDecimal("10.00"));
        when(accountMapper.selectById(2L)).thenReturn(account);
        when(accountMapper.selectByIdForUpdate(2L, 934091L)).thenReturn(account);
        when(accountMapper.updateById(any(FundAccount.class))).thenReturn(0);
        FundAccountService service = new FundAccountService(accountMapper, entryMapper);
        FundAccountCommand command = new FundAccountCommand();
        command.setAccountCode("LOCK-2");
        command.setAccountName("Lock 2");
        command.setAccountType(CashbookConstants.AccountType.CASH);
        command.setOpeningDate(account.getOpeningDate());
        command.setOpeningBalance(account.getOpeningBalance());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateFundAccount(2L, command));

        assertEquals("FUND_ACCOUNT_CONCURRENT_MODIFICATION", error.getCode());
    }
}
