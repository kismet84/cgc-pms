package com.cgcpms.accounting;

import com.cgcpms.accounting.entity.AccountingEntry;
import com.cgcpms.accounting.mapper.AccountingEntryLineMapper;
import com.cgcpms.accounting.mapper.AccountingEntryMapper;
import com.cgcpms.accounting.service.AccountingEntryService;
import com.cgcpms.accounting.service.AccountingPeriodGuard;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import com.cgcpms.project.auth.ProjectAccessChecker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AccountingEntryCasTest {

    @AfterEach
    void clearContext() {
        TestUserContext.clear();
    }

    @Test
    void reviewFailsClosedWhenUpdateAffectsNoRow() {
        AccountingEntryMapper entryMapper = mock(AccountingEntryMapper.class);
        AccountingPeriodGuard periodGuard = mock(AccountingPeriodGuard.class);
        AccountingEntry entry = new AccountingEntry();
        entry.setId(1L);
        entry.setTenantId(99L);
        entry.setEntryDate(LocalDate.of(2031, 1, 1));
        entry.setEntryStatus("DRAFT");
        entry.setReviewStatus("PENDING");
        entry.setCreatedBy(1L);
        when(entryMapper.selectByIdForUpdate(1L, 99L)).thenReturn(entry);
        when(entryMapper.updateById(entry)).thenReturn(0);
        AccountingEntryService service = new AccountingEntryService(entryMapper,
                mock(AccountingEntryLineMapper.class), mock(CostSubjectMapper.class),
                periodGuard, mock(ProjectAccessChecker.class));
        TestUserContext.setUser(99L, 2L, "reviewer", java.util.List.of("FINANCE"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.review(1L, true, "复核"));

        assertEquals("ENTRY_CONCURRENT_MODIFICATION", error.getCode());
        verify(periodGuard).assertWritable(LocalDate.of(2031, 1, 1));
    }
}
