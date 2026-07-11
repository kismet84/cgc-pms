package com.cgcpms.cashbook;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.cashbook.constant.CashbookConstants;
import com.cgcpms.cashbook.dto.FundAccountCommand;
import com.cgcpms.cashbook.entity.CashJournalEntry;
import com.cgcpms.cashbook.mapper.CashJournalEntryMapper;
import com.cgcpms.cashbook.service.FundAccountService;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "spring.main.allow-circular-references=true")
@ActiveProfiles("local")
@Transactional
class FundAccountServiceTest {

    private static final long TENANT_ID = 934010L;

    @Autowired
    private FundAccountService fundAccountService;

    @Autowired
    private CashJournalEntryMapper entryMapper;

    @BeforeEach
    void setUp() {
        TestUserContext.setAdmin(TENANT_ID, 1L);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void createReturnsMaskedBankAccountAndRejectsDuplicateCode() {
        FundAccountCommand command = command("BANK-SVC-001", "6222021234567890", new BigDecimal("100.00"));

        var created = fundAccountService.createFundAccount(command);

        assertTrue(created.getBankAccountNo().startsWith("****"));
        assertTrue(created.getBankAccountNo().endsWith("7890"));
        BusinessException duplicate = assertThrows(BusinessException.class,
                () -> fundAccountService.createFundAccount(command));
        assertEquals("FUND_ACCOUNT_CODE_DUPLICATE", duplicate.getCode());
    }

    @Test
    void managementListReturnsFullBankAccountWhileDefaultListStaysMasked() {
        FundAccountCommand command = command("BANK-SVC-003", "6222021234560003", new BigDecimal("100.00"));
        command.setRemark("management only");
        fundAccountService.createFundAccount(command);

        assertEquals("****0003", fundAccountService.list().getFirst().getBankAccountNo());
        assertNull(fundAccountService.list().getFirst().getRemark());
        assertEquals("6222021234560003", fundAccountService.listForManagement().getFirst().getBankAccountNo());
        assertEquals("management only", fundAccountService.listForManagement().getFirst().getRemark());
    }

    @Test
    void serviceRejectsInvalidOpeningBalancePrecision() {
        FundAccountCommand fractional = command("BANK-SVC-AMOUNT-1", "6222021234560011",
                new BigDecimal("1.001"));
        assertEquals("FUND_ACCOUNT_INVALID",
                assertThrows(BusinessException.class, () -> fundAccountService.createFundAccount(fractional)).getCode());

        FundAccountCommand oversized = command("BANK-SVC-AMOUNT-2", "6222021234560012",
                new BigDecimal("10000000000000000.00"));
        assertEquals("FUND_ACCOUNT_INVALID",
                assertThrows(BusinessException.class, () -> fundAccountService.createFundAccount(oversized)).getCode());
    }

    @Test
    void openingBalanceCannotChangeAfterJournalExists() {
        var created = fundAccountService.createFundAccount(
                command("BANK-SVC-002", "6222020000000002", new BigDecimal("100.00")));
        CashJournalEntry entry = new CashJournalEntry();
        entry.setTenantId(TENANT_ID);
        entry.setEntryNo("CJ-20260710-101");
        entry.setAccountId(Long.valueOf(created.getId()));
        entry.setDirection(CashbookConstants.Direction.IN);
        entry.setAmount(new BigDecimal("1.00"));
        entry.setBusinessDate(LocalDate.of(2026, 7, 10));
        entry.setSummary("opening lock test");
        entry.setSourceType(CashbookConstants.SourceType.MANUAL);
        entry.setStatus(CashbookConstants.Status.DRAFT);
        entry.setClosureDueAt(LocalDateTime.now().plusHours(24));
        entry.setVersion(0);
        entryMapper.insert(entry);

        FundAccountCommand changed = command("BANK-SVC-002", "6222020000000002", new BigDecimal("101.00"));
        BusinessException error = assertThrows(BusinessException.class,
                () -> fundAccountService.updateFundAccount(Long.valueOf(created.getId()), changed));
        assertEquals("FUND_ACCOUNT_OPENING_LOCKED", error.getCode());
    }

    private FundAccountCommand command(String code, String accountNo, BigDecimal openingBalance) {
        FundAccountCommand command = new FundAccountCommand();
        command.setAccountCode(code);
        command.setAccountName(code);
        command.setAccountType(CashbookConstants.AccountType.BANK);
        command.setBankName("Test Bank");
        command.setBankAccountNo(accountNo);
        command.setOpeningDate(LocalDate.of(2026, 7, 1));
        command.setOpeningBalance(openingBalance);
        return command;
    }
}
