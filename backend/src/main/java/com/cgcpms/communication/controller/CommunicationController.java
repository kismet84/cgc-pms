package com.cgcpms.communication.controller;

import com.cgcpms.audit.annotation.AuditedOperation;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.result.ApiResponse;
import com.cgcpms.communication.service.CommunicationEventService;
import com.cgcpms.communication.service.CommunicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/communications")
@RequiredArgsConstructor
@ConditionalOnProperty(name = {"minio.enabled", "communication.enabled"}, havingValue = "true", matchIfMissing = true)
public class CommunicationController {

    private static final String VIEW = "hasAuthority('communication:view') or hasAnyRole('ADMIN','SUPER_ADMIN')";
    private static final String SEND = "hasAuthority('communication:send') or hasAnyRole('ADMIN','SUPER_ADMIN')";

    private final CommunicationService service;
    private final CommunicationEventService eventService;

    @GetMapping("/users")
    @PreAuthorize(SEND)
    public ApiResponse<List<CommunicationService.CommunicationUserSummary>> users(
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(service.users(keyword));
    }

    @GetMapping("/conversations")
    @PreAuthorize(VIEW)
    public ApiResponse<List<CommunicationService.ConversationSummary>> conversations() {
        return ApiResponse.success(service.conversations());
    }

    @GetMapping("/conversations/{id}/members")
    @PreAuthorize(VIEW)
    public ApiResponse<List<CommunicationService.MemberSummary>> members(@PathVariable long id) {
        return ApiResponse.success(service.members(id));
    }

    @PostMapping("/conversations")
    @PreAuthorize(SEND)
    @AuditedOperation(type = "CREATE", businessType = "COMMUNICATION_CONVERSATION")
    public ApiResponse<CommunicationService.ConversationSummary> create(@RequestBody ConversationRequest request) {
        return ApiResponse.success(service.createConversation(request.type(), request.name(), request.memberIds()));
    }

    @PatchMapping("/conversations/{id}")
    @PreAuthorize(VIEW)
    @AuditedOperation(type = "UPDATE", businessType = "COMMUNICATION_CONVERSATION", businessIdExpression = "#id")
    public ApiResponse<CommunicationService.ConversationSummary> rename(
            @PathVariable long id, @RequestBody RenameRequest request) {
        return ApiResponse.success(service.rename(id, request.name()));
    }

    @PostMapping("/conversations/{id}/members")
    @PreAuthorize(VIEW)
    @AuditedOperation(type = "ADD_MEMBER", businessType = "COMMUNICATION_CONVERSATION", businessIdExpression = "#id")
    public ApiResponse<CommunicationService.ConversationSummary> addMembers(
            @PathVariable long id, @RequestBody MembersRequest request) {
        return ApiResponse.success(service.addMembers(id, request.userIds()));
    }

    @DeleteMapping("/conversations/{id}/members/{userId}")
    @PreAuthorize(VIEW)
    @AuditedOperation(type = "REMOVE_MEMBER", businessType = "COMMUNICATION_CONVERSATION", businessIdExpression = "#id")
    public ApiResponse<Void> removeMember(@PathVariable long id, @PathVariable long userId) {
        service.removeMember(id, userId);
        return ApiResponse.success();
    }

    @PutMapping("/conversations/{id}/members/{userId}/role")
    @PreAuthorize(VIEW)
    @AuditedOperation(type = "UPDATE_MEMBER_ROLE", businessType = "COMMUNICATION_CONVERSATION", businessIdExpression = "#id")
    public ApiResponse<Void> updateRole(@PathVariable long id, @PathVariable long userId,
                                        @RequestBody RoleRequest request) {
        service.updateRole(id, userId, request.role());
        return ApiResponse.success();
    }

    @PutMapping("/conversations/{id}/owner")
    @PreAuthorize(VIEW)
    @AuditedOperation(type = "TRANSFER_OWNER", businessType = "COMMUNICATION_CONVERSATION", businessIdExpression = "#id")
    public ApiResponse<Void> transferOwner(@PathVariable long id, @RequestBody OwnerRequest request) {
        service.transferOwner(id, request.userId());
        return ApiResponse.success();
    }

    @PostMapping("/conversations/{id}/leave")
    @PreAuthorize(VIEW)
    @AuditedOperation(type = "LEAVE", businessType = "COMMUNICATION_CONVERSATION", businessIdExpression = "#id")
    public ApiResponse<Void> leave(@PathVariable long id) {
        service.leave(id);
        return ApiResponse.success();
    }

    @PostMapping("/conversations/{id}/close")
    @PreAuthorize(VIEW)
    @AuditedOperation(type = "CLOSE", businessType = "COMMUNICATION_CONVERSATION", businessIdExpression = "#id")
    public ApiResponse<Void> close(@PathVariable long id) {
        service.close(id);
        return ApiResponse.success();
    }

    @GetMapping("/conversations/{id}/messages")
    @PreAuthorize(VIEW)
    public ApiResponse<List<CommunicationService.MessageRecord>> messages(
            @PathVariable long id,
            @RequestParam(defaultValue = "0") long afterSeq,
            @RequestParam(defaultValue = "50") int pageSize) {
        return ApiResponse.success(service.messages(id, afterSeq, pageSize));
    }

    @PostMapping("/conversations/{id}/drafts")
    @PreAuthorize(SEND)
    public ApiResponse<CommunicationService.MessageRecord> createDraft(
            @PathVariable long id, @RequestBody DraftRequest request) {
        return ApiResponse.success(service.createDraft(id, request.body(), request.clientMessageId()));
    }

    @PostMapping("/messages/{id}/send")
    @PreAuthorize(SEND)
    @AuditedOperation(type = "SEND", businessType = "COMMUNICATION_MESSAGE", businessIdExpression = "#id")
    public ApiResponse<CommunicationService.MessageRecord> send(@PathVariable long id) {
        return ApiResponse.success(service.send(id));
    }

    @DeleteMapping("/messages/{id}")
    @PreAuthorize(SEND)
    public ApiResponse<Void> deleteDraft(@PathVariable long id) {
        service.deleteDraft(id);
        return ApiResponse.success();
    }

    @PutMapping("/conversations/{id}/read")
    @PreAuthorize(VIEW)
    public ApiResponse<Void> markRead(@PathVariable long id, @RequestBody ReadRequest request) {
        service.markRead(id, request.seq());
        return ApiResponse.success();
    }

    @GetMapping("/unread-count")
    @PreAuthorize(VIEW)
    public ApiResponse<Map<String, Long>> unreadCount() {
        return ApiResponse.success(Map.of("count", service.unreadCount()));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize(VIEW)
    public SseEmitter stream(@RequestParam(required = false) String clientId) {
        return eventService.subscribe(UserContext.getCurrentTenantId(), UserContext.getCurrentUserId(), clientId);
    }

    public record ConversationRequest(String type, String name, List<Long> memberIds) {}
    public record RenameRequest(String name) {}
    public record MembersRequest(List<Long> userIds) {}
    public record RoleRequest(String role) {}
    public record OwnerRequest(long userId) {}
    public record DraftRequest(String body, String clientMessageId) {}
    public record ReadRequest(long seq) {}
}
