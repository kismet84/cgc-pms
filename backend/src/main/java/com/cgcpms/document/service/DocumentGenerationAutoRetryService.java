package com.cgcpms.document.service;

import com.cgcpms.document.entity.DocumentGeneration;
import com.cgcpms.document.mapper.DocumentGenerationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Durable retry worker for approval-triggered procurement documents.
 * Failed generation facts remain the queue; child retry facts fence duplicates.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentGenerationAutoRetryService {
    private static final int MAX_ATTEMPTS = 3;

    private final DocumentGenerationMapper generationMapper;
    private final DocumentGenerationService generationService;

    @Scheduled(fixedDelayString = "${document.generation.auto-retry-delay-ms:60000}") // SQL-SAFETY: fixed-sql-fragment — Spring configuration placeholder, not SQL
    public void retryFailedProcurementDocuments() {
        for (DocumentGeneration failed : generationMapper.selectAutoRetryCandidates(LocalDateTime.now().minusMinutes(1))) {
            int attempt = attempt(failed);
            if (attempt >= MAX_ATTEMPTS || failed.getRequestedBy() == null || failed.getTenantId() == null) continue;
            String key = "AUTO_RETRY:" + failed.getId() + ":" + (attempt + 1);
            try {
                generationService.generateSystem(failed.getBusinessType(), failed.getBusinessId(), key,
                        failed.getTenantId(), failed.getRequestedBy(), failed.getId());
            } catch (RuntimeException exception) {
                log.warn("采购文档自动重试失败，保留失败事实等待下次调度 businessType={}, businessId={}, generationId={}, attempt={}",
                        failed.getBusinessType(), failed.getBusinessId(), failed.getId(), attempt + 1, exception);
            }
        }
    }

    private int attempt(DocumentGeneration generation) {
        String key = generation.getIdempotencyKey();
        if (key == null) return MAX_ATTEMPTS;
        if (key.startsWith("AUTO_RETRY:")) {
            String[] parts = key.split(":");
            if (parts.length == 3) {
                try { return Integer.parseInt(parts[2]); } catch (NumberFormatException ignored) { return MAX_ATTEMPTS; }
            }
        }
        String type = generation.getBusinessType() == null ? "" : generation.getBusinessType().toUpperCase(Locale.ROOT);
        return key.startsWith(type + ":") ? 0 : MAX_ATTEMPTS;
    }
}
