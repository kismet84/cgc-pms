package com.cgcpms.communication;

import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.communication.service.CommunicationEventService;
import com.cgcpms.communication.service.CommunicationService;
import com.cgcpms.file.service.FileService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommunicationServiceTest {

    private static final long USER_ONE = 991001L;
    private static final long USER_TWO = 991002L;
    private static final long OUTSIDER = 991003L;

    private CommunicationService service;
    private CountingJdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private FileService files;
    private CommunicationEventService events;

    @BeforeEach
    void setUp() {
        Flyway flyway = Flyway.configure()
                .dataSource("jdbc:h2:mem:communication_service;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "")
                .locations("classpath:db/migration-h2", "filesystem:src/main/resources/db/migration-h2-legacy",
                        "classpath:com/cgcpms/common/migration")
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();
        DataSource dataSource = flyway.getConfiguration().getDataSource();
        jdbc = new CountingJdbcTemplate(dataSource);
        jdbc.update("""
                INSERT INTO sys_user(id,tenant_id,username,password,real_name,status,is_admin,deleted_flag)
                VALUES(991001,0,'comm-one','-', '用户一','ENABLE',0,0),
                      (991002,0,'comm-two','-', '用户二','ENABLE',0,0),
                      (991003,0,'comm-out','-', '局外人','ENABLE',0,0)
                """);
        @SuppressWarnings("unchecked")
        ObjectProvider<CommunicationService> selfProvider = mock(ObjectProvider.class);
        files = mock(FileService.class);
        events = mock(CommunicationEventService.class);
        service = new CommunicationService(jdbc, files, events, selfProvider);
        when(selfProvider.getObject()).thenReturn(service);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        TestUserContext.setUser(0, USER_ONE, "comm-one", List.of());
    }

    @AfterEach
    void tearDown() {
        TestUserContext.clear();
    }

    @Test
    void directConversationMessageSequenceAndIdempotencyStayServerAuthoritative() {
        var conversation = transactions.execute(status ->
                service.createConversation("DIRECT", null, List.of(USER_TWO)));
        var duplicate = transactions.execute(status ->
                service.createConversation("DIRECT", null, List.of(USER_TWO)));
        assertEquals(conversation.id(), duplicate.id());

        var draft = transactions.execute(status ->
                service.createDraft(Long.parseLong(conversation.id()), "hello", "client-msg-0001"));
        var sameDraft = transactions.execute(status ->
                service.createDraft(Long.parseLong(conversation.id()), "hello", "client-msg-0001"));
        assertEquals(draft.id(), sameDraft.id());
        assertEquals("1", transactions.execute(status -> service.send(Long.parseLong(draft.id()))).seq());
        assertEquals("1", transactions.execute(status -> service.createDraft(
                Long.parseLong(conversation.id()), "hello", "client-msg-0001")).seq());
        assertEquals(0, service.unreadCount());
        TestUserContext.setUser(0, USER_TWO, "comm-two", List.of());
        assertEquals(1, service.unreadCount());
        TestUserContext.setUser(0, USER_ONE, "comm-one", List.of());

        BusinessException conflict = assertThrows(BusinessException.class, () -> transactions.execute(status ->
                service.createDraft(Long.parseLong(conversation.id()), "different", "client-msg-0001")));
        assertEquals("COMMUNICATION_IDEMPOTENCY_CONFLICT", conflict.getCode());
    }

    @Test
    void concurrentOppositeDirectCreationAndSendingStayUnique() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier createBarrier = new CyclicBarrier(2);
            var first = pool.submit(() -> createDirectAfterBarrier(USER_ONE, USER_TWO, createBarrier));
            var second = pool.submit(() -> createDirectAfterBarrier(USER_TWO, USER_ONE, createBarrier));
            String firstId = first.get(10, TimeUnit.SECONDS);
            String secondId = second.get(10, TimeUnit.SECONDS);
            assertEquals(firstId, secondId);
            long conversationId = Long.parseLong(firstId);
            assertEquals(1L, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM communication_conversation WHERE tenant_id=0 AND id=?", Long.class,
                    conversationId));
            assertEquals(2L, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM communication_member WHERE tenant_id=0 AND conversation_id=?", Long.class,
                    conversationId));

            TestUserContext.setUser(0, USER_ONE, "comm-one", List.of());
            long firstDraft = Long.parseLong(transactions.execute(status -> service.createDraft(
                    conversationId, "one", "client-concurrent-one")).id());
            TestUserContext.setUser(0, USER_TWO, "comm-two", List.of());
            long secondDraft = Long.parseLong(transactions.execute(status -> service.createDraft(
                    conversationId, "two", "client-concurrent-two")).id());
            CyclicBarrier sendBarrier = new CyclicBarrier(2);
            var sendOne = pool.submit(() -> sendAfterBarrier(USER_ONE, firstDraft, sendBarrier));
            var sendTwo = pool.submit(() -> sendAfterBarrier(USER_TWO, secondDraft, sendBarrier));

            assertEquals(Set.of("1", "2"), Set.of(
                    sendOne.get(10, TimeUnit.SECONDS), sendTwo.get(10, TimeUnit.SECONDS)));
            assertEquals(List.of(1L, 2L), jdbc.queryForList("""
                    SELECT seq FROM communication_message
                    WHERE tenant_id=0 AND conversation_id=? AND status='SENT' ORDER BY seq
                    """, Long.class, conversationId));
        } finally {
            pool.shutdownNow();
            TestUserContext.setUser(0, USER_ONE, "comm-one", List.of());
        }
    }

    @Test
    void nonMemberCannotReadConversationEvenInSameTenant() {
        var conversation = transactions.execute(status ->
                service.createConversation("DIRECT", null, List.of(USER_TWO)));
        TestUserContext.setUser(0, OUTSIDER, "comm-out", List.of("ADMIN"));

        BusinessException denied = assertThrows(BusinessException.class,
                () -> service.messages(Long.parseLong(conversation.id()), 0, 20));
        assertEquals("COMMUNICATION_MEMBER_NOT_FOUND", denied.getCode());

        TestUserContext.setUser(1001, USER_ONE, "comm-one", List.of("ADMIN"));
        BusinessException crossTenant = assertThrows(BusinessException.class,
                () -> service.messages(Long.parseLong(conversation.id()), 0, 20));
        assertEquals("COMMUNICATION_MEMBER_NOT_FOUND", crossTenant.getCode());
    }

    @Test
    void groupJoinBoundaryAndRemovalStayMemberScoped() {
        TestUserContext.setAdmin(0, USER_ONE);
        var conversation = transactions.execute(status ->
                service.createConversation("GROUP", "项目群", List.of(USER_TWO)));
        var draft = transactions.execute(status -> service.createDraft(
                Long.parseLong(conversation.id()), "before join", "client-msg-0002"));
        transactions.execute(status -> service.send(Long.parseLong(draft.id())));
        transactions.execute(status -> service.addMembers(Long.parseLong(conversation.id()), List.of(OUTSIDER)));

        TestUserContext.setUser(0, OUTSIDER, "comm-out", List.of());
        assertEquals(0, service.messages(Long.parseLong(conversation.id()), 0, 20).size());

        TestUserContext.setAdmin(0, USER_ONE);
        transactions.executeWithoutResult(status -> service.removeMember(Long.parseLong(conversation.id()), USER_TWO));
        TestUserContext.setUser(0, USER_TWO, "comm-two", List.of("ADMIN"));
        BusinessException denied = assertThrows(BusinessException.class,
                () -> service.messages(Long.parseLong(conversation.id()), 0, 20));
        assertEquals("COMMUNICATION_MEMBER_NOT_FOUND", denied.getCode());
    }

    @Test
    void removedAndRejoinedMemberOnlySeesMessagesAfterLatestJoin() {
        TestUserContext.setAdmin(0, USER_ONE);
        var conversation = transactions.execute(status ->
                service.createConversation("GROUP", "项目群", List.of(USER_TWO, OUTSIDER)));
        long conversationId = Long.parseLong(conversation.id());
        sendText(conversationId, "before removal", "client-rejoin-before");
        transactions.executeWithoutResult(status -> service.removeMember(conversationId, OUTSIDER));
        sendText(conversationId, "while removed", "client-rejoin-away");
        transactions.execute(status -> service.addMembers(conversationId, List.of(OUTSIDER)));
        sendText(conversationId, "after rejoin", "client-rejoin-after");

        TestUserContext.setUser(0, OUTSIDER, "comm-out", List.of());
        var visible = service.messages(conversationId, 0, 20);
        assertEquals(1, visible.size());
        assertEquals("after rejoin", visible.getFirst().body());
    }

    @Test
    void addingAnExistingMemberDoesNotDowngradeTheirRole() {
        TestUserContext.setAdmin(0, USER_ONE);
        var conversation = transactions.execute(status ->
                service.createConversation("GROUP", "项目群", List.of(USER_TWO)));
        long conversationId = Long.parseLong(conversation.id());
        transactions.executeWithoutResult(status -> service.updateRole(conversationId, USER_TWO, "ADMIN"));

        transactions.execute(status -> service.addMembers(conversationId, List.of(USER_TWO)));

        assertEquals("ADMIN", jdbc.queryForObject("""
                SELECT role FROM communication_member
                WHERE tenant_id=0 AND conversation_id=? AND user_id=?
                """, String.class, conversationId, USER_TWO));
    }

    @Test
    void groupManagersCanListActiveMembersWithRolesAndUserStatus() {
        TestUserContext.setAdmin(0, USER_ONE);
        var conversation = transactions.execute(status ->
                service.createConversation("GROUP", "项目群", List.of(USER_TWO, OUTSIDER)));
        long conversationId = Long.parseLong(conversation.id());
        transactions.executeWithoutResult(status -> service.updateRole(conversationId, USER_TWO, "ADMIN"));
        jdbc.update("UPDATE sys_user SET status='DISABLE' WHERE id=?", OUTSIDER);

        var members = service.members(conversationId);

        assertEquals(List.of(USER_ONE, USER_TWO, OUTSIDER), members.stream()
                .map(member -> Long.parseLong(member.userId())).toList());
        assertEquals(List.of("OWNER", "ADMIN", "MEMBER"), members.stream()
                .map(CommunicationService.MemberSummary::role).toList());
        assertEquals("DISABLE", members.get(2).userStatus());

        TestUserContext.setUser(0, OUTSIDER, "comm-out", List.of());
        BusinessException denied = assertThrows(BusinessException.class, () -> service.members(conversationId));
        assertEquals("COMMUNICATION_GROUP_MANAGE_DENIED", denied.getCode());
    }

    @Test
    void closedGroupIsReadOnly() {
        TestUserContext.setAdmin(0, USER_ONE);
        var conversation = transactions.execute(status ->
                service.createConversation("GROUP", "项目群", List.of(USER_TWO)));
        long conversationId = Long.parseLong(conversation.id());
        transactions.executeWithoutResult(status -> service.close(conversationId));

        BusinessException error = assertThrows(BusinessException.class, () ->
                transactions.execute(status -> service.addMembers(conversationId, List.of(OUTSIDER))));

        assertEquals("COMMUNICATION_CLOSED", error.getCode());
    }

    @Test
    void rejectsInvalidConversationMessageAndGroupStateAtTheBoundary() {
        assertEquals("COMMUNICATION_TYPE_INVALID", assertThrows(BusinessException.class,
                () -> service.createConversation(null, null, List.of())).getCode());
        assertEquals("COMMUNICATION_TARGET_REQUIRED", assertThrows(BusinessException.class,
                () -> service.createConversation("DIRECT", null, List.of(USER_ONE))).getCode());
        assertEquals("COMMUNICATION_USER_NOT_FOUND", assertThrows(BusinessException.class,
                () -> service.createConversation("DIRECT", null, List.of(123456789L))).getCode());
        assertEquals("COMMUNICATION_GROUP_MANAGE_DENIED", assertThrows(BusinessException.class,
                () -> service.createConversation("GROUP", "group", List.of(USER_TWO))).getCode());

        TestUserContext.setAdmin(0, USER_ONE);
        assertEquals("COMMUNICATION_NAME_INVALID", assertThrows(BusinessException.class,
                () -> service.createConversation("GROUP", " ", List.of(USER_TWO))).getCode());
        assertEquals("COMMUNICATION_GROUP_TOO_LARGE", assertThrows(BusinessException.class,
                () -> service.createConversation("GROUP", "group",
                        LongStream.rangeClosed(1, 100).boxed().toList())).getCode());

        var direct = transactions.execute(status ->
                service.createConversation("DIRECT", null, List.of(USER_TWO)));
        long directId = Long.parseLong(direct.id());
        assertEquals("COMMUNICATION_GROUP_REQUIRED", assertThrows(BusinessException.class,
                () -> transactions.execute(status -> service.rename(directId, "renamed"))).getCode());
        assertEquals("COMMUNICATION_CLIENT_ID_INVALID", assertThrows(BusinessException.class,
                () -> transactions.execute(status -> service.createDraft(directId, "body", "short"))).getCode());
        assertEquals("COMMUNICATION_BODY_TOO_LONG", assertThrows(BusinessException.class,
                () -> transactions.execute(status -> service.createDraft(
                        directId, "x".repeat(4_001), "client-msg-long"))).getCode());
        assertEquals("COMMUNICATION_READ_REGRESSION", assertThrows(BusinessException.class,
                () -> service.markRead(directId, -1)).getCode());
        assertEquals("COMMUNICATION_READ_OUT_OF_RANGE", assertThrows(BusinessException.class,
                () -> service.markRead(directId, 1)).getCode());

        var draft = transactions.execute(status ->
                service.createDraft(directId, "sent", "client-msg-sent"));
        transactions.execute(status -> service.send(Long.parseLong(draft.id())));
        assertEquals("COMMUNICATION_MESSAGE_IMMUTABLE", assertThrows(BusinessException.class,
                () -> transactions.executeWithoutResult(status -> service.deleteDraft(Long.parseLong(draft.id())))).getCode());
    }

    @Test
    void groupGovernanceProtectsOwnerManagerAndMembershipTransitions() {
        TestUserContext.setAdmin(0, USER_ONE);
        var group = transactions.execute(status ->
                service.createConversation("GROUP", "project", List.of(USER_TWO)));
        long groupId = Long.parseLong(group.id());

        TestUserContext.setUser(0, USER_TWO, "comm-two", List.of());
        assertEquals("COMMUNICATION_OWNER_REQUIRED", assertThrows(BusinessException.class,
                () -> transactions.executeWithoutResult(status -> service.close(groupId))).getCode());
        assertEquals("COMMUNICATION_OWNER_REQUIRED", assertThrows(BusinessException.class,
                () -> transactions.executeWithoutResult(status -> service.transferOwner(groupId, USER_ONE))).getCode());
        assertEquals("COMMUNICATION_OWNER_REQUIRED", assertThrows(BusinessException.class,
                () -> transactions.executeWithoutResult(status -> service.updateRole(groupId, USER_ONE, "ADMIN"))).getCode());
        assertEquals("COMMUNICATION_GROUP_MANAGE_DENIED", assertThrows(BusinessException.class,
                () -> transactions.execute(status -> service.addMembers(groupId, List.of(OUTSIDER)))).getCode());

        TestUserContext.setAdmin(0, USER_ONE);
        assertEquals("COMMUNICATION_ROLE_INVALID", assertThrows(BusinessException.class,
                () -> transactions.executeWithoutResult(status -> service.updateRole(groupId, USER_TWO, "OWNER"))).getCode());
        assertEquals("COMMUNICATION_OWNER_PROTECTED", assertThrows(BusinessException.class,
                () -> transactions.executeWithoutResult(status -> service.updateRole(groupId, USER_ONE, "ADMIN"))).getCode());
        assertEquals("COMMUNICATION_MEMBER_NOT_FOUND", assertThrows(BusinessException.class,
                () -> transactions.executeWithoutResult(status -> service.transferOwner(groupId, OUTSIDER))).getCode());
        assertEquals("COMMUNICATION_OWNER_TRANSFER_REQUIRED", assertThrows(BusinessException.class,
                () -> transactions.executeWithoutResult(status -> service.leave(groupId))).getCode());
        assertEquals("COMMUNICATION_NAME_INVALID", assertThrows(BusinessException.class,
                () -> transactions.execute(status -> service.rename(groupId, "x".repeat(101)))).getCode());

        transactions.executeWithoutResult(status -> service.transferOwner(groupId, USER_TWO));
        transactions.executeWithoutResult(status -> service.leave(groupId));
        TestUserContext.setUser(0, USER_TWO, "comm-two", List.of());
        transactions.executeWithoutResult(status -> service.close(groupId));
    }

    @Test
    void attachmentLimitsUnsafeFilesAndDraftExpiryDoNotPublishMessages() {
        var conversation = transactions.execute(status ->
                service.createConversation("DIRECT", null, List.of(USER_TWO)));
        long conversationId = Long.parseLong(conversation.id());
        long limitedDraft = Long.parseLong(transactions.execute(status -> service.createDraft(
                conversationId, "six files", "client-attachment-limit")).id());
        for (int index = 0; index < 6; index++) insertAttachment(880000L + index, limitedDraft, "CLEAN");

        assertEquals("COMMUNICATION_ATTACHMENT_LIMIT", assertThrows(BusinessException.class,
                () -> transactions.execute(status -> service.send(limitedDraft))).getCode());
        jdbc.update("UPDATE sys_file SET deleted_flag=1 WHERE id=880005");
        jdbc.update("UPDATE sys_file SET virus_scan_status='PENDING' WHERE id=880000");
        assertEquals("COMMUNICATION_ATTACHMENT_UNSAFE", assertThrows(BusinessException.class,
                () -> transactions.execute(status -> service.send(limitedDraft))).getCode());
        assertEquals("DRAFT", jdbc.queryForObject(
                "SELECT status FROM communication_message WHERE id=?", String.class, limitedDraft));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT last_message_seq FROM communication_conversation WHERE id=?", Long.class, conversationId));
        verify(events, never()).publish(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any());

        jdbc.update("UPDATE sys_file SET virus_scan_status='CLEAN' WHERE id=880000");
        assertEquals("1", transactions.execute(status -> service.send(limitedDraft)).seq());

        long oldDraft = Long.parseLong(transactions.execute(status -> service.createDraft(
                conversationId, "old", "client-expired-draft")).id());
        long freshDraft = Long.parseLong(transactions.execute(status -> service.createDraft(
                conversationId, "fresh", "client-fresh-draft")).id());
        insertAttachment(881001L, oldDraft, "CLEAN");
        insertAttachment(881002L, freshDraft, "CLEAN");
        jdbc.update("UPDATE communication_message SET created_at=DATEADD('HOUR',-25,CURRENT_TIMESTAMP) WHERE id=?", oldDraft);

        service.expireDrafts();

        verify(files).deleteForBusinessCascade(881001L, "COMMUNICATION_MESSAGE", oldDraft);
        verify(files, never()).deleteForBusinessCascade(881002L, "COMMUNICATION_MESSAGE", freshDraft);
        assertEquals(1, jdbc.queryForObject(
                "SELECT deleted_flag FROM communication_message WHERE id=?", Integer.class, oldDraft));
        assertEquals(0, jdbc.queryForObject(
                "SELECT deleted_flag FROM communication_message WHERE id=?", Integer.class, freshDraft));
    }

    @Test
    void messageAttachmentsStayOrderedAndUseThreeQueriesForAnyPageSize() {
        var conversation = transactions.execute(status ->
                service.createConversation("DIRECT", null, List.of(USER_TWO)));
        long conversationId = Long.parseLong(conversation.id());
        List<Object[]> messages = new ArrayList<>(100);
        for (int index = 1; index <= 100; index++) {
            messages.add(new Object[]{890_000L + index, conversationId, USER_ONE, index,
                    "message-" + index, "bulk-message-" + index});
        }
        jdbc.batchUpdate("""
                INSERT INTO communication_message(
                    id,tenant_id,conversation_id,sender_id,status,seq,body,client_message_id,
                    created_at,updated_at,deleted_flag)
                VALUES(?,0,?,?,'SENT',?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)
                """, messages);
        insertAttachment(892_002L, 890_001L, "CLEAN");
        insertAttachment(892_001L, 890_001L, "CLEAN");
        jdbc.update("UPDATE sys_file SET created_at=TIMESTAMP '2026-01-01 00:00:00' WHERE id IN (892001,892002)");

        jdbc.resetQueryCount();
        var one = service.messages(conversationId, 0, 1);
        assertEquals(3, jdbc.queryCount());
        assertEquals(List.of("892001", "892002"), one.getFirst().attachments().stream()
                .map(CommunicationService.AttachmentRecord::id).toList());

        jdbc.resetQueryCount();
        var fifty = service.messages(conversationId, 0, 50);
        assertEquals(3, jdbc.queryCount());
        assertEquals(50, fifty.size());
        assertEquals(List.of(), fifty.get(1).attachments());

        jdbc.resetQueryCount();
        assertEquals(100, service.messages(conversationId, 0, 100).size());
        assertEquals(3, jdbc.queryCount());
    }

    @Test
    void backwardCursorReturnsBoundedAscendingWindowsWithConstantQueryCount() {
        var conversation = transactions.execute(status ->
                service.createConversation("DIRECT", null, List.of(USER_TWO)));
        long conversationId = Long.parseLong(conversation.id());
        List<Object[]> messages = new ArrayList<>(10_000);
        for (int index = 1; index <= 10_000; index++) {
            messages.add(new Object[]{910_000L + index, conversationId, USER_ONE, index,
                    "history-" + index, "history-client-" + index});
        }
        jdbc.batchUpdate("""
                INSERT INTO communication_message(
                    id,tenant_id,conversation_id,sender_id,status,seq,body,client_message_id,
                    created_at,updated_at,deleted_flag)
                VALUES(?,0,?,?,'SENT',?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)
                """, messages);

        jdbc.resetQueryCount();
        var latest = service.messagesBefore(conversationId, 0, 1_000);
        assertEquals(3, jdbc.queryCount());
        assertEquals(100, latest.size());
        assertEquals("9901", latest.getFirst().seq());
        assertEquals("10000", latest.getLast().seq());

        jdbc.resetQueryCount();
        var previous = service.messagesBefore(conversationId, 9901, 100);
        assertEquals(3, jdbc.queryCount());
        assertEquals("9801", previous.getFirst().seq());
        assertEquals("9900", previous.getLast().seq());

        jdbc.update("""
                UPDATE communication_member SET join_seq=9900,last_read_seq=9900
                WHERE tenant_id=0 AND conversation_id=? AND user_id=?
                """, conversationId, USER_TWO);
        TestUserContext.setUser(0, USER_TWO, "comm-two", List.of());
        jdbc.resetQueryCount();
        var joined = service.messagesBefore(conversationId, 0, 100);
        assertEquals(3, jdbc.queryCount());
        assertEquals(100, joined.size());
        assertEquals("9901", joined.getFirst().seq());

        BusinessException invalid = assertThrows(BusinessException.class,
                () -> service.messagesBefore(conversationId, -1, 100));
        assertEquals("COMMUNICATION_CURSOR_INVALID", invalid.getCode());
    }

    @Test
    void draftCleanupSkipsExpectedRacesButPropagatesUnknownFailures() {
        var conversation = transactions.execute(status ->
                service.createConversation("DIRECT", null, List.of(USER_TWO)));
        long conversationId = Long.parseLong(conversation.id());
        long missingDraft = Long.parseLong(transactions.execute(status -> service.createDraft(
                conversationId, "missing", "client-race-missing")).id());
        long sentDraft = Long.parseLong(transactions.execute(status -> service.createDraft(
                conversationId, "sent", "client-race-sent")).id());
        long survivingDraft = Long.parseLong(transactions.execute(status -> service.createDraft(
                conversationId, "surviving", "client-race-surviving")).id());
        jdbc.update("UPDATE communication_message SET created_at=DATEADD('HOUR',-25,CURRENT_TIMESTAMP) WHERE id IN (?,?,?)",
                missingDraft, sentDraft, survivingDraft);

        @SuppressWarnings("unchecked")
        ObjectProvider<CommunicationService> proxyProvider = mock(ObjectProvider.class);
        CommunicationService cleanup = new CommunicationService(jdbc, files, events, proxyProvider);
        CommunicationService proxy = mock(CommunicationService.class);
        when(proxyProvider.getObject()).thenReturn(proxy);
        doAnswer(invocation -> {
            long messageId = invocation.getArgument(0);
            if (messageId == missingDraft) jdbc.update(
                    "UPDATE communication_message SET deleted_flag=1 WHERE id=?", messageId);
            if (messageId == sentDraft) jdbc.update(
                    "UPDATE communication_message SET status='SENT',seq=1 WHERE id=?", messageId);
            cleanup.deleteDraft(messageId);
            return null;
        }).when(proxy).deleteDraft(org.mockito.ArgumentMatchers.anyLong());

        cleanup.expireDrafts();

        assertEquals(1, jdbc.queryForObject(
                "SELECT deleted_flag FROM communication_message WHERE id=?", Integer.class, survivingDraft));

        long failedDraft = Long.parseLong(transactions.execute(status -> service.createDraft(
                conversationId, "failed", "client-race-failed")).id());
        insertAttachment(893_001L, failedDraft, "CLEAN");
        jdbc.update("UPDATE communication_message SET created_at=DATEADD('HOUR',-25,CURRENT_TIMESTAMP) WHERE id=?",
                failedDraft);
        doThrow(new IllegalStateException("object storage unavailable")).when(files)
                .deleteForBusinessCascade(893_001L, "COMMUNICATION_MESSAGE", failedDraft);

        assertThrows(IllegalStateException.class, service::expireDrafts);
        assertEquals(0, jdbc.queryForObject(
                "SELECT deleted_flag FROM communication_message WHERE id=?", Integer.class, failedDraft));
    }

    private String createDirectAfterBarrier(long currentUser, long targetUser, CyclicBarrier barrier) throws Exception {
        TestUserContext.setUser(0, currentUser, "concurrent", List.of());
        try {
            barrier.await(5, TimeUnit.SECONDS);
            return transactions.execute(status ->
                    service.createConversation("DIRECT", null, List.of(targetUser))).id();
        } finally {
            TestUserContext.clear();
        }
    }

    private String sendAfterBarrier(long currentUser, long draftId, CyclicBarrier barrier) throws Exception {
        TestUserContext.setUser(0, currentUser, "concurrent", List.of());
        try {
            barrier.await(5, TimeUnit.SECONDS);
            return transactions.execute(status -> service.send(draftId)).seq();
        } finally {
            TestUserContext.clear();
        }
    }

    private void sendText(long conversationId, String body, String clientId) {
        var draft = transactions.execute(status -> service.createDraft(conversationId, body, clientId));
        transactions.execute(status -> service.send(Long.parseLong(draft.id())));
    }

    private void insertAttachment(long fileId, long messageId, String scanStatus) {
        jdbc.update("""
                INSERT INTO sys_file(id,tenant_id,business_type,document_type,business_id,file_name,original_name,
                    file_size,content_type,storage_path,bucket_name,virus_scan_status,virus_scanned_at,
                    created_by,created_at,updated_by,updated_at,deleted_flag)
                VALUES(?,0,'COMMUNICATION_MESSAGE','CHAT_ATTACHMENT',?,'file.pdf','file.pdf',100,
                    'application/pdf',?,'test',?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,0)
                """, fileId, messageId, "COMMUNICATION_MESSAGE/" + messageId + "/" + fileId, scanStatus,
                USER_ONE, USER_ONE);
    }

    private static final class CountingJdbcTemplate extends JdbcTemplate {
        private int queryCount;

        private CountingJdbcTemplate(DataSource dataSource) {
            super(dataSource);
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            queryCount++;
            return super.queryForObject(sql, requiredType, args);
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            queryCount++;
            return super.queryForList(sql, args);
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            queryCount++;
            return super.query(sql, rowMapper, args);
        }

        private int queryCount() {
            return queryCount;
        }

        private void resetQueryCount() {
            queryCount = 0;
        }
    }
}
