package com.cgcpms.communication.service;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.file.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = {"minio.enabled", "communication.enabled"}, havingValue = "true", matchIfMissing = true)
public class CommunicationService {

    private static final int MAX_GROUP_MEMBERS = 100;
    private static final int MAX_ATTACHMENTS = 5;
    private static final int MAX_BODY_LENGTH = 4_000;

    private final JdbcTemplate jdbcTemplate;
    private final FileService fileService;
    private final CommunicationEventService eventService;
    private final ObjectProvider<CommunicationService> selfProvider;

    public List<CommunicationUserSummary> users(String keyword) {
        long tenantId = tenantId();
        long userId = userId();
        String term = keyword == null ? "" : keyword.trim().toLowerCase();
        return jdbcTemplate.query("""
                SELECT id,username,real_name,avatar
                FROM sys_user
                WHERE tenant_id=? AND id<>? AND status='ENABLE' AND deleted_flag=0
                  AND (?='' OR LOWER(CONCAT(username,' ',COALESCE(real_name,''))) LIKE ?)
                ORDER BY COALESCE(real_name,username),id
                LIMIT 50
                """, (rs, ignored) -> new CommunicationUserSummary(
                id(rs.getLong("id")), rs.getString("username"), rs.getString("real_name"), rs.getString("avatar")),
                tenantId, userId, term, "%" + term + "%");
    }

    public List<ConversationSummary> conversations() {
        return jdbcTemplate.query("""
                SELECT c.id,c.type,c.name,c.owner_user_id,c.last_message_seq,c.last_message_at,c.status,
                       m.role,m.last_read_seq,m.join_seq,
                       CASE WHEN c.type='DIRECT' THEN (
                           SELECT COALESCE(u.real_name,u.username)
                           FROM communication_member other_m
                           JOIN sys_user u ON u.tenant_id=other_m.tenant_id AND u.id=other_m.user_id
                           WHERE other_m.tenant_id=c.tenant_id AND other_m.conversation_id=c.id
                             AND other_m.user_id<>m.user_id AND other_m.deleted_flag=0
                           ORDER BY other_m.user_id LIMIT 1
                       ) ELSE c.name END AS display_name,
                       (SELECT COUNT(*) FROM communication_message msg
                        WHERE msg.tenant_id=c.tenant_id AND msg.conversation_id=c.id
                          AND msg.status='SENT' AND msg.deleted_flag=0
                          AND msg.seq>CASE WHEN m.last_read_seq>m.join_seq THEN m.last_read_seq ELSE m.join_seq END)
                          AS unread_count
                FROM communication_member m
                JOIN communication_conversation c ON c.tenant_id=m.tenant_id AND c.id=m.conversation_id
                WHERE m.tenant_id=? AND m.user_id=? AND m.status='ACTIVE' AND m.deleted_flag=0
                  AND c.deleted_flag=0
                ORDER BY CASE WHEN c.last_message_at IS NULL THEN 1 ELSE 0 END,c.last_message_at DESC,c.created_at DESC
                """, (rs, ignored) -> new ConversationSummary(
                id(rs.getLong("id")), rs.getString("type"), rs.getString("display_name"),
                nullableId(rs, "owner_user_id"), id(rs.getLong("last_message_seq")),
                rs.getObject("last_message_at", LocalDateTime.class), rs.getString("status"),
                rs.getString("role"), rs.getLong("unread_count")), tenantId(), userId());
    }

    public List<MemberSummary> members(long conversationId) {
        ConversationAccess access = requireConversation(conversationId, false);
        requireGroup(access);
        requireActiveConversation(access);
        requireManager(access);
        return jdbcTemplate.query("""
                SELECT m.user_id,u.username,u.real_name,u.avatar,m.role,u.status AS user_status
                FROM communication_member m
                LEFT JOIN sys_user u ON u.tenant_id=m.tenant_id AND u.id=m.user_id
                WHERE m.tenant_id=? AND m.conversation_id=?
                  AND m.status='ACTIVE' AND m.deleted_flag=0
                ORDER BY m.user_id
                """, (rs, ignored) -> new MemberSummary(id(rs.getLong("user_id")), rs.getString("username"),
                rs.getString("real_name"), rs.getString("avatar"), rs.getString("role"),
                rs.getString("user_status")), tenantId(), conversationId);
    }

    @Transactional
    public ConversationSummary createConversation(String type, String name, List<Long> memberIds) {
        String normalizedType = type == null ? "" : type.trim().toUpperCase();
        return switch (normalizedType) {
            case "DIRECT" -> createDirect(memberIds);
            case "GROUP" -> createGroup(name, memberIds);
            default -> throw new BusinessException("COMMUNICATION_TYPE_INVALID", "会话类型无效");
        };
    }

    private ConversationSummary createDirect(List<Long> memberIds) {
        long currentUserId = userId();
        Set<Long> targets = uniqueMembers(memberIds);
        targets.remove(currentUserId);
        if (targets.size() != 1) {
            throw new BusinessException("COMMUNICATION_TARGET_REQUIRED", "私聊必须且只能选择一名通讯对象");
        }
        long targetUserId = targets.iterator().next();
        requireEnabledUsers(Set.of(targetUserId));
        long low = Math.min(currentUserId, targetUserId);
        long high = Math.max(currentUserId, targetUserId);
        String pair = low + ":" + high;
        List<Long> existing = jdbcTemplate.queryForList("""
                SELECT id FROM communication_conversation
                WHERE tenant_id=? AND direct_pair_key=? AND deleted_flag=0
                """, Long.class, tenantId(), pair);
        if (!existing.isEmpty()) return summary(existing.getFirst());

        long conversationId = IdWorker.getId();
        try {
            jdbcTemplate.update("""
                    INSERT INTO communication_conversation(
                        id,tenant_id,type,direct_pair_key,status,created_by,updated_by,created_at,updated_at)
                    VALUES(?,?,'DIRECT',?,'ACTIVE',?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                    """, conversationId, tenantId(), pair, currentUserId, currentUserId);
            insertMember(conversationId, currentUserId, "MEMBER", 0);
            insertMember(conversationId, targetUserId, "MEMBER", 0);
        } catch (DuplicateKeyException duplicate) {
            conversationId = jdbcTemplate.queryForObject("""
                    SELECT id FROM communication_conversation
                    WHERE tenant_id=? AND direct_pair_key=? AND deleted_flag=0
                    FOR UPDATE
                    """, Long.class, tenantId(), pair);
        }
        return summary(conversationId);
    }

    private ConversationSummary createGroup(String name, List<Long> memberIds) {
        requireAuthority("communication:group:manage");
        String groupName = requireName(name);
        long ownerId = userId();
        Set<Long> members = uniqueMembers(memberIds);
        members.add(ownerId);
        if (members.size() > MAX_GROUP_MEMBERS) {
            throw new BusinessException("COMMUNICATION_GROUP_TOO_LARGE", "群成员数量超过上限");
        }
        requireEnabledUsers(members);
        long conversationId = IdWorker.getId();
        jdbcTemplate.update("""
                INSERT INTO communication_conversation(
                    id,tenant_id,type,name,owner_user_id,status,created_by,updated_by,created_at,updated_at)
                VALUES(?,?,'GROUP',?,?,'ACTIVE',?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, conversationId, tenantId(), groupName, ownerId, ownerId, ownerId);
        for (long memberId : members) insertMember(conversationId, memberId,
                memberId == ownerId ? "OWNER" : "MEMBER", 0);
        return summary(conversationId);
    }

    @Transactional
    public ConversationSummary rename(long conversationId, String name) {
        ConversationAccess access = requireConversation(conversationId, true);
        requireGroup(access);
        requireActiveConversation(access);
        requireManager(access);
        jdbcTemplate.update("""
                UPDATE communication_conversation SET name=?,updated_by=?,updated_at=CURRENT_TIMESTAMP
                WHERE tenant_id=? AND id=? AND status='ACTIVE' AND deleted_flag=0
                """, requireName(name), userId(), tenantId(), conversationId);
        return summary(conversationId);
    }

    @Transactional
    public ConversationSummary addMembers(long conversationId, List<Long> memberIds) {
        ConversationAccess access = requireConversation(conversationId, true);
        requireGroup(access);
        requireActiveConversation(access);
        requireManager(access);
        Set<Long> members = uniqueMembers(memberIds);
        requireEnabledUsers(members);
        int current = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM communication_member
                WHERE tenant_id=? AND conversation_id=? AND status='ACTIVE' AND deleted_flag=0
                """, Integer.class, tenantId(), conversationId);
        long additions = members.stream().filter(memberId -> !isActiveMember(conversationId, memberId)).count();
        if (current + additions > MAX_GROUP_MEMBERS) {
            throw new BusinessException("COMMUNICATION_GROUP_TOO_LARGE", "群成员数量超过上限");
        }
        for (long memberId : members) {
            if (isActiveMember(conversationId, memberId)) continue;
            int updated = jdbcTemplate.update("""
                    UPDATE communication_member
                    SET role='MEMBER',join_seq=?,leave_seq=NULL,last_read_seq=?,status='ACTIVE',
                        updated_by=?,updated_at=CURRENT_TIMESTAMP,deleted_flag=0
                    WHERE tenant_id=? AND conversation_id=? AND user_id=? AND status='LEFT'
                    """, access.lastMessageSeq(), access.lastMessageSeq(), userId(), tenantId(), conversationId, memberId);
            if (updated == 0) insertMember(conversationId, memberId, "MEMBER", access.lastMessageSeq());
        }
        return summary(conversationId);
    }

    @Transactional
    public void removeMember(long conversationId, long targetUserId) {
        ConversationAccess requester = requireConversation(conversationId, true);
        requireGroup(requester);
        requireActiveConversation(requester);
        requireManager(requester);
        MemberAccess target = requireMember(conversationId, targetUserId, true);
        if ("OWNER".equals(target.role()) || ("ADMIN".equals(target.role()) && !"OWNER".equals(requester.role()))) {
            throw new BusinessException("COMMUNICATION_MEMBER_ROLE_DENIED", "无权移除该成员");
        }
        leaveMember(conversationId, targetUserId, requester.lastMessageSeq());
    }

    @Transactional
    public void updateRole(long conversationId, long targetUserId, String role) {
        ConversationAccess requester = requireConversation(conversationId, true);
        requireGroup(requester);
        requireActiveConversation(requester);
        if (!"OWNER".equals(requester.role())) {
            throw new BusinessException("COMMUNICATION_OWNER_REQUIRED", "仅群主可调整管理员");
        }
        String normalized = role == null ? "" : role.trim().toUpperCase();
        if (!Set.of("ADMIN", "MEMBER").contains(normalized)) {
            throw new BusinessException("COMMUNICATION_ROLE_INVALID", "群角色无效");
        }
        MemberAccess target = requireMember(conversationId, targetUserId, true);
        if ("OWNER".equals(target.role())) throw new BusinessException("COMMUNICATION_OWNER_PROTECTED", "不能直接修改群主角色");
        jdbcTemplate.update("""
                UPDATE communication_member SET role=?,updated_by=?,updated_at=CURRENT_TIMESTAMP
                WHERE tenant_id=? AND conversation_id=? AND user_id=? AND status='ACTIVE' AND deleted_flag=0
                """, normalized, userId(), tenantId(), conversationId, targetUserId);
    }

    @Transactional
    public void transferOwner(long conversationId, long targetUserId) {
        ConversationAccess requester = requireConversation(conversationId, true);
        requireGroup(requester);
        requireActiveConversation(requester);
        if (!"OWNER".equals(requester.role())) throw new BusinessException("COMMUNICATION_OWNER_REQUIRED", "仅群主可转让群聊");
        requireMember(conversationId, targetUserId, true);
        if (targetUserId == userId()) return;
        jdbcTemplate.update("""
                UPDATE communication_member SET role='MEMBER',updated_by=?,updated_at=CURRENT_TIMESTAMP
                WHERE tenant_id=? AND conversation_id=? AND user_id=?
                """, userId(), tenantId(), conversationId, userId());
        jdbcTemplate.update("""
                UPDATE communication_member SET role='OWNER',updated_by=?,updated_at=CURRENT_TIMESTAMP
                WHERE tenant_id=? AND conversation_id=? AND user_id=? AND status='ACTIVE'
                """, userId(), tenantId(), conversationId, targetUserId);
        jdbcTemplate.update("""
                UPDATE communication_conversation SET owner_user_id=?,updated_by=?,updated_at=CURRENT_TIMESTAMP
                WHERE tenant_id=? AND id=?
                """, targetUserId, userId(), tenantId(), conversationId);
    }

    @Transactional
    public void leave(long conversationId) {
        ConversationAccess access = requireConversation(conversationId, true);
        requireGroup(access);
        requireActiveConversation(access);
        if ("OWNER".equals(access.role())) {
            int otherMembers = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM communication_member
                    WHERE tenant_id=? AND conversation_id=? AND user_id<>? AND status='ACTIVE' AND deleted_flag=0
                    """, Integer.class, tenantId(), conversationId, userId());
            if (otherMembers > 0) throw new BusinessException("COMMUNICATION_OWNER_TRANSFER_REQUIRED", "请先转让群主");
            close(conversationId);
        }
        leaveMember(conversationId, userId(), access.lastMessageSeq());
    }

    @Transactional
    public void close(long conversationId) {
        ConversationAccess access = requireConversation(conversationId, true);
        requireGroup(access);
        if (!"OWNER".equals(access.role())) throw new BusinessException("COMMUNICATION_OWNER_REQUIRED", "仅群主可关闭群聊");
        jdbcTemplate.update("""
                UPDATE communication_conversation SET status='CLOSED',updated_by=?,updated_at=CURRENT_TIMESTAMP
                WHERE tenant_id=? AND id=? AND status='ACTIVE'
                """, userId(), tenantId(), conversationId);
    }

    public List<MessageRecord> messages(long conversationId, long afterSeq, int pageSize) {
        MemberAccess member = requireMember(conversationId, userId(), true);
        int limit = Math.min(100, Math.max(1, pageSize));
        long lowerBound = Math.max(Math.max(0, afterSeq), member.joinSeq());
        List<MessageRecord> messages = jdbcTemplate.query("""
                SELECT msg.id,msg.conversation_id,msg.sender_id,msg.seq,msg.body,msg.created_at,
                       COALESCE(u.real_name,u.username) AS sender_name
                FROM communication_message msg
                JOIN sys_user u ON u.tenant_id=msg.tenant_id AND u.id=msg.sender_id
                WHERE msg.tenant_id=? AND msg.conversation_id=? AND msg.status='SENT' AND msg.deleted_flag=0
                  AND msg.seq>? AND (? IS NULL OR msg.seq<=?)
                ORDER BY msg.seq
                LIMIT ?
                """, (rs, ignored) -> new MessageRecord(
                id(rs.getLong("id")), id(rs.getLong("conversation_id")), id(rs.getLong("sender_id")),
                id(rs.getLong("seq")), rs.getString("body"), rs.getString("sender_name"),
                rs.getObject("created_at", LocalDateTime.class), List.of()),
                tenantId(), conversationId, lowerBound, member.leaveSeq(), member.leaveSeq(), limit);
        return attachFiles(messages);
    }

    @Transactional
    public MessageRecord createDraft(long conversationId, String body, String clientMessageId) {
        ConversationAccess access = requireConversation(conversationId, true);
        if (!"ACTIVE".equals(access.status())) throw new BusinessException("COMMUNICATION_CLOSED", "会话已关闭");
        String normalizedBody = normalizeBody(body);
        String clientId = requireClientMessageId(clientMessageId);
        MessageRecord existing = findIdempotentMessage(conversationId, normalizedBody, clientId);
        if (existing != null) return existing;
        long messageId = IdWorker.getId();
        try {
            jdbcTemplate.update("""
                    INSERT INTO communication_message(
                        id,tenant_id,conversation_id,sender_id,status,body,client_message_id,created_at,updated_at)
                    VALUES(?,?,?,?,'DRAFT',?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                    """, messageId, tenantId(), conversationId, userId(), normalizedBody, clientId);
        } catch (DuplicateKeyException exception) {
            MessageRecord concurrent = findIdempotentMessage(conversationId, normalizedBody, clientId);
            if (concurrent != null) return concurrent;
            throw exception;
        }
        return draftRecord(messageId);
    }

    private MessageRecord findIdempotentMessage(long conversationId, String normalizedBody, String clientId) {
        List<Map<String, Object>> existing = jdbcTemplate.queryForList("""
                SELECT id,conversation_id,status,seq,body FROM communication_message
                WHERE tenant_id=? AND sender_id=? AND client_message_id=? AND deleted_flag=0
                """, tenantId(), userId(), clientId);
        if (existing.isEmpty()) return null;
        Map<String, Object> row = existing.getFirst();
        if (((Number) row.get("conversation_id")).longValue() != conversationId
                || !Objects.equals(row.get("body"), normalizedBody)) {
            throw new BusinessException("COMMUNICATION_IDEMPOTENCY_CONFLICT", "幂等键已用于不同消息");
        }
        long existingId = ((Number) row.get("id")).longValue();
        if ("SENT".equals(row.get("status"))) {
            long existingSeq = ((Number) row.get("seq")).longValue();
            return messages(conversationId, existingSeq - 1, 1).getFirst();
        }
        return draftRecord(existingId);
    }

    @Transactional
    public MessageRecord send(long messageId) {
        Map<String, Object> message = requireDraftForUpdate(messageId);
        long conversationId = ((Number) message.get("conversation_id")).longValue();
        if ("SENT".equals(message.get("status"))) {
            long existingSeq = ((Number) message.get("seq")).longValue();
            return messages(conversationId, existingSeq - 1, 1).getFirst();
        }
        ConversationAccess access = requireConversation(conversationId, true);
        if (!"ACTIVE".equals(access.status())) throw new BusinessException("COMMUNICATION_CLOSED", "会话已关闭");
        int attachmentCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sys_file
                WHERE tenant_id=? AND business_type='COMMUNICATION_MESSAGE' AND business_id=? AND deleted_flag=0
                """, Integer.class, tenantId(), messageId);
        int unsafeAttachments = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sys_file
                WHERE tenant_id=? AND business_type='COMMUNICATION_MESSAGE' AND business_id=?
                  AND deleted_flag=0 AND virus_scan_status<>'CLEAN'
                """, Integer.class, tenantId(), messageId);
        if (attachmentCount > MAX_ATTACHMENTS) throw new BusinessException("COMMUNICATION_ATTACHMENT_LIMIT", "消息附件不能超过5个");
        if (unsafeAttachments > 0) throw new BusinessException("COMMUNICATION_ATTACHMENT_UNSAFE", "附件尚未通过安全检查");
        if ((message.get("body") == null || String.valueOf(message.get("body")).isBlank()) && attachmentCount == 0) {
            throw new BusinessException("COMMUNICATION_MESSAGE_EMPTY", "消息内容不能为空");
        }
        long seq = access.lastMessageSeq() + 1;
        jdbcTemplate.update("""
                UPDATE communication_message SET status='SENT',seq=?,updated_at=CURRENT_TIMESTAMP
                WHERE tenant_id=? AND id=? AND status='DRAFT' AND sender_id=?
                """, seq, tenantId(), messageId, userId());
        jdbcTemplate.update("""
                UPDATE communication_conversation
                SET last_message_seq=?,last_message_at=CURRENT_TIMESTAMP,updated_by=?,updated_at=CURRENT_TIMESTAMP
                WHERE tenant_id=? AND id=?
                """, seq, userId(), tenantId(), conversationId);
        jdbcTemplate.update("""
                UPDATE communication_member SET last_read_seq=?,updated_by=?,updated_at=CURRENT_TIMESTAMP
                WHERE tenant_id=? AND conversation_id=? AND user_id=? AND status='ACTIVE'
                """, seq, userId(), tenantId(), conversationId, userId());
        publishAfterCommit(conversationId, messageId, seq);
        return messages(conversationId, seq - 1, 1).getFirst();
    }

    @Transactional
    public void deleteDraft(long messageId) {
        Map<String, Object> draft = requireDraftForUpdate(messageId);
        if (!"DRAFT".equals(draft.get("status"))) {
            throw new BusinessException("COMMUNICATION_MESSAGE_IMMUTABLE", "已发送消息不可删除");
        }
        List<Long> fileIds = jdbcTemplate.queryForList("""
                SELECT id FROM sys_file
                WHERE tenant_id=? AND business_type='COMMUNICATION_MESSAGE' AND business_id=? AND deleted_flag=0
                ORDER BY id
                """, Long.class, tenantId(), messageId);
        fileIds.forEach(fileId -> fileService.deleteForBusinessCascade(fileId, "COMMUNICATION_MESSAGE", messageId));
        jdbcTemplate.update("""
                UPDATE communication_message SET deleted_flag=1,updated_at=CURRENT_TIMESTAMP
                WHERE tenant_id=? AND id=? AND status='DRAFT' AND sender_id=?
                """, tenantId(), messageId, userId());
    }

    @Transactional
    public void markRead(long conversationId, long seq) {
        ConversationAccess access = requireConversation(conversationId, true);
        if (seq < access.lastReadSeq()) throw new BusinessException("COMMUNICATION_READ_REGRESSION", "已读位置不能回退");
        if (seq > access.lastMessageSeq()) throw new BusinessException("COMMUNICATION_READ_OUT_OF_RANGE", "已读位置超出消息范围");
        jdbcTemplate.update("""
                UPDATE communication_member SET last_read_seq=?,updated_by=?,updated_at=CURRENT_TIMESTAMP
                WHERE tenant_id=? AND conversation_id=? AND user_id=? AND status='ACTIVE'
                """, seq, userId(), tenantId(), conversationId, userId());
    }

    public long unreadCount() {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN c.last_message_seq>
                    CASE WHEN m.last_read_seq>m.join_seq THEN m.last_read_seq ELSE m.join_seq END
                    THEN c.last_message_seq-CASE WHEN m.last_read_seq>m.join_seq THEN m.last_read_seq ELSE m.join_seq END
                    ELSE 0 END),0)
                FROM communication_member m
                JOIN communication_conversation c ON c.tenant_id=m.tenant_id AND c.id=m.conversation_id
                WHERE m.tenant_id=? AND m.user_id=? AND m.status='ACTIVE' AND m.deleted_flag=0 AND c.deleted_flag=0
                """, Long.class, tenantId(), userId());
        return count == null ? 0 : count;
    }

    @Scheduled(fixedDelayString = "${communication.draft-cleanup-delay-ms:3600000}") // SQL-SAFETY: fixed-sql-fragment Spring property placeholder, not SQL
    public void expireDrafts() {
        List<Map<String, Object>> expired = jdbcTemplate.queryForList("""
                SELECT id,tenant_id,sender_id FROM communication_message
                WHERE status='DRAFT' AND deleted_flag=0 AND created_at<?
                ORDER BY created_at,id LIMIT 100
                """, LocalDateTime.now().minusHours(24));
        UserContext.Snapshot previous = UserContext.capture();
        int deleted = 0;
        int missing = 0;
        int sent = 0;
        try {
            for (Map<String, Object> row : expired) {
                long tenantId = ((Number) row.get("tenant_id")).longValue();
                long senderId = ((Number) row.get("sender_id")).longValue();
                UserContext.restore(new UserContext.Snapshot(senderId, null, tenantId, List.of()));
                long messageId = ((Number) row.get("id")).longValue();
                try {
                    selfProvider.getObject().deleteDraft(messageId);
                    deleted++;
                } catch (BusinessException race) {
                    if ("COMMUNICATION_MESSAGE_NOT_FOUND".equals(race.getCode())) {
                        missing++;
                    } else if ("COMMUNICATION_MESSAGE_IMMUTABLE".equals(race.getCode())) {
                        sent++;
                    } else {
                        log.error("communication_draft_cleanup failed expired={} deleted={} missing={} sent={} failed=1 messageId={}",
                                expired.size(), deleted, missing, sent, messageId, race);
                        throw race;
                    }
                } catch (RuntimeException failure) {
                    log.error("communication_draft_cleanup failed expired={} deleted={} missing={} sent={} failed=1 messageId={}",
                            expired.size(), deleted, missing, sent, messageId, failure);
                    throw failure;
                }
            }
            log.info("communication_draft_cleanup completed expired={} deleted={} missing={} sent={} failed=0",
                    expired.size(), deleted, missing, sent);
        } finally {
            UserContext.restore(previous);
        }
    }

    private void publishAfterCommit(long conversationId, long messageId, long seq) {
        List<Long> recipients = jdbcTemplate.queryForList("""
                SELECT m.user_id FROM communication_member m
                JOIN sys_user u ON u.tenant_id=m.tenant_id AND u.id=m.user_id
                WHERE m.tenant_id=? AND m.conversation_id=? AND m.status='ACTIVE' AND m.deleted_flag=0
                  AND u.status='ENABLE' AND u.deleted_flag=0
                """, Long.class, tenantId(), conversationId);
        long tenantId = tenantId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                eventService.publish(tenantId, recipients, new CommunicationEventService.CommunicationEvent(
                        "MESSAGE", id(conversationId), id(messageId), id(seq)));
            }
        });
    }

    private ConversationSummary summary(long conversationId) {
        List<ConversationSummary> rows = jdbcTemplate.query("""
                SELECT c.id,c.type,c.name,c.owner_user_id,c.last_message_seq,c.last_message_at,c.status,
                       m.role,m.last_read_seq,m.join_seq,
                       CASE WHEN c.type='DIRECT' THEN (
                           SELECT COALESCE(u.real_name,u.username)
                           FROM communication_member other_m
                           JOIN sys_user u ON u.tenant_id=other_m.tenant_id AND u.id=other_m.user_id
                           WHERE other_m.tenant_id=c.tenant_id AND other_m.conversation_id=c.id
                             AND other_m.user_id<>m.user_id AND other_m.deleted_flag=0
                           ORDER BY other_m.user_id LIMIT 1
                       ) ELSE c.name END AS display_name,
                       (SELECT COUNT(*) FROM communication_message msg
                        WHERE msg.tenant_id=c.tenant_id AND msg.conversation_id=c.id
                          AND msg.status='SENT' AND msg.deleted_flag=0
                          AND msg.seq>CASE WHEN m.last_read_seq>m.join_seq THEN m.last_read_seq ELSE m.join_seq END)
                          AS unread_count
                FROM communication_member m
                JOIN communication_conversation c ON c.tenant_id=m.tenant_id AND c.id=m.conversation_id
                WHERE m.tenant_id=? AND m.user_id=? AND m.status='ACTIVE' AND m.deleted_flag=0
                  AND c.id=? AND c.deleted_flag=0
                LIMIT 1 FOR UPDATE
                """, (rs, ignored) -> new ConversationSummary(
                id(rs.getLong("id")), rs.getString("type"), rs.getString("display_name"),
                nullableId(rs, "owner_user_id"), id(rs.getLong("last_message_seq")),
                rs.getObject("last_message_at", LocalDateTime.class), rs.getString("status"),
                rs.getString("role"), rs.getLong("unread_count")), tenantId(), userId(), conversationId);
        if (rows.isEmpty()) throw new BusinessException("COMMUNICATION_NOT_FOUND", "会话不存在");
        return rows.getFirst();
    }

    private ConversationAccess requireConversation(long conversationId, boolean lock) {
        String sql = """
                SELECT c.id,c.type,c.status,c.owner_user_id,c.last_message_seq,
                       m.role,m.join_seq,m.leave_seq,m.last_read_seq,m.status AS member_status
                FROM communication_conversation c
                JOIN communication_member m ON m.tenant_id=c.tenant_id AND m.conversation_id=c.id
                WHERE c.tenant_id=? AND c.id=? AND c.deleted_flag=0
                  AND m.user_id=? AND m.status='ACTIVE' AND m.deleted_flag=0
                """ + (lock ? " FOR UPDATE" : "");
        List<ConversationAccess> rows = jdbcTemplate.query(sql, (rs, ignored) -> new ConversationAccess(
                rs.getLong("id"), rs.getString("type"), rs.getString("status"),
                nullableLong(rs, "owner_user_id"), rs.getLong("last_message_seq"), rs.getString("role"),
                rs.getLong("join_seq"), nullableLong(rs, "leave_seq"), rs.getLong("last_read_seq")),
                tenantId(), conversationId, userId());
        if (rows.size() != 1) throw new BusinessException("COMMUNICATION_NOT_FOUND", "会话不存在");
        return rows.getFirst();
    }

    private MemberAccess requireMember(long conversationId, long targetUserId, boolean activeOnly) {
        List<MemberAccess> rows = jdbcTemplate.query("""
                SELECT role,join_seq,leave_seq,status FROM communication_member
                WHERE tenant_id=? AND conversation_id=? AND user_id=? AND deleted_flag=0
                """, (rs, ignored) -> new MemberAccess(rs.getString("role"), rs.getLong("join_seq"),
                nullableLong(rs, "leave_seq"), rs.getString("status")), tenantId(), conversationId, targetUserId);
        if (rows.size() != 1 || (activeOnly && !"ACTIVE".equals(rows.getFirst().status()))) {
            throw new BusinessException("COMMUNICATION_MEMBER_NOT_FOUND", "会话成员不存在");
        }
        return rows.getFirst();
    }

    private boolean isActiveMember(long conversationId, long targetUserId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM communication_member
                WHERE tenant_id=? AND conversation_id=? AND user_id=?
                  AND status='ACTIVE' AND deleted_flag=0
                """, Integer.class, tenantId(), conversationId, targetUserId);
        return count != null && count > 0;
    }

    private Map<String, Object> requireDraftForUpdate(long messageId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id,conversation_id,sender_id,status,seq,body FROM communication_message
                WHERE tenant_id=? AND id=? AND sender_id=? AND deleted_flag=0 FOR UPDATE
                """, tenantId(), messageId, userId());
        if (rows.size() != 1) throw new BusinessException("COMMUNICATION_MESSAGE_NOT_FOUND", "消息不存在");
        if ("SENT".equals(rows.getFirst().get("status"))) return rows.getFirst();
        if (!"DRAFT".equals(rows.getFirst().get("status"))) {
            throw new BusinessException("COMMUNICATION_MESSAGE_IMMUTABLE", "消息不可修改");
        }
        return rows.getFirst();
    }

    private MessageRecord draftRecord(long messageId) {
        return jdbcTemplate.queryForObject("""
                SELECT id,conversation_id,sender_id,body,created_at FROM communication_message
                WHERE tenant_id=? AND id=? AND sender_id=? AND status='DRAFT' AND deleted_flag=0
                """, (rs, ignored) -> new MessageRecord(id(rs.getLong("id")), id(rs.getLong("conversation_id")),
                id(rs.getLong("sender_id")), null, rs.getString("body"), UserContext.getCurrentUsername(),
                rs.getObject("created_at", LocalDateTime.class), attachments(messageId)), tenantId(), messageId, userId());
    }

    private List<MessageRecord> attachFiles(List<MessageRecord> messages) {
        if (messages.isEmpty()) return messages;
        List<Long> messageIds = messages.stream().map(message -> Long.parseLong(message.id())).toList();
        String placeholders = String.join(",", java.util.Collections.nCopies(messageIds.size(), "?"));
        List<Object> args = new ArrayList<>(messageIds.size() + 1);
        args.add(tenantId());
        args.addAll(messageIds);
        List<MessageAttachment> rows = jdbcTemplate.query("""
                SELECT business_id,id,original_name,file_size,content_type,virus_scan_status FROM sys_file
                WHERE tenant_id=? AND business_type='COMMUNICATION_MESSAGE' AND business_id IN (%s)
                  AND deleted_flag=0
                ORDER BY business_id,created_at,id
                """.formatted(placeholders), (rs, ignored) -> new MessageAttachment(
                rs.getLong("business_id"), new AttachmentRecord(id(rs.getLong("id")),
                rs.getString("original_name"), rs.getLong("file_size"), rs.getString("content_type"),
                rs.getString("virus_scan_status"))), args.toArray());
        Map<Long, List<AttachmentRecord>> byMessage = new HashMap<>();
        for (MessageAttachment row : rows) {
            byMessage.computeIfAbsent(row.messageId(), ignored -> new ArrayList<>()).add(row.attachment());
        }
        return messages.stream().map(message -> new MessageRecord(message.id(), message.conversationId(),
                message.senderId(), message.seq(), message.body(), message.senderName(), message.createdAt(),
                byMessage.getOrDefault(Long.parseLong(message.id()), List.of()))).toList();
    }

    private List<AttachmentRecord> attachments(long messageId) {
        return jdbcTemplate.query("""
                SELECT id,original_name,file_size,content_type,virus_scan_status FROM sys_file
                WHERE tenant_id=? AND business_type='COMMUNICATION_MESSAGE' AND business_id=? AND deleted_flag=0
                ORDER BY created_at,id
                """, (rs, ignored) -> new AttachmentRecord(id(rs.getLong("id")), rs.getString("original_name"),
                rs.getLong("file_size"), rs.getString("content_type"), rs.getString("virus_scan_status")),
                tenantId(), messageId);
    }

    private record MessageAttachment(long messageId, AttachmentRecord attachment) {}

    private void insertMember(long conversationId, long memberId, String role, long joinSeq) {
        jdbcTemplate.update("""
                INSERT INTO communication_member(
                    id,tenant_id,conversation_id,user_id,role,join_seq,last_read_seq,status,
                    created_by,updated_by,created_at,updated_at)
                VALUES(?,?,?,?,?,?,?,'ACTIVE',?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, IdWorker.getId(), tenantId(), conversationId, memberId, role, joinSeq, joinSeq, userId(), userId());
    }

    private void leaveMember(long conversationId, long memberId, long leaveSeq) {
        jdbcTemplate.update("""
                UPDATE communication_member
                SET status='LEFT',leave_seq=?,updated_by=?,updated_at=CURRENT_TIMESTAMP
                WHERE tenant_id=? AND conversation_id=? AND user_id=? AND status='ACTIVE'
                """, leaveSeq, userId(), tenantId(), conversationId, memberId);
    }

    private Set<Long> uniqueMembers(List<Long> memberIds) {
        Set<Long> members = new LinkedHashSet<>();
        if (memberIds != null) memberIds.stream().filter(Objects::nonNull).forEach(members::add);
        return members;
    }

    private void requireEnabledUsers(Set<Long> userIds) {
        if (userIds.isEmpty()) return;
        String placeholders = String.join(",", java.util.Collections.nCopies(userIds.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(tenantId());
        args.addAll(userIds);
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_user WHERE tenant_id=? AND id IN ("
                + placeholders + ") AND status='ENABLE' AND deleted_flag=0", Integer.class, args.toArray());
        if (count == null || count != userIds.size()) {
            throw new BusinessException("COMMUNICATION_USER_NOT_FOUND", "通讯用户不存在或已停用");
        }
    }

    private void requireGroup(ConversationAccess access) {
        if (!"GROUP".equals(access.type())) throw new BusinessException("COMMUNICATION_GROUP_REQUIRED", "该操作仅适用于群聊");
    }

    private void requireActiveConversation(ConversationAccess access) {
        if (!"ACTIVE".equals(access.status())) {
            throw new BusinessException("COMMUNICATION_CLOSED", "会话已关闭");
        }
    }

    private void requireManager(ConversationAccess access) {
        if (!Set.of("OWNER", "ADMIN").contains(access.role())) {
            throw new BusinessException("COMMUNICATION_GROUP_MANAGE_DENIED", "无权管理群聊");
        }
    }

    private void requireAuthority(String authority) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean allowed = UserContext.hasAnyRole("ADMIN", "SUPER_ADMIN") || (authentication != null
                && authentication.getAuthorities().stream().anyMatch(item -> authority.equals(item.getAuthority())));
        if (!allowed) throw new BusinessException("COMMUNICATION_GROUP_MANAGE_DENIED", "无权创建群聊");
    }

    private String requireName(String name) {
        String value = name == null ? "" : name.trim();
        if (value.isEmpty() || value.length() > 100) throw new BusinessException("COMMUNICATION_NAME_INVALID", "群聊名称长度无效");
        return value;
    }

    private String normalizeBody(String body) {
        if (body == null) return null;
        String value = body.strip();
        if (value.length() > MAX_BODY_LENGTH) throw new BusinessException("COMMUNICATION_BODY_TOO_LONG", "消息内容超过长度上限");
        return value.isEmpty() ? null : value;
    }

    private String requireClientMessageId(String value) {
        String clientId = value == null ? "" : value.trim();
        if (!clientId.matches("[A-Za-z0-9_-]{8,64}")) {
            throw new BusinessException("COMMUNICATION_CLIENT_ID_INVALID", "客户端消息ID格式无效");
        }
        return clientId;
    }

    private long tenantId() {
        Long value = UserContext.getCurrentTenantId();
        if (value == null) throw new BusinessException("AUTH_CONTEXT_MISSING", "缺少租户上下文");
        return value;
    }

    private long userId() {
        Long value = UserContext.getCurrentUserId();
        if (value == null) throw new BusinessException("AUTH_CONTEXT_MISSING", "缺少用户上下文");
        return value;
    }

    private static String id(long value) { return String.valueOf(value); }
    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
    private static String nullableId(ResultSet rs, String column) throws SQLException {
        Long value = nullableLong(rs, column);
        return value == null ? null : id(value);
    }

    private record ConversationAccess(long id, String type, String status, Long ownerId, long lastMessageSeq,
                                      String role, long joinSeq, Long leaveSeq, long lastReadSeq) {}
    private record MemberAccess(String role, long joinSeq, Long leaveSeq, String status) {}

    public record CommunicationUserSummary(String id, String username, String realName, String avatar) {}
    public record MemberSummary(String userId, String username, String realName, String avatar,
                                String role, String userStatus) {}
    public record ConversationSummary(String id, String type, String name, String ownerUserId,
                                      String lastMessageSeq, LocalDateTime lastMessageAt,
                                      String status, String role, long unreadCount) {}
    public record MessageRecord(String id, String conversationId, String senderId, String seq, String body,
                                String senderName, LocalDateTime createdAt, List<AttachmentRecord> attachments) {}
    public record AttachmentRecord(String id, String originalName, long fileSize, String contentType,
                                   String virusScanStatus) {}
}
