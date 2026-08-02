package com.cgcpms.document.service;

import com.cgcpms.audit.event.OperationAuditEvent;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.document.event.PurchaseRequestApprovedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseRequestDocumentGenerationListener {
    private final DocumentGenerationService generationService;
    private final ApplicationEventPublisher events;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(PurchaseRequestApprovedEvent event) {
        String key = "PURCHASE_REQUEST:" + event.requestId() + ":INSTANCE:" + event.instanceId();
        try {
            generationService.generateSystem("PURCHASE_REQUEST", event.requestId(), key,
                    event.tenantId(), event.requestedBy());
            audit(event, true, null);
        } catch (RuntimeException exception) {
            String code = exception instanceof BusinessException business ? business.getCode()
                    : "DOCUMENT_GENERATION_FAILED";
            log.error("采购申请审批后文档生成失败，不影响已提交审批 requestId={}, instanceId={}, code={}",
                    event.requestId(), event.instanceId(), code, exception);
            audit(event, false, code);
        }
    }

    private void audit(PurchaseRequestApprovedEvent event, boolean success, String errorCode) {
        events.publishEvent(OperationAuditEvent.builder()
                .tenantId(event.tenantId())
                .userId(event.requestedBy())
                .operationType("PURCHASE_REQUEST_DOCUMENT_AUTO_GENERATE")
                .businessType("PURCHASE_REQUEST")
                .businessId(String.valueOf(event.requestId()))
                .httpMethod("SYSTEM")
                .requestPath("AFTER_COMMIT")
                .successFlag(success)
                .errorCode(errorCode)
                .durationMs(0)
                .createdAt(LocalDateTime.now())
                .build());
    }
}
