package com.cgcpms.audit.event;

import java.time.LocalDateTime;

/**
 * 不可变操作审计事件，由切面发布、由审计服务异步消费。
 */
public record OperationAuditEvent(
        Long tenantId,
        Long userId,
        String operationType,
        String businessType,
        String businessId,
        String httpMethod,
        String requestPath,
        boolean successFlag,
        String errorCode,
        String sourceIp,
        int durationMs,
        LocalDateTime createdAt
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long tenantId;
        private Long userId;
        private String operationType;
        private String businessType;
        private String businessId;
        private String httpMethod;
        private String requestPath;
        private boolean successFlag;
        private String errorCode;
        private String sourceIp;
        private int durationMs;
        private LocalDateTime createdAt;

        public Builder tenantId(Long tenantId) { this.tenantId = tenantId; return this; }
        public Builder userId(Long userId) { this.userId = userId; return this; }
        public Builder operationType(String operationType) { this.operationType = operationType; return this; }
        public Builder businessType(String businessType) { this.businessType = businessType; return this; }
        public Builder businessId(String businessId) { this.businessId = businessId; return this; }
        public Builder httpMethod(String httpMethod) { this.httpMethod = httpMethod; return this; }
        public Builder requestPath(String requestPath) { this.requestPath = requestPath; return this; }
        public Builder successFlag(boolean successFlag) { this.successFlag = successFlag; return this; }
        public Builder errorCode(String errorCode) { this.errorCode = errorCode; return this; }
        public Builder sourceIp(String sourceIp) { this.sourceIp = sourceIp; return this; }
        public Builder durationMs(int durationMs) { this.durationMs = durationMs; return this; }
        public Builder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }

        public OperationAuditEvent build() {
            return new OperationAuditEvent(tenantId, userId, operationType, businessType, businessId,
                    httpMethod, requestPath, successFlag, errorCode, sourceIp, durationMs, createdAt);
        }
    }
}
