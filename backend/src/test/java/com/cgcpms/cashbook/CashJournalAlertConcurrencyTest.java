package com.cgcpms.cashbook;

import com.cgcpms.alert.mapper.AlertLogMapper;
import com.cgcpms.alert.notification.AlertNotificationDispatcher;
import com.cgcpms.alert.service.AlertLifecycleService;
import com.cgcpms.cashbook.constant.CashbookConstants;
import com.cgcpms.cashbook.entity.CashJournalEntry;
import com.cgcpms.cashbook.mapper.CashJournalEntryMapper;
import com.cgcpms.cashbook.service.CashJournalAlertRecipientResolver;
import com.cgcpms.cashbook.service.CashJournalAlertService;
import com.cgcpms.file.mapper.SysFileMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "spring.main.allow-circular-references=true")
@ActiveProfiles("local")
class CashJournalAlertConcurrencyTest {

    private static final long TENANT_ID = 934043L;

    @Autowired CashJournalEntryMapper entryMapper;
    @Autowired SysFileMapper fileMapper;
    @Autowired AlertLogMapper alertLogMapper;
    @Autowired AlertLifecycleService alertLifecycleService;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanup();
        CashJournalEntry entry = new CashJournalEntry();
        entry.setTenantId(TENANT_ID);
        entry.setEntryNo("CJ-20260710-043");
        entry.setDirection(CashbookConstants.Direction.IN);
        entry.setAmount(new BigDecimal("10.00"));
        entry.setBusinessDate(LocalDate.of(2026, 7, 10));
        entry.setSummary("concurrent alert");
        entry.setSourceType(CashbookConstants.SourceType.MANUAL);
        entry.setStatus(CashbookConstants.Status.DRAFT);
        entry.setClosureDueAt(LocalDateTime.now().minusMinutes(1));
        entry.setVersion(0);
        entryMapper.insert(entry);
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void openAlertDedupLookupUsesLockingCurrentRead() throws Exception {
        org.apache.ibatis.annotations.Select select = AlertLogMapper.class
                .getMethod("selectOpenByDedupKey", Long.class, String.class)
                .getAnnotation(org.apache.ibatis.annotations.Select.class);

        String sql = String.join(" ", select.value()).replaceAll("\\s+", " ").toUpperCase();
        assertTrue(sql.contains("FOR UPDATE"),
                "MySQL RR 下告警去重必须使用锁定当前读，不能沿用事务快照");
    }

    @Test
    void databaseRowLockDeduplicatesAcrossServiceInstances() throws Exception {
        CountDownLatch candidatesSelected = new CountDownLatch(2);
        CountDownLatch secondOpenLookup = new CountDownLatch(1);
        AtomicInteger openLookups = new AtomicInteger();
        CashJournalEntryMapper coordinatedEntries = proxy(CashJournalEntryMapper.class, entryMapper, (method, result) -> {
            if (method.getName().equals("selectOverdueForTenant")) {
                candidatesSelected.countDown();
                assertTrue(candidatesSelected.await(5, TimeUnit.SECONDS));
            }
            return result;
        });
        AlertLogMapper coordinatedAlerts = proxy(AlertLogMapper.class, alertLogMapper, (method, result) -> {
            if (method.getName().equals("selectOpenByDedupKey")) {
                if (openLookups.incrementAndGet() == 1) {
                    secondOpenLookup.await(300, TimeUnit.MILLISECONDS);
                } else {
                    secondOpenLookup.countDown();
                }
            }
            return result;
        });
        CashJournalAlertRecipientResolver recipientResolver = mock(CashJournalAlertRecipientResolver.class);
        when(recipientResolver.resolve(TENANT_ID)).thenReturn(Set.of(93404301L));
        AlertNotificationDispatcher notificationDispatcher = mock(AlertNotificationDispatcher.class);
        CashJournalAlertService first = service(
                coordinatedEntries, coordinatedAlerts, recipientResolver, notificationDispatcher);
        CashJournalAlertService second = service(
                coordinatedEntries, coordinatedAlerts, recipientResolver, notificationDispatcher);
        TransactionTemplate firstTransaction = new TransactionTemplate(transactionManager);
        TransactionTemplate secondTransaction = new TransactionTemplate(transactionManager);
        firstTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        secondTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> one = executor.submit(() -> firstTransaction.execute(status -> first.evaluateOverdue(TENANT_ID)));
            Future<Integer> two = executor.submit(() -> secondTransaction.execute(status -> second.evaluateOverdue(TENANT_ID)));

            assertEquals(1, one.get(10, TimeUnit.SECONDS) + two.get(10, TimeUnit.SECONDS));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM alert_log WHERE tenant_id = ? AND source_type = 'CASH_JOURNAL' "
                            + "AND source_id = (SELECT id FROM cash_journal_entry WHERE tenant_id = ?)",
                    Integer.class, TENANT_ID, TENANT_ID));
            verify(notificationDispatcher, times(1)).dispatchAlertCreated(
                    eq(TENANT_ID), eq(93404301L), any(), eq(CashJournalAlertService.TITLE));
        } finally {
            executor.shutdownNow();
        }
    }

    private CashJournalAlertService service(CashJournalEntryMapper entries, AlertLogMapper alerts,
                                            CashJournalAlertRecipientResolver recipients,
                                            AlertNotificationDispatcher notifications) {
        return new CashJournalAlertService(entries, fileMapper, alerts, recipients, notifications,
                alertLifecycleService);
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, T delegate, AfterInvocation after) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            try {
                Object result = method.invoke(delegate, args);
                return after.apply(method, result);
            } catch (InvocationTargetException error) {
                throw error.getCause();
            }
        });
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM alert_log WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM cash_journal_entry WHERE tenant_id = ?", TENANT_ID);
    }

    @FunctionalInterface
    private interface AfterInvocation {
        Object apply(java.lang.reflect.Method method, Object result) throws Exception;
    }
}
