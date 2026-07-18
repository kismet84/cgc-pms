package com.cgcpms.document.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.document.entity.DocumentGeneration;
import com.cgcpms.document.entity.DocumentTemplateVersion;
import com.cgcpms.document.config.DocumentGenerationProperties;
import com.cgcpms.document.mapper.DocumentGenerationMapper;
import com.cgcpms.document.provider.DocumentDataProviderRegistry;
import com.cgcpms.document.provider.DocumentDataSnapshot;
import com.cgcpms.document.render.DocumentRenderer;
import com.cgcpms.document.render.RenderedDocument;
import com.cgcpms.document.render.RestrictedTemplateEngine;
import com.cgcpms.file.auth.BusinessObjectAuthorizer;
import com.cgcpms.file.service.FileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DocumentGenerationService {
    private final DocumentGenerationMapper generationMapper;
    private final DocumentTemplateService templateService;
    private final DocumentDataProviderRegistry providerRegistry;
    private final RestrictedTemplateEngine templateEngine;
    private final DocumentRenderer renderer;
    private final DocumentGenerationPersistenceService persistenceService;
    private final BusinessObjectAuthorizer businessObjectAuthorizer;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<FileService> fileServiceProvider;
    private final DocumentGenerationProperties properties;

    public DocumentGeneration generate(String businessType, Long businessId, String idempotencyKey,
                                       Long retryOfGenerationId) {
        Long tenantId = requireTenant();
        Long userId = requireUser();
        String normalizedType = normalizeBusinessType(businessType);
        requireGenerationEnabled(normalizedType);
        if (businessId == null) throw new BusinessException("DOCUMENT_BUSINESS_ID_REQUIRED", "业务ID不能为空");
        if (idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9._:-]{8,120}")) {
            throw new BusinessException("DOCUMENT_IDEMPOTENCY_KEY_INVALID", "文档生成幂等键格式非法");
        }

        DocumentGeneration existing = findByIdempotencyKey(tenantId, idempotencyKey);
        if (existing != null) return existing;
        validateRetrySource(retryOfGenerationId, tenantId, normalizedType, businessId);

        businessObjectAuthorizer.checkGeneratedDocumentAccess(normalizedType, businessId);
        DocumentDataSnapshot snapshot = providerRegistry.require(normalizedType).load(businessId);
        DocumentTemplateVersion version = templateService.requireDefaultVersion(normalizedType);
        if (!version.getSchemaVersion().equals(snapshot.schemaVersion())) {
            throw new BusinessException("DOCUMENT_SCHEMA_VERSION_MISMATCH", "模板与业务数据契约版本不一致");
        }

        String sourceDigest = sha256(canonicalJson(snapshot.values()));
        long generationId = IdWorker.getId();
        DocumentGeneration generation = new DocumentGeneration();
        generation.setId(generationId);
        generation.setTenantId(tenantId);
        generation.setGenerationNo("DOC-" + Long.toUnsignedString(generationId, 36).toUpperCase(Locale.ROOT));
        generation.setBusinessType(normalizedType);
        generation.setBusinessId(businessId);
        generation.setTemplateId(version.getTemplateId());
        generation.setTemplateVersionId(version.getId());
        generation.setSchemaVersion(version.getSchemaVersion());
        generation.setSourceDigest(sourceDigest);
        generation.setRendererId(renderer.rendererId());
        generation.setRendererVersion(renderer.rendererVersion());
        generation.setStatus("PENDING");
        generation.setIdempotencyKey(idempotencyKey);
        generation.setRetryOfGenerationId(retryOfGenerationId);
        generation.setRequestedBy(userId);
        generation.setRequestedAt(LocalDateTime.now());

        try {
            persistenceService.start(generation);
        } catch (DuplicateKeyException exception) {
            DocumentGeneration raced = findByIdempotencyKey(tenantId, idempotencyKey);
            if (raced != null) return raced;
            throw new BusinessException("DOCUMENT_GENERATION_CONFLICT", "文档生成请求冲突，请重试");
        }

        try {
            persistenceService.markRendering(generationId, tenantId);
            String html = templateEngine.render(version.getTemplateContent(), snapshot.values());
            RenderedDocument rendered = renderer.render(html);
            persistenceService.succeed(generation, rendered);
            return requireGeneration(generationId, tenantId);
        } catch (BusinessException exception) {
            persistenceService.fail(generationId, tenantId, exception.getCode());
            throw exception;
        } catch (RuntimeException exception) {
            persistenceService.fail(generationId, tenantId, "DOCUMENT_GENERATION_FAILED");
            throw new BusinessException("DOCUMENT_GENERATION_FAILED", "文档生成失败，请稍后重试", exception);
        }
    }

    public RenderedDocument preview(String businessType, Long businessId) {
        String normalizedType = normalizeBusinessType(businessType);
        requireGenerationEnabled(normalizedType);
        if (businessId == null) throw new BusinessException("DOCUMENT_BUSINESS_ID_REQUIRED", "业务ID不能为空");
        businessObjectAuthorizer.checkGeneratedDocumentAccess(normalizedType, businessId);
        DocumentDataSnapshot snapshot = providerRegistry.require(normalizedType).loadPreview(businessId);
        DocumentTemplateVersion version = templateService.requireDefaultVersion(normalizedType);
        if (!version.getSchemaVersion().equals(snapshot.schemaVersion())) {
            throw new BusinessException("DOCUMENT_SCHEMA_VERSION_MISMATCH", "模板与业务数据契约版本不一致");
        }
        String html = templateEngine.render(version.getTemplateContent(), snapshot.values());
        return renderer.render(addPreviewWatermark(html));
    }

    /**
     * 模板治理预览只渲染当前保存的草稿/发布版本，不写入生成事实或文件归档。
     * 业务对象授权仍与正式预览一致，避免模板维护权限扩大数据可见范围。
     */
    public RenderedDocument previewTemplateVersion(Long templateVersionId, Long businessId) {
        if (businessId == null) throw new BusinessException("DOCUMENT_BUSINESS_ID_REQUIRED", "业务ID不能为空");
        DocumentTemplateService.TemplateVersionContext context = templateService.requirePreviewVersionContext(templateVersionId);
        String businessType = context.template().getBusinessType();
        requireGenerationEnabled(businessType);
        businessObjectAuthorizer.checkGeneratedDocumentAccess(businessType, businessId);
        DocumentDataSnapshot snapshot = providerRegistry.require(businessType).loadPreview(businessId);
        if (!context.version().getSchemaVersion().equals(snapshot.schemaVersion())) {
            throw new BusinessException("DOCUMENT_SCHEMA_VERSION_MISMATCH", "模板与业务数据契约版本不一致");
        }
        String html = templateEngine.render(context.version().getTemplateContent(), snapshot.values());
        return renderer.render(addPreviewWatermark(html));
    }

    public DocumentGeneration requireGeneration(Long id) {
        DocumentGeneration generation = requireGeneration(id, requireTenant());
        businessObjectAuthorizer.checkGeneratedDocumentAccess(generation.getBusinessType(), generation.getBusinessId());
        return generation;
    }

    public IPage<DocumentGeneration> history(long pageNo, long pageSize, String businessType, Long businessId) {
        String normalizedType = normalizeBusinessType(businessType);
        if (businessId == null) throw new BusinessException("DOCUMENT_BUSINESS_ID_REQUIRED", "业务ID不能为空");
        businessObjectAuthorizer.checkGeneratedDocumentAccess(normalizedType, businessId);
        return generationMapper.selectPage(new Page<>(Math.max(1, pageNo), Math.min(100, Math.max(1, pageSize))),
                new LambdaQueryWrapper<DocumentGeneration>()
                        .eq(DocumentGeneration::getTenantId, requireTenant())
                        .eq(DocumentGeneration::getBusinessType, normalizedType)
                        .eq(DocumentGeneration::getBusinessId, businessId)
                        .orderByDesc(DocumentGeneration::getRequestedAt));
    }

    public String downloadUrl(Long generationId) {
        DocumentGeneration generation = requireGeneration(generationId, requireTenant());
        businessObjectAuthorizer.checkGeneratedDocumentAccess(generation.getBusinessType(), generation.getBusinessId());
        if (!"SUCCEEDED".equals(generation.getStatus()) || generation.getFileId() == null) {
            throw new BusinessException("DOCUMENT_GENERATION_NOT_DOWNLOADABLE", "文档尚未成功归档");
        }
        FileService fileService = fileServiceProvider.getIfAvailable();
        if (fileService == null) throw new BusinessException("FILE_STORAGE_UNAVAILABLE", "文件服务未启用");
        return fileService.getGeneratedDocumentPresignedUrl(generation.getFileId());
    }

    public String auditDownloadUrl(Long generationId, String reason) {
        if (reason == null || reason.trim().length() < 4 || reason.length() > 200) {
            throw new BusinessException("DOCUMENT_AUDIT_REASON_INVALID", "审计下载原因须为4到200个字符");
        }
        DocumentGeneration generation = requireGeneration(generationId, requireTenant());
        if (!"SUCCEEDED".equals(generation.getStatus()) || generation.getFileId() == null) {
            throw new BusinessException("DOCUMENT_GENERATION_NOT_DOWNLOADABLE", "文档尚未成功归档");
        }
        FileService fileService = fileServiceProvider.getIfAvailable();
        if (fileService == null) throw new BusinessException("FILE_STORAGE_UNAVAILABLE", "文件服务未启用");
        return fileService.getGeneratedDocumentAuditPresignedUrl(generation.getFileId());
    }

    private DocumentGeneration findByIdempotencyKey(Long tenantId, String key) {
        return generationMapper.selectOne(new LambdaQueryWrapper<DocumentGeneration>()
                .eq(DocumentGeneration::getTenantId, tenantId)
                .eq(DocumentGeneration::getIdempotencyKey, key));
    }

    private DocumentGeneration requireGeneration(Long id, Long tenantId) {
        DocumentGeneration generation = generationMapper.selectOne(new LambdaQueryWrapper<DocumentGeneration>()
                .eq(DocumentGeneration::getId, id)
                .eq(DocumentGeneration::getTenantId, tenantId));
        if (generation == null) throw new BusinessException("DOCUMENT_GENERATION_NOT_FOUND", "文档生成记录不存在");
        return generation;
    }

    private void validateRetrySource(Long retryId, Long tenantId, String businessType, Long businessId) {
        if (retryId == null) return;
        DocumentGeneration previous = requireGeneration(retryId, tenantId);
        if (!"FAILED".equals(previous.getStatus())
                || !businessType.equals(previous.getBusinessType())
                || !businessId.equals(previous.getBusinessId())) {
            throw new BusinessException("DOCUMENT_RETRY_SOURCE_INVALID", "仅允许重试同一业务对象的失败生成记录");
        }
    }

    private String canonicalJson(Object value) {
        try {
            return objectMapper.writeValueAsString(canonicalize(value));
        } catch (Exception exception) {
            throw new BusinessException("DOCUMENT_SOURCE_SERIALIZATION_FAILED", "业务数据无法规范化", exception);
        }
    }

    private Object canonicalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new LinkedHashMap<>();
            map.entrySet().stream()
                    .sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                    .forEach(entry -> sorted.put(String.valueOf(entry.getKey()), canonicalize(entry.getValue())));
            return sorted;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            list.forEach(item -> result.add(canonicalize(item)));
            return result;
        }
        return value;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private String normalizeBusinessType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("PAYMENT", "SETTLEMENT").contains(normalized)) {
            throw new BusinessException("DOCUMENT_BUSINESS_TYPE_INVALID", "仅支持PAYMENT或SETTLEMENT业务单据");
        }
        return normalized;
    }

    private void requireGenerationEnabled(String businessType) {
        if (!properties.isEnabled()) {
            throw new BusinessException("DOCUMENT_GENERATION_DISABLED", "业务单据生成能力尚未启用");
        }
        if ("PAYMENT".equals(businessType) && !properties.isPaymentEnabled()) {
            throw new BusinessException("DOCUMENT_PAYMENT_GENERATION_DISABLED", "付款单据生成能力尚未启用");
        }
        if ("SETTLEMENT".equals(businessType) && !properties.isSettlementEnabled()) {
            throw new BusinessException("DOCUMENT_SETTLEMENT_GENERATION_DISABLED", "结算单据生成能力尚未启用");
        }
    }

    private String addPreviewWatermark(String html) {
        String watermark = "<div style=\"position:fixed;top:42%;left:12%;width:76%;text-align:center;"
                + "font-size:42pt;color:#ead8d8;transform:rotate(-28deg);z-index:9999;\">"
                + "预览件 非正式文件</div>";
        int bodyEnd = html.toLowerCase(Locale.ROOT).lastIndexOf("</body>");
        return bodyEnd < 0 ? html + watermark : html.substring(0, bodyEnd) + watermark + html.substring(bodyEnd);
    }

    private Long requireTenant() {
        Long value = UserContext.getCurrentTenantId();
        if (value == null) throw new BusinessException("AUTH_CONTEXT_MISSING", "缺少租户上下文");
        return value;
    }

    private Long requireUser() {
        Long value = UserContext.getCurrentUserId();
        if (value == null) throw new BusinessException("AUTH_CONTEXT_MISSING", "缺少用户上下文");
        return value;
    }
}
