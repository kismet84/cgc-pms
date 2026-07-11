package com.cgcpms.cashbook;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.cashbook.constant.CashbookConstants;
import com.cgcpms.cashbook.entity.FundAccount;
import com.cgcpms.cashbook.entity.CashJournalEntry;
import com.cgcpms.cashbook.mapper.CashJournalEntryMapper;
import com.cgcpms.cashbook.mapper.FundAccountMapper;
import com.cgcpms.common.TestUserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "spring.main.allow-circular-references=true")
@ActiveProfiles("local")
@Transactional
class FundAccountMapperTest {

    private static final long TENANT_ID = 934001L;

    @Autowired
    private FundAccountMapper fundAccountMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    void accountCodeIsUniqueWithinTenantAndAmountKeepsTwoDecimals() {
        FundAccount first = account(93400101L, TENANT_ID, "BANK-001", "1234.50");
        fundAccountMapper.insert(first);

        FundAccount stored = fundAccountMapper.selectById(first.getId());
        assertEquals(0, new BigDecimal("1234.50").compareTo(stored.getOpeningBalance()));
        assertEquals(CashbookConstants.AccountType.BANK, stored.getAccountType());

        assertThrows(DuplicateKeyException.class,
                () -> fundAccountMapper.insert(account(93400102L, TENANT_ID, "BANK-001", "1.00")));
    }

    @Test
    void sameAccountCodeCanBeUsedByAnotherTenant() {
        fundAccountMapper.insert(account(93400103L, TENANT_ID, "CASH-001", "10.00"));
        fundAccountMapper.insert(account(93400104L, TENANT_ID + 1, "CASH-001", "20.00"));

        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fund_account WHERE account_code = 'CASH-001'", Integer.class));
    }

    @Test
    void cashbookAuthoritiesDoNotGrantFinanceGlobalFileAccess() {
        List<String> cashbookPermissions = List.of(
                "cashbook:journal:query",
                "cashbook:journal:maintain",
                "cashbook:journal:export",
                "cashbook:account:manage");
        assertEquals(4, countMenus(cashbookPermissions));
        assertEquals(0, countMenus(List.of("file:upload", "file:query", "file:delete")));
        assertEquals(0, countMenuIds(List.of(956L, 957L, 958L)));

        assertEquals(3, countRolePermissions(6L, List.of(
                "cashbook:journal:query",
                "cashbook:journal:maintain",
                "cashbook:journal:export")));
        assertEquals(0, countRolePermissions(6L, List.of(
                "cashbook:account:manage", "file:upload", "file:query", "file:delete")));
        assertEquals(4, countRolePermissions(1L, List.of(
                "cashbook:journal:query",
                "cashbook:journal:maintain",
                "cashbook:journal:export",
                "cashbook:account:manage")));
    }

    @Test
    void currentBalanceIgnoresLegacyEffectiveEntriesBeforeOpeningDate() {
        FundAccount account = account(93400110L, TENANT_ID, "CASH-OPENING", "100.00");
        fundAccountMapper.insert(account);
        entryMapper.insert(entry(93400111L, account.getId(), LocalDate.of(2026, 7, 9), "50.00"));
        entryMapper.insert(entry(93400112L, account.getId(), LocalDate.of(2026, 7, 10), "20.00"));

        assertEquals(0, new BigDecimal("120.00").compareTo(
                fundAccountMapper.selectCurrentBalance(account.getId(), TENANT_ID)));
    }

    private int countMenus(List<String> permissions) {
        String placeholders = String.join(",", permissions.stream().map(value -> "?").toList());
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_menu WHERE deleted_flag = 0 AND perms IN (" + placeholders + ")",
                Integer.class,
                permissions.toArray());
    }

    private int countMenuIds(List<Long> menuIds) {
        String placeholders = String.join(",", menuIds.stream().map(value -> "?").toList());
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_menu WHERE id IN (" + placeholders + ")",
                Integer.class,
                menuIds.toArray());
    }

    private int countRolePermissions(Long roleId, List<String> permissions) {
        String placeholders = String.join(",", permissions.stream().map(value -> "?").toList());
        Object[] args = new Object[permissions.size() + 1];
        args[0] = roleId;
        for (int i = 0; i < permissions.size(); i++) {
            args[i + 1] = permissions.get(i);
        }
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_role_menu rm JOIN sys_menu m ON m.id = rm.menu_id "
                        + "WHERE rm.role_id = ? AND m.deleted_flag = 0 AND m.perms IN (" + placeholders + ")",
                Integer.class,
                args);
    }

    private FundAccount account(Long id, Long tenantId, String code, String openingBalance) {
        FundAccount account = new FundAccount();
        account.setId(id);
        account.setTenantId(tenantId);
        account.setAccountCode(code);
        account.setAccountName(code);
        account.setAccountType(code.startsWith("BANK")
                ? CashbookConstants.AccountType.BANK
                : CashbookConstants.AccountType.CASH);
        account.setOpeningDate(LocalDate.of(2026, 7, 10));
        account.setOpeningBalance(new BigDecimal(openingBalance));
        account.setEnabledFlag(1);
        account.setVersion(0);
        return account;
    }


    private CashJournalEntry entry(Long id, Long accountId, LocalDate businessDate, String amount) {
        CashJournalEntry entry = new CashJournalEntry();
        entry.setId(id);
        entry.setTenantId(TENANT_ID);
        entry.setEntryNo("CJ-20260710-" + id.toString().substring(id.toString().length() - 3));
        entry.setAccountId(accountId);
        entry.setDirection(CashbookConstants.Direction.IN);
        entry.setAmount(new BigDecimal(amount));
        entry.setBusinessDate(businessDate);
        entry.setSummary("opening boundary");
        entry.setSourceType(CashbookConstants.SourceType.MANUAL);
        entry.setStatus(CashbookConstants.Status.ARCHIVED);
        entry.setClosureDueAt(LocalDateTime.now());
        entry.setArchivedBy(1L);
        entry.setArchivedAt(LocalDateTime.now());
        entry.setVersion(0);
        return entry;
    }
}
