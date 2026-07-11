package com.cgcpms.cashbook;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.cashbook.constant.CashbookConstants;
import com.cgcpms.cashbook.entity.CashJournalChangeLog;
import com.cgcpms.cashbook.entity.CashJournalEntry;
import com.cgcpms.cashbook.mapper.CashJournalChangeLogMapper;
import com.cgcpms.cashbook.mapper.CashJournalEntryMapper;
import com.cgcpms.cashbook.mapper.FundAccountMapper;
import com.cgcpms.cashbook.entity.FundAccount;
import com.cgcpms.cashbook.dto.CashJournalQuery;
import com.cgcpms.cashbook.vo.CashJournalEntryVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "spring.main.allow-circular-references=true")
@ActiveProfiles("local")
@Transactional
class CashJournalEntryMapperTest {

    private static final long TENANT_ID = 934002L;

    @Autowired
    private CashJournalEntryMapper entryMapper;

    @Autowired
    private CashJournalChangeLogMapper changeLogMapper;

    @Autowired
    private FundAccountMapper fundAccountMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        TestUserContext.setAdmin(TENANT_ID, 1L);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void payRecordSourceIsIdempotentWithinTenant() {
        entryMapper.insert(entry(93400201L, "CJ-20260710-001",
                CashbookConstants.SourceType.PAY_RECORD, 88001L));

        assertThrows(DuplicateKeyException.class, () -> entryMapper.insert(entry(
                93400202L,
                "CJ-20260710-002",
                CashbookConstants.SourceType.PAY_RECORD,
                88001L)));
    }

    @Test
    void manualEntriesMayHaveNullSourceIdsAndKeepDecimalPrecision() {
        entryMapper.insert(entry(93400203L, "CJ-20260710-003", CashbookConstants.SourceType.MANUAL, null));
        entryMapper.insert(entry(93400204L, "CJ-20260710-004", CashbookConstants.SourceType.MANUAL, null));

        BigDecimal stored = jdbcTemplate.queryForObject(
                "SELECT amount FROM cash_journal_entry WHERE id = ?", BigDecimal.class, 93400203L);
        assertEquals(0, new BigDecimal("88.12").compareTo(stored));
    }

    @Test
    void changeLogMapsAppendOnlyAuditFields() {
        CashJournalChangeLog log = new CashJournalChangeLog();
        log.setId(93400205L);
        log.setTenantId(TENANT_ID);
        log.setJournalEntryId(93400203L);
        log.setAction(CashbookConstants.ChangeAction.REOPEN);
        log.setReason("correction");
        log.setBeforeSnapshot("{\"status\":\"ARCHIVED\"}");
        log.setAfterSnapshot("{\"status\":\"DRAFT\"}");
        log.setOperatorId(1L);
        log.setCreatedAt(LocalDateTime.now());

        changeLogMapper.insert(log);

        CashJournalChangeLog stored = changeLogMapper.selectById(log.getId());
        assertNotNull(stored);
        assertEquals(CashbookConstants.ChangeAction.REOPEN, stored.getAction());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE TABLE_NAME = 'cash_journal_change_log' AND COLUMN_NAME IN ('updated_at','deleted_flag')",
                Integer.class));
    }

    @Test
    void entryNumberCollisionLookupUsesCurrentRead() {
        Method method = Arrays.stream(CashJournalEntryMapper.class.getMethods())
                .filter(candidate -> candidate.getName().equals("selectByEntryNoForUpdate"))
                .findFirst().orElse(null);

        assertNotNull(method, "流水号碰撞判断必须提供独立的数据库当前读");
        org.apache.ibatis.annotations.Select select = method.getAnnotation(org.apache.ibatis.annotations.Select.class);
        assertNotNull(select, "流水号碰撞当前读必须显式声明 SQL");
        String sql = String.join(" ", select.value()).replaceAll("\\s+", " ").toUpperCase();
        assertTrue(sql.contains("DELETED_FLAG = 0"));
        assertTrue(sql.contains("FOR UPDATE"));
    }

    @Test
    void runningBalanceIgnoresLegacyEffectiveEntriesBeforeOpeningDate() {
        FundAccount account = new FundAccount();
        account.setId(93400220L);
        account.setTenantId(TENANT_ID);
        account.setAccountCode("RUNNING-OPENING");
        account.setAccountName("Running Opening");
        account.setAccountType(CashbookConstants.AccountType.CASH);
        account.setOpeningDate(LocalDate.of(2026, 7, 10));
        account.setOpeningBalance(new BigDecimal("100.00"));
        account.setEnabledFlag(1);
        account.setVersion(0);
        fundAccountMapper.insert(account);

        CashJournalEntry legacy = entry(93400221L, "CJ-20260709-021", CashbookConstants.SourceType.MANUAL, null);
        legacy.setAccountId(account.getId());
        legacy.setBusinessDate(LocalDate.of(2026, 7, 9));
        legacy.setStatus(CashbookConstants.Status.ARCHIVED);
        entryMapper.insert(legacy);
        CashJournalEntry current = entry(93400222L, "CJ-20260710-022", CashbookConstants.SourceType.MANUAL, null);
        current.setAccountId(account.getId());
        current.setBusinessDate(LocalDate.of(2026, 7, 10));
        current.setAmount(new BigDecimal("10.00"));
        current.setDirection(CashbookConstants.Direction.IN);
        current.setStatus(CashbookConstants.Status.ARCHIVED);
        entryMapper.insert(current);

        CashJournalQuery query = new CashJournalQuery();
        query.setAccountId(account.getId());
        var records = entryMapper.selectPageWithBalance(new Page<CashJournalEntryVO>(1, 20), TENANT_ID, query)
                .getRecords();

        assertEquals("110.00", records.stream().filter(row -> row.getId().equals(String.valueOf(current.getId())))
                .findFirst().orElseThrow().getRunningBalance());
        assertEquals("100.00", records.stream().filter(row -> row.getId().equals(String.valueOf(legacy.getId())))
                .findFirst().orElseThrow().getRunningBalance());
    }

    private CashJournalEntry entry(Long id, String entryNo, String sourceType, Long sourceId) {
        CashJournalEntry entry = new CashJournalEntry();
        entry.setId(id);
        entry.setTenantId(TENANT_ID);
        entry.setEntryNo(entryNo);
        entry.setDirection(CashbookConstants.Direction.OUT);
        entry.setAmount(new BigDecimal("88.12"));
        entry.setBusinessDate(LocalDate.of(2026, 7, 10));
        entry.setSummary("mapper test");
        entry.setSourceType(sourceType);
        entry.setSourceId(sourceId);
        entry.setStatus(CashbookConstants.Status.PENDING_ARCHIVE);
        entry.setClosureDueAt(LocalDateTime.now().plusHours(24));
        entry.setVersion(0);
        return entry;
    }
}
