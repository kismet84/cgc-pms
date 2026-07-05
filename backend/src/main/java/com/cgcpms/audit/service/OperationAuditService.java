package com.cgcpms.audit.service;

import com.cgcpms.audit.entity.OperationAuditLog;
import com.cgcpms.audit.event.OperationAuditEvent;
import com.cgcpms.audit.mapper.OperationAuditLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 异步持久化操作审计事件。
 * Mapper 异常被捕获并记录日志，绝不抛回业务线程。
 */
@Service
public class OperationAuditService {

    private static final Logger log = LoggerFactory.getLogger(OperationAuditService.class);

    private final OperationAuditLogMapper mapper;

    public OperationAuditService(OperationAuditLogMapper mapper) {
        this.mapper = mapper;
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @EventListener
    public void handleAuditEvent(OperationAuditEvent event) {
        try {
            OperationAuditLog entity = new OperationAuditLog();
            entity.setTenantId(event.tenantId());
            entity.setUserId(event.userId());
            entity.setOperationType(event.operationType());
            entity.setBusinessType(event.businessType());
            entity.setBusinessId(event.businessId());
            entity.setHttpMethod(event.httpMethod());
            entity.setRequestPath(event.requestPath());
            entity.setSuccessFlag(event.successFlag() ? 1 : 0);
            entity.setErrorCode(event.errorCode());
            entity.setSourceIp(event.sourceIp());
            entity.setDurationMs(event.durationMs());
            entity.setCreatedAt(event.createdAt());
            mapper.insert(entity);
        } catch (Exception e) {
            log.error("Failed to persist audit log: operationType={}, userId={}", event.operationType(), event.userId(), e);
        }
    }
}
