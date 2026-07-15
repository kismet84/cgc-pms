package com.cgcpms.cashbook;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.cashbook.constant.CashbookConstants;
import com.cgcpms.cashbook.dto.CashJournalCreateRequest;
import com.cgcpms.cashbook.dto.FundAccountCommand;
import com.cgcpms.cashbook.mapper.CashJournalEntryMapper;
import com.cgcpms.cashbook.service.CashJournalService;
import com.cgcpms.cashbook.service.FundAccountService;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.config.MinioConfig;
import com.cgcpms.file.auth.BusinessObjectAuthorizer;
import com.cgcpms.file.entity.SysFile;
import com.cgcpms.file.mapper.SysFileMapper;
import com.cgcpms.file.service.FileService;
import com.cgcpms.file.scan.VirusScanner;
import io.micrometer.core.instrument.MeterRegistry;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = "spring.main.allow-circular-references=true")
@ActiveProfiles("local")
class CashJournalFileConcurrencyTest {

    private static final long TENANT_ID = 934093L;

    @Autowired CashJournalService journalService;
    @Autowired FundAccountService accountService;
    @Autowired CashJournalEntryMapper entryMapper;
    @Autowired SysFileMapper fileMapper;
    @Autowired BusinessObjectAuthorizer authorizer;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanup();
        setAdminContext();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        SecurityContextHolder.clearContext();
        cleanup();
    }

    @Test
    void deleteCommitsBeforeArchiveSoArchiveSeesNoAttachment() throws Exception {
        long accountId = createAccount("FILE-RACE-DELETE", "100.00");
        long entryId = createOutflow(accountId, "10.00");
        long fileId = attach(entryId, 93409301L);
        MinioClient minioClient = mock(MinioClient.class);
        CountDownLatch deleteHasJournalLock = new CountDownLatch(1);
        CountDownLatch releaseDelete = new CountDownLatch(1);
        doAnswer(invocation -> {
            deleteHasJournalLock.countDown();
            await(releaseDelete);
            return null;
        }).when(minioClient).removeObject(any(RemoveObjectArgs.class));
        FileService fileService = fileService(minioClient);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch archiveStarted = new CountDownLatch(1);
        try {
            Future<?> deletion = executor.submit(() -> inAdminTransaction(() -> fileService.delete(fileId)));
            assertTrue(deleteHasJournalLock.await(5, TimeUnit.SECONDS));
            Future<Throwable> archive = executor.submit(() -> {
                archiveStarted.countDown();
                return capture(() -> inAdminTransaction(() -> journalService.archive(entryId)));
            });
            assertTrue(archiveStarted.await(5, TimeUnit.SECONDS));
            BusinessException error = assertInstanceOf(BusinessException.class,
                    archive.get(5, TimeUnit.SECONDS));
            assertEquals("CASH_JOURNAL_ATTACHMENT_REQUIRED", error.getCode());
            assertFalse(deletion.isDone(), "提交后的 MinIO 清理仍被测试闩锁阻塞");

            releaseDelete.countDown();
            deletion.get(5, TimeUnit.SECONDS);
        } finally {
            releaseDelete.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void archiveCommitsBeforeDeleteSoDeleteSeesImmutableStatus() throws Exception {
        long accountId = createAccount("FILE-RACE-ARCHIVE", "100.00");
        long entryId = createOutflow(accountId, "10.00");
        long fileId = attach(entryId, 93409302L);
        MinioClient minioClient = mock(MinioClient.class);
        FileService fileService = fileService(minioClient);
        CountDownLatch archivedInsideTransaction = new CountDownLatch(1);
        CountDownLatch releaseArchiveCommit = new CountDownLatch(1);
        CountDownLatch deleteStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> archive = executor.submit(() -> inAdminTransaction(() -> {
                entryMapper.selectByIdForUpdate(entryId, TENANT_ID);
                journalService.archive(entryId);
                archivedInsideTransaction.countDown();
                await(releaseArchiveCommit);
            }));
            assertTrue(archivedInsideTransaction.await(5, TimeUnit.SECONDS));
            Future<Throwable> deletion = executor.submit(() -> {
                deleteStarted.countDown();
                return capture(() -> inAdminTransaction(() -> fileService.delete(fileId)));
            });
            assertTrue(deleteStarted.await(5, TimeUnit.SECONDS));
            Thread.sleep(200);
            assertFalse(deletion.isDone(), "附件删除必须等待归档持有的流水行锁");

            releaseArchiveCommit.countDown();
            archive.get(5, TimeUnit.SECONDS);
            BusinessException error = assertInstanceOf(BusinessException.class,
                    deletion.get(5, TimeUnit.SECONDS));

            assertEquals("CASH_JOURNAL_ARCHIVED_IMMUTABLE", error.getCode());
            verify(minioClient, never()).removeObject(any(RemoveObjectArgs.class));
        } finally {
            releaseArchiveCommit.countDown();
            executor.shutdownNow();
        }
    }

    private FileService fileService(MinioClient minioClient) {
        MinioConfig config = new MinioConfig();
        config.setBucket("test");
        @SuppressWarnings("unchecked")
        ObjectProvider<MeterRegistry> meterRegistryProvider = mock(ObjectProvider.class);
        VirusScanner virusScanner = mock(VirusScanner.class);
        return new FileService(fileMapper, minioClient, config, authorizer,
                new RetryTemplate(), meterRegistryProvider, virusScanner);
    }

    private void inAdminTransaction(Runnable action) {
        setAdminContext();
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
        } finally {
            UserContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private Throwable capture(Runnable action) {
        try {
            action.run();
            return null;
        } catch (Throwable error) {
            return error;
        }
    }

    private void setAdminContext() {
        TestUserContext.setAdmin(TENANT_ID, 1L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private long createAccount(String code, String openingBalance) {
        FundAccountCommand command = new FundAccountCommand();
        command.setAccountCode(code);
        command.setAccountName(code);
        command.setAccountType(CashbookConstants.AccountType.CASH);
        command.setOpeningDate(LocalDate.of(2026, 7, 1));
        command.setOpeningBalance(new BigDecimal(openingBalance));
        return Long.parseLong(accountService.createFundAccount(command).getId());
    }

    private long createOutflow(long accountId, String amount) {
        CashJournalCreateRequest request = new CashJournalCreateRequest();
        request.setAccountId(accountId);
        request.setDirection(CashbookConstants.Direction.OUT);
        request.setAmount(new BigDecimal(amount));
        request.setBusinessDate(LocalDate.of(2026, 7, 10));
        request.setSummary("file concurrency test");
        return Long.parseLong(journalService.createManual(request).getId());
    }

    private long attach(long entryId, long fileId) {
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
        return fileId;
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
        jdbcTemplate.update("DELETE FROM sys_file WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM cash_journal_change_log WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM cash_journal_entry WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM fund_account WHERE tenant_id = ?", TENANT_ID);
    }
}
