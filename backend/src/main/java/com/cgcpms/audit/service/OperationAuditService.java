package com.cgcpms.audit.service;

import com.cgcpms.audit.entity.OperationAuditLog;
import com.cgcpms.audit.event.OperationAuditEvent;
import com.cgcpms.audit.mapper.OperationAuditLogMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 持久化操作审计事件。
 * Mapper 异常被捕获并记录日志，绝不抛回业务线程。
 */
@Service
public class OperationAuditService {

    private static final Logger log = LoggerFactory.getLogger(OperationAuditService.class);

    private final OperationAuditLogMapper mapper;
    private final Counter attempts;
    private final Counter successes;
    private final Counter failures;

    public OperationAuditService(OperationAuditLogMapper mapper, MeterRegistry meterRegistry) {
        this.mapper = mapper;
        this.attempts = auditCounter(meterRegistry, "attempt");
        this.successes = auditCounter(meterRegistry, "success");
        this.failures = auditCounter(meterRegistry, "failure");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @EventListener
    public void handleAuditEvent(OperationAuditEvent event) {
        attempts.increment();
        try {
            OperationAuditLog entity = new OperationAuditLog();
            entity.setTenantId(event.tenantId());
            entity.setUserId(event.userId());
            entity.setOperationType(event.operationType());
            entity.setBusinessType(event.businessType());
            entity.setBusinessId(event.businessId());
            entity.setFileId(event.fileId());
            entity.setHttpMethod(event.httpMethod());
            entity.setRequestPath(event.requestPath());
            entity.setSuccessFlag(event.successFlag() ? 1 : 0);
            entity.setErrorCode(event.errorCode());
            entity.setSourceIp(event.sourceIp());
            entity.setDurationMs(event.durationMs());
            entity.setCreatedAt(event.createdAt());
            mapper.insert(entity);
            successes.increment();
        } catch (Exception e) {
            failures.increment();
            log.error("Failed to persist audit log: operationType={}, userId={}", event.operationType(), event.userId(), e);
        }
    }

    private Counter auditCounter(MeterRegistry meterRegistry, String outcome) {
        return Counter.builder("operation.audit.persistence")
                .description("Best-effort operation audit persistence outcomes")
                .tag("outcome", outcome)
                .register(meterRegistry);
    }
}
