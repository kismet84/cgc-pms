package com.cgcpms.document.service;

import com.cgcpms.audit.event.OperationAuditEvent;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.document.event.PurchaseOrderApprovedEvent;
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
public class PurchaseDocumentGenerationListener {
    private final DocumentGenerationService generationService;
    private final ApplicationEventPublisher events;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterRequestCommit(PurchaseRequestApprovedEvent event) {
        generate(
                "PURCHASE_REQUEST",
                event.requestId(),
                event.instanceId(),
                event.tenantId(),
                event.requestedBy(),
                "PURCHASE_REQUEST_DOCUMENT_AUTO_GENERATE",
                "采购申请审批后文档生成失败，不影响已提交审批");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterOrderCommit(PurchaseOrderApprovedEvent event) {
        generate(
                "PURCHASE_ORDER",
                event.orderId(),
                event.instanceId(),
                event.tenantId(),
                event.requestedBy(),
                "PURCHASE_ORDER_DOCUMENT_AUTO_GENERATE",
                "采购订单审批后文档生成失败，不影响已提交审批");
    }

    private void generate(
            String businessType,
            Long businessId,
            Long instanceId,
            Long tenantId,
            Long requestedBy,
            String operationType,
            String failureMessage) {
        String key = businessType + ":" + businessId + ":INSTANCE:" + instanceId;
        try {
            generationService.generateSystem(businessType, businessId, key, tenantId, requestedBy);
            audit(tenantId, requestedBy, operationType, businessType, businessId, true, null);
        } catch (RuntimeException exception) {
            String code = exception instanceof BusinessException business
                    ? business.getCode()
                    : "DOCUMENT_GENERATION_FAILED";
            log.error("{} businessId={}, instanceId={}, code={}",
                    failureMessage, businessId, instanceId, code, exception);
            audit(tenantId, requestedBy, operationType, businessType, businessId, false, code);
        }
    }

    private void audit(
            Long tenantId,
            Long requestedBy,
            String operationType,
            String businessType,
            Long businessId,
            boolean success,
            String errorCode) {
        events.publishEvent(OperationAuditEvent.builder()
                .tenantId(tenantId)
                .userId(requestedBy)
                .operationType(operationType)
                .businessType(businessType)
                .businessId(String.valueOf(businessId))
                .httpMethod("SYSTEM")
                .requestPath("AFTER_COMMIT")
                .successFlag(success)
                .errorCode(errorCode)
                .durationMs(0)
                .createdAt(LocalDateTime.now())
                .build());
    }
}
