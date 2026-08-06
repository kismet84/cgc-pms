package com.cgcpms.communication;

import com.cgcpms.common.TestUserContext;
import com.cgcpms.communication.service.CommunicationEventService;
import com.cgcpms.communication.service.CommunicationService;
import com.cgcpms.file.service.FileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "CGCPMS_M75_MYSQL_CONCURRENCY", matches = "true")
class CommunicationMySqlConcurrencyTest {

    private static final long USER_ONE = 9_975_001L;
    private static final long USER_TWO = 9_975_002L;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;

    private TransactionTemplate transactions;
    private CommunicationService service;
    private ObjectProvider<CommunicationService> selfProvider;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.locations",
                () -> "classpath:db/migration,classpath:db/migration-legacy");
    }

    @BeforeEach
    void setUp() {
        cleanup();
        jdbc.update("""
                INSERT INTO sys_user(id,tenant_id,username,password,real_name,status,is_admin,deleted_flag)
                VALUES(?,0,'m75-mysql-one','-','并发用户一','ENABLE',0,0),
                      (?,0,'m75-mysql-two','-','并发用户二','ENABLE',0,0)
                """, USER_ONE, USER_TWO);
        @SuppressWarnings("unchecked")
        ObjectProvider<CommunicationService> self = mock(ObjectProvider.class);
        selfProvider = self;
        service = new CommunicationService(
                jdbc, mock(FileService.class), mock(CommunicationEventService.class), self);
        when(selfProvider.getObject()).thenReturn(service);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @AfterEach
    void tearDown() {
        TestUserContext.clear();
        cleanup();
    }

    @Test
    void oppositeDirectCreationAndConcurrentSendUseOneConversationAndUniqueSequence() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier createBarrier = new CyclicBarrier(2);
            var createOne = pool.submit(() -> createDirect(USER_ONE, USER_TWO, createBarrier));
            var createTwo = pool.submit(() -> createDirect(USER_TWO, USER_ONE, createBarrier));
            String firstId = createOne.get(15, TimeUnit.SECONDS);
            String secondId = createTwo.get(15, TimeUnit.SECONDS);
            assertEquals(firstId, secondId);
            long conversationId = Long.parseLong(firstId);
            assertEquals(1L, jdbc.queryForObject("""
                    SELECT COUNT(*) FROM communication_conversation
                    WHERE tenant_id=0 AND direct_pair_key=?
                    """, Long.class, USER_ONE + ":" + USER_TWO));
            assertEquals(2L, jdbc.queryForObject("""
                    SELECT COUNT(*) FROM communication_member
                    WHERE tenant_id=0 AND conversation_id=? AND status='ACTIVE'
                    """, Long.class, conversationId));

            TestUserContext.setUser(0, USER_ONE, "m75-mysql-one", List.of());
            long draftOne = Long.parseLong(transactions.execute(status -> service.createDraft(
                    conversationId, "one", "m75-mysql-client-one")).id());
            TestUserContext.setUser(0, USER_TWO, "m75-mysql-two", List.of());
            long draftTwo = Long.parseLong(transactions.execute(status -> service.createDraft(
                    conversationId, "two", "m75-mysql-client-two")).id());

            CyclicBarrier sendBarrier = new CyclicBarrier(2);
            var sendOne = pool.submit(() -> send(USER_ONE, draftOne, sendBarrier));
            var sendTwo = pool.submit(() -> send(USER_TWO, draftTwo, sendBarrier));
            assertEquals(Set.of("1", "2"), Set.of(
                    sendOne.get(15, TimeUnit.SECONDS), sendTwo.get(15, TimeUnit.SECONDS)));
            assertEquals(List.of(1L, 2L), jdbc.queryForList("""
                    SELECT seq FROM communication_message
                    WHERE tenant_id=0 AND conversation_id=? AND status='SENT' ORDER BY seq
                    """, Long.class, conversationId));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void draftCleanupContinuesWhenCandidateIsDeletedOrSentBeforeLock() {
        TestUserContext.setUser(0, USER_ONE, "m75-mysql-one", List.of());
        long conversationId = Long.parseLong(transactions.execute(status ->
                service.createConversation("DIRECT", null, List.of(USER_TWO))).id());
        long missingDraft = Long.parseLong(transactions.execute(status -> service.createDraft(
                conversationId, "missing", "m76-mysql-race-missing")).id());
        long sentDraft = Long.parseLong(transactions.execute(status -> service.createDraft(
                conversationId, "sent", "m76-mysql-race-sent")).id());
        long survivingDraft = Long.parseLong(transactions.execute(status -> service.createDraft(
                conversationId, "surviving", "m76-mysql-race-surviving")).id());
        jdbc.update("UPDATE communication_message SET created_at=DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 25 HOUR) WHERE id IN (?,?,?)",
                missingDraft, sentDraft, survivingDraft);

        CommunicationService proxy = mock(CommunicationService.class);
        when(selfProvider.getObject()).thenReturn(proxy);
        doAnswer(invocation -> {
            long messageId = invocation.getArgument(0);
            if (messageId == missingDraft) jdbc.update(
                    "UPDATE communication_message SET deleted_flag=1 WHERE id=?", messageId);
            if (messageId == sentDraft) jdbc.update(
                    "UPDATE communication_message SET status='SENT',seq=1 WHERE id=?", messageId);
            transactions.executeWithoutResult(status -> service.deleteDraft(messageId));
            return null;
        }).when(proxy).deleteDraft(org.mockito.ArgumentMatchers.anyLong());

        assertDoesNotThrow(service::expireDrafts);
        assertEquals(1, jdbc.queryForObject(
                "SELECT deleted_flag FROM communication_message WHERE id=?", Integer.class, survivingDraft));
        assertEquals("SENT", jdbc.queryForObject(
                "SELECT status FROM communication_message WHERE id=?", String.class, sentDraft));
    }

    private String createDirect(long current, long target, CyclicBarrier barrier) throws Exception {
        TestUserContext.setUser(0, current, "mysql-concurrent", List.of());
        try {
            barrier.await(10, TimeUnit.SECONDS);
            return transactions.execute(status ->
                    service.createConversation("DIRECT", null, List.of(target))).id();
        } finally {
            TestUserContext.clear();
        }
    }

    private String send(long current, long draftId, CyclicBarrier barrier) throws Exception {
        TestUserContext.setUser(0, current, "mysql-concurrent", List.of());
        try {
            barrier.await(10, TimeUnit.SECONDS);
            return transactions.execute(status -> service.send(draftId)).seq();
        } finally {
            TestUserContext.clear();
        }
    }

    private void cleanup() {
        jdbc.update("""
                DELETE FROM communication_message
                WHERE tenant_id=0 AND sender_id IN (?,?)
                """, USER_ONE, USER_TWO);
        jdbc.update("""
                DELETE FROM communication_member
                WHERE tenant_id=0 AND user_id IN (?,?)
                """, USER_ONE, USER_TWO);
        jdbc.update("""
                DELETE FROM communication_conversation
                WHERE tenant_id=0 AND created_by IN (?,?)
                """, USER_ONE, USER_TWO);
        jdbc.update("DELETE FROM sys_user WHERE tenant_id=0 AND id IN (?,?)", USER_ONE, USER_TWO);
    }
}
