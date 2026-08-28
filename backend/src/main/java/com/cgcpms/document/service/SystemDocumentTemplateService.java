package com.cgcpms.document.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.document.entity.DocumentDefaultBinding;
import com.cgcpms.document.entity.DocumentTemplate;
import com.cgcpms.document.entity.DocumentTemplateVersion;
import com.cgcpms.document.mapper.DocumentDefaultBindingMapper;
import com.cgcpms.document.mapper.DocumentTemplateMapper;
import com.cgcpms.document.mapper.DocumentTemplateVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SystemDocumentTemplateService {
    private final SystemDocumentTemplateCatalog catalog;
    private final DocumentTemplateService templateService;
    private final DocumentTemplateMapper templateMapper;
    private final DocumentTemplateVersionMapper versionMapper;
    private final DocumentDefaultBindingMapper bindingMapper;

    public List<SystemTemplateStatus> statuses() {
        return catalog.definitions().stream().map(this::status).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public InstallResult install(String businessType) {
        SystemDocumentTemplateCatalog.ValidatedDefinition definition = catalog.validate(catalog.require(businessType));
        lockTenantCodeScope();
        return installValidated(definition);
    }

    @Transactional(rollbackFor = Exception.class)
    public DocumentTemplateVersion installVersion(String businessType) {
        InstallResult result = install(businessType);
        DocumentTemplateVersion version = versionMapper.selectOne(new LambdaQueryWrapper<DocumentTemplateVersion>()
                .eq(DocumentTemplateVersion::getTenantId, requireTenant())
                .eq(DocumentTemplateVersion::getId, result.versionId()));
        if (version == null) throw new BusinessException("DOCUMENT_TEMPLATE_VERSION_NOT_FOUND", "模板版本不存在");
        return version;
    }

    @Transactional(rollbackFor = Exception.class)
    public List<InstallResult> installAll() {
        List<SystemDocumentTemplateCatalog.ValidatedDefinition> definitions = catalog.validateAll();
        lockTenantCodeScope();
        return definitions.stream().map(this::installValidated).toList();
    }

    private InstallResult installValidated(SystemDocumentTemplateCatalog.ValidatedDefinition validated) {
        SystemDocumentTemplateCatalog.Definition definition = validated.definition();
        Long tenantId = requireTenant();
        DocumentTemplate template = templateMapper.selectOne(new LambdaQueryWrapper<DocumentTemplate>()
                .eq(DocumentTemplate::getTenantId, tenantId)
                .eq(DocumentTemplate::getTemplateCode, definition.templateCode()));
        InstallAction action;
        DocumentTemplateVersion version;
        if (template == null) {
            version = templateService.create(definition.templateCode(), definition.templateName(),
                    definition.businessType(), draft(definition, validated.designSchema()));
            version = templateService.publish(version.getId());
            template = requireTemplate(version.getTemplateId());
            action = InstallAction.CREATED;
        } else {
            requireSystemTemplateIdentity(template, definition);
            DocumentTemplateVersion latest = latestPublishedVersion(template.getId());
            if (latest != null && "PUBLISHED".equals(latest.getStatus())
                    && Objects.equals(latest.getTemplateContent(), validated.templateContent())) {
                version = latest;
                action = InstallAction.UNCHANGED;
            } else {
                version = templateService.createNextDraft(template.getId(), draft(definition, validated.designSchema()));
                version = templateService.publish(version.getId());
                action = InstallAction.UPGRADED;
            }
        }
        BindingAction bindingAction = bindSystemDefault(template, version);
        return new InstallResult(definition.businessType(), template.getId(), version.getId(), action, bindingAction);
    }

    private BindingAction bindSystemDefault(DocumentTemplate template, DocumentTemplateVersion version) {
        DocumentDefaultBinding binding = bindingMapper.selectOne(new LambdaQueryWrapper<DocumentDefaultBinding>()
                .eq(DocumentDefaultBinding::getTenantId, requireTenant())
                .eq(DocumentDefaultBinding::getBusinessType, template.getBusinessType()));
        if (binding == null) {
            templateService.bindDefault(version.getId(), 0);
            return BindingAction.BOUND;
        }
        if (!Objects.equals(binding.getTemplateId(), template.getId())) return BindingAction.PRESERVED_CUSTOM;
        if (Objects.equals(binding.getTemplateVersionId(), version.getId())) return BindingAction.UNCHANGED_SYSTEM;
        templateService.bindDefault(version.getId(), binding.getLockVersion());
        return BindingAction.UPDATED_SYSTEM;
    }

    private SystemTemplateStatus status(SystemDocumentTemplateCatalog.Definition definition) {
        Long tenantId = requireTenant();
        DocumentTemplate template = templateMapper.selectOne(new LambdaQueryWrapper<DocumentTemplate>()
                .eq(DocumentTemplate::getTenantId, tenantId)
                .eq(DocumentTemplate::getTemplateCode, definition.templateCode()));
        DocumentTemplateVersion latest = template == null ? null : latestPublishedVersion(template.getId());
        DocumentDefaultBinding binding = bindingMapper.selectOne(new LambdaQueryWrapper<DocumentDefaultBinding>()
                .eq(DocumentDefaultBinding::getTenantId, tenantId)
                .eq(DocumentDefaultBinding::getBusinessType, definition.businessType()));
        boolean current = false;
        if (latest != null && "PUBLISHED".equals(latest.getStatus())) {
            try {
                current = Objects.equals(latest.getTemplateContent(), catalog.validate(definition).templateContent());
            } catch (BusinessException ignored) {
                current = false;
            }
        }
        String bindingState = binding == null ? "UNBOUND"
                : template != null && Objects.equals(binding.getTemplateId(), template.getId()) ? "SYSTEM" : "CUSTOM";
        return new SystemTemplateStatus(definition.businessType(), definition.templateCode(), definition.templateName(),
                definition.schemaVersion(), definition.orientation(), template == null ? null : template.getId(),
                latest == null ? null : latest.getId(), template != null, current, bindingState);
    }

    private DocumentTemplateService.DraftCommand draft(SystemDocumentTemplateCatalog.Definition definition,
                                                        String designSchema) {
        return new DocumentTemplateService.DraftCommand(definition.schemaVersion(), null, null,
                "CGC-PMS 系统模板；管理员显式安装，可安全追加升级。", designSchema);
    }

    private DocumentTemplate requireTemplate(Long templateId) {
        DocumentTemplate template = templateMapper.selectOne(new LambdaQueryWrapper<DocumentTemplate>()
                .eq(DocumentTemplate::getTenantId, requireTenant()).eq(DocumentTemplate::getId, templateId));
        if (template == null) throw new BusinessException("DOCUMENT_TEMPLATE_NOT_FOUND", "模板不存在");
        return template;
    }

    private DocumentTemplateVersion latestPublishedVersion(Long templateId) {
        return versionMapper.selectOne(new LambdaQueryWrapper<DocumentTemplateVersion>()
                .eq(DocumentTemplateVersion::getTenantId, requireTenant())
                .eq(DocumentTemplateVersion::getTemplateId, templateId)
                .eq(DocumentTemplateVersion::getStatus, "PUBLISHED")
                .orderByDesc(DocumentTemplateVersion::getVersionNo)
                .last("LIMIT 1")); // SQL-SAFETY: fixed-sql-fragment — fixed row limit, no user input
    }

    private void requireSystemTemplateIdentity(DocumentTemplate template,
                                               SystemDocumentTemplateCatalog.Definition definition) {
        if (!definition.businessType().equals(template.getBusinessType())
                || !Integer.valueOf(1).equals(template.getEnabled())) {
            throw new BusinessException("SYSTEM_DOCUMENT_TEMPLATE_IDENTITY_CONFLICT",
                    "系统模板编码已被其他业务类型占用或模板已停用: " + definition.templateCode());
        }
    }

    private void lockTenantCodeScope() {
        Long tenantId = requireTenant();
        templateMapper.ensureTenantCodeScope(tenantId);
        if (templateMapper.lockTenantCodeScope(tenantId) == null) {
            throw new BusinessException("DOCUMENT_TEMPLATE_CODE_SCOPE_UNAVAILABLE", "模板编号锁定范围不可用");
        }
    }

    private Long requireTenant() {
        Long tenantId = UserContext.getCurrentTenantId();
        if (tenantId == null) throw new BusinessException("AUTH_CONTEXT_MISSING", "缺少租户上下文");
        return tenantId;
    }

    public enum InstallAction { CREATED, UPGRADED, UNCHANGED }
    public enum BindingAction { BOUND, UPDATED_SYSTEM, UNCHANGED_SYSTEM, PRESERVED_CUSTOM }

    public record InstallResult(String businessType, Long templateId, Long versionId, InstallAction action,
                                BindingAction bindingAction) {
    }

    public record SystemTemplateStatus(String businessType, String templateCode, String templateName,
                                       String schemaVersion, String orientation, Long templateId, Long versionId,
                                       boolean installed, boolean current, String defaultBinding) {
    }
}
