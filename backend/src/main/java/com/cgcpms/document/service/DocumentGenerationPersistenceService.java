package com.cgcpms.document.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.document.entity.DocumentGeneration;
import com.cgcpms.document.mapper.DocumentGenerationMapper;
import com.cgcpms.document.render.RenderedDocument;
import com.cgcpms.file.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DocumentGenerationPersistenceService {
    private final DocumentGenerationMapper generationMapper;
    private final ObjectProvider<FileService> fileServiceProvider;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void start(DocumentGeneration generation) {
        generationMapper.insert(generation);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markRendering(Long id, Long tenantId) {
        int changed = generationMapper.update(null, new LambdaUpdateWrapper<DocumentGeneration>()
                .eq(DocumentGeneration::getId, id)
                .eq(DocumentGeneration::getTenantId, tenantId)
                .eq(DocumentGeneration::getStatus, "PENDING")
                .set(DocumentGeneration::getStatus, "RENDERING"));
        if (changed != 1) {
            throw new BusinessException("DOCUMENT_GENERATION_STATE_CONFLICT", "文档生成状态已变化");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void succeed(DocumentGeneration generation, RenderedDocument rendered) {
        FileService fileService = fileServiceProvider.getIfAvailable();
        if (fileService == null) {
            throw new BusinessException("FILE_STORAGE_UNAVAILABLE", "文件服务未启用");
        }
        FileService.GeneratedFileArchive archive = fileService.archiveGeneratedPdf(
                rendered.content(), generation.getBusinessType(), generation.getBusinessId(),
                generation.getGenerationNo(), rendered.sha256());
        int changed = generationMapper.update(null, new LambdaUpdateWrapper<DocumentGeneration>()
                .eq(DocumentGeneration::getId, generation.getId())
                .eq(DocumentGeneration::getTenantId, generation.getTenantId())
                .eq(DocumentGeneration::getStatus, "RENDERING")
                .set(DocumentGeneration::getStatus, "SUCCEEDED")
                .set(DocumentGeneration::getFileId, archive.fileId())
                .set(DocumentGeneration::getOutputSha256, archive.sha256())
                .set(DocumentGeneration::getCompletedAt, LocalDateTime.now()));
        if (changed != 1) {
            throw new BusinessException("DOCUMENT_GENERATION_STATE_CONFLICT", "文档生成状态已变化");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void fail(Long id, Long tenantId, String failureCode) {
        generationMapper.update(null, new LambdaUpdateWrapper<DocumentGeneration>()
                .eq(DocumentGeneration::getId, id)
                .eq(DocumentGeneration::getTenantId, tenantId)
                .in(DocumentGeneration::getStatus, "PENDING", "RENDERING")
                .set(DocumentGeneration::getStatus, "FAILED")
                .set(DocumentGeneration::getFailureCode, sanitizeFailureCode(failureCode))
                .set(DocumentGeneration::getCompletedAt, LocalDateTime.now()));
    }

    private String sanitizeFailureCode(String code) {
        if (code == null || !code.matches("[A-Z0-9_]{1,80}")) return "DOCUMENT_GENERATION_FAILED";
        return code;
    }
}
