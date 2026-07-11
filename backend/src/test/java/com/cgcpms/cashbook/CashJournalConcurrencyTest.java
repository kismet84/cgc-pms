package com.cgcpms.cashbook;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.cashbook.constant.CashbookConstants;
import com.cgcpms.cashbook.dto.CashJournalCreateRequest;
import com.cgcpms.cashbook.dto.FundAccountCommand;
import com.cgcpms.cashbook.entity.CashJournalEntry;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "spring.main.allow-circular-references=true")
@ActiveProfiles("local")
class CashJournalConcurrencyTest {

    private static final long TENANT_ID = 934092L;

    @Autowired CashJournalEntryMapper entryMapper;
    @Autowired FundAccountMapper accountMapper;
    @Autowired FundAccountService accountService;
    @Autowired CtContractMapper contractMapper;
    @Autowired ProjectAccessChecker projectAccessChecker;
    @Autowired CashJournalChangeLogMapper changeLogMapper;
    @Autowired SysFileMapper fileMapper;
    @Autowired ObjectMapper objectMapper;
    @Autowired CashJournalAlertService alertService;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanup();
        TestUserContext.setAdmin(TENANT_ID, 1L);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        cleanup();
    }

    @Test
    void databaseUniqueConstraintRetriesNumberCollisionAcrossServiceInstances() throws Exception {
        CountDownLatch bothReadSameNumber = new CountDownLatch(2);
        AtomicInteger reads = new AtomicInteger();
        CashJournalEntryMapper barrierMapper = (CashJournalEntryMapper) Proxy.newProxyInstance(
                CashJournalEntryMapper.class.getClassLoader(), new Class<?>[]{CashJournalEntryMapper.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("selectLastEntryNo") && reads.incrementAndGet() <= 2) {
                        bothReadSameNumber.countDown();
                        assertTrue(bothReadSameNumber.await(5, TimeUnit.SECONDS));
                    }
                    try {
                        return method.invoke(entryMapper, args);
                    } catch (InvocationTargetException error) {
                        throw error.getCause();
                    }
                });
        CashJournalService first = service(barrierMapper);
        CashJournalService second = service(barrierMapper);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<String> one = executor.submit(() -> createInTransaction(transaction, first, start));
            Future<String> two = executor.submit(() -> createInTransaction(transaction, second, start));
            start.countDown();

            List<String> numbers = List.of(one.get(10, TimeUnit.SECONDS), two.get(10, TimeUnit.SECONDS));

            assertEquals(2, numbers.stream().distinct().count());
            assertEquals(2, reads.get(), "每个服务实例只能执行一次初始流水号读取");
            assertEquals(2, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM cash_journal_entry WHERE tenant_id = ?", Integer.class, TENANT_ID));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void firstEntryWaitsForAccountLock() throws Exception {
        long accountId = createAccount("LOCK-CREATE", "100.00");
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> holder = executor.submit(() -> transaction.executeWithoutResult(status -> withUser(() -> {
                accountMapper.selectByIdForUpdate(accountId, TENANT_ID);
                locked.countDown();
                await(release);
            })));
            assertTrue(locked.await(5, TimeUnit.SECONDS));
            Future<?> creator = executor.submit(() -> withUser(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> service(entryMapper).createManual(request(accountId)))));

            Thread.sleep(200);
            assertFalse(creator.isDone(), "首条流水必须等待账户行锁");
            release.countDown();
            holder.get(5, TimeUnit.SECONDS);
            creator.get(5, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void accountUpdateSeesFirstEntryCommittedWhileWaitingForLock() throws Exception {
        long accountId = createAccount("LOCK-OPENING", "100.00");
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> firstEntry = executor.submit(() -> transaction.executeWithoutResult(status -> withUser(() -> {
                accountMapper.selectByIdForUpdate(accountId, TENANT_ID);
                locked.countDown();
                await(release);
                entryMapper.insert(entry(accountId));
            })));
            assertTrue(locked.await(5, TimeUnit.SECONDS));
            Future<Throwable> update = executor.submit(() -> {
                try {
                    withUser(() -> accountService.updateFundAccount(accountId,
                            accountCommand("LOCK-OPENING", "101.00")));
                    return null;
                } catch (Throwable error) {
                    return error;
                }
            });

            Thread.sleep(200);
            release.countDown();
            firstEntry.get(5, TimeUnit.SECONDS);
            Throwable error = update.get(5, TimeUnit.SECONDS);

            BusinessException conflict = assertInstanceOf(BusinessException.class, error);
            assertEquals("FUND_ACCOUNT_OPENING_LOCKED", conflict.getCode());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private String createInTransaction(TransactionTemplate transaction, CashJournalService service,
                                       CountDownLatch start) {
        return transaction.execute(status -> {
            TestUserContext.setAdmin(TENANT_ID, 1L);
            try {
                await(start);
                return service.createManual(request(null)).getEntryNo();
            } finally {
                UserContext.clear();
            }
        });
    }

    private CashJournalService service(CashJournalEntryMapper mapper) {
        return new CashJournalService(mapper, accountMapper, accountService, contractMapper, projectAccessChecker,
                changeLogMapper, fileMapper, objectMapper, alertService);
    }

    private long createAccount(String code, String openingBalance) {
        return Long.parseLong(accountService.createFundAccount(accountCommand(code, openingBalance)).getId());
    }

    private FundAccountCommand accountCommand(String code, String openingBalance) {
        FundAccountCommand command = new FundAccountCommand();
        command.setAccountCode(code);
        command.setAccountName(code);
        command.setAccountType(CashbookConstants.AccountType.CASH);
        command.setOpeningDate(LocalDate.of(2026, 7, 1));
        command.setOpeningBalance(new BigDecimal(openingBalance));
        return command;
    }

    private CashJournalCreateRequest request(Long accountId) {
        CashJournalCreateRequest request = new CashJournalCreateRequest();
        request.setAccountId(accountId);
        request.setDirection(CashbookConstants.Direction.IN);
        request.setAmount(new BigDecimal("1.00"));
        request.setBusinessDate(LocalDate.of(2026, 7, 10));
        request.setSummary("concurrency test");
        return request;
    }

    private CashJournalEntry entry(long accountId) {
        CashJournalEntry entry = new CashJournalEntry();
        entry.setTenantId(TENANT_ID);
        entry.setEntryNo("CJ-20260710-991");
        entry.setAccountId(accountId);
        entry.setDirection(CashbookConstants.Direction.IN);
        entry.setAmount(new BigDecimal("1.00"));
        entry.setBusinessDate(LocalDate.of(2026, 7, 10));
        entry.setSummary("first entry");
        entry.setSourceType(CashbookConstants.SourceType.MANUAL);
        entry.setStatus(CashbookConstants.Status.DRAFT);
        entry.setClosureDueAt(LocalDateTime.now().plusHours(24));
        entry.setVersion(0);
        return entry;
    }

    private void withUser(Runnable action) {
        TestUserContext.setAdmin(TENANT_ID, 1L);
        try {
            action.run();
        } finally {
            UserContext.clear();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("latch timeout");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM cash_journal_change_log WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM cash_journal_entry WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM fund_account WHERE tenant_id = ?", TENANT_ID);
    }
}
