package com.cgcpms.document.service;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.document.mapper.DocumentGenerationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentGenerationReconciliationService {
    private final DocumentGenerationMapper mapper;
    private final DocumentGenerationPersistenceService persistenceService;

    public ReconciliationResult reconcileCurrentTenant(int staleMinutes) {
        if (staleMinutes < 5 || staleMinutes > 1440) {
            throw new BusinessException("DOCUMENT_RECONCILIATION_WINDOW_INVALID", "对账超时窗口必须为5到1440分钟");
        }
        Long tenantId = UserContext.getCurrentTenantId();
        if (tenantId == null) throw new BusinessException("AUTH_CONTEXT_MISSING", "缺少租户上下文");
        List<Long> staleIds = mapper.selectStaleIds(tenantId, LocalDateTime.now().minusMinutes(staleMinutes));
        staleIds.forEach(id -> persistenceService.fail(id, tenantId, "DOCUMENT_GENERATION_STALE"));
        List<Long> brokenSuccessIds = mapper.selectBrokenSuccessfulIds(tenantId);
        List<Long> orphanFileIds = mapper.selectOrphanGeneratedFileIds(tenantId);
        if (!brokenSuccessIds.isEmpty() || !orphanFileIds.isEmpty()) {
            log.error("Document generation reconciliation found broken references: tenantId={}, brokenSuccessCount={}, orphanFileCount={}",
                    tenantId, brokenSuccessIds.size(), orphanFileIds.size());
        }
        return new ReconciliationResult(staleIds, brokenSuccessIds, orphanFileIds);
    }

    public record ReconciliationResult(List<Long> staleFailedIds, List<Long> brokenSuccessfulIds,
                                       List<Long> orphanGeneratedFileIds) {
        public ReconciliationResult {
            staleFailedIds = List.copyOf(staleFailedIds);
            brokenSuccessfulIds = List.copyOf(brokenSuccessfulIds);
            orphanGeneratedFileIds = List.copyOf(orphanGeneratedFileIds);
        }
    }
}
