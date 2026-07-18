package com.cgcpms.document.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.document.catalog.DocumentTemplateFieldCatalog;
import com.cgcpms.document.entity.DocumentDefaultBinding;
import com.cgcpms.document.entity.DocumentTemplate;
import com.cgcpms.document.entity.DocumentTemplateVersion;
import com.cgcpms.document.mapper.DocumentDefaultBindingMapper;
import com.cgcpms.document.mapper.DocumentTemplateMapper;
import com.cgcpms.document.mapper.DocumentTemplateVersionMapper;
import com.cgcpms.document.render.RestrictedTemplateEngine;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class DocumentTemplateService {
    private static final Set<String> BUSINESS_TYPES = Set.of("PAYMENT", "SETTLEMENT");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)*)\\s*}}");
    private static final Pattern LOOP = Pattern.compile(
            "\\{\\{#each\\s+([A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)*)\\s*}}(.*?)\\{\\{/each}}",
            Pattern.DOTALL);

    private final DocumentTemplateMapper templateMapper;
    private final DocumentTemplateVersionMapper versionMapper;
    private final DocumentDefaultBindingMapper bindingMapper;
    private final RestrictedTemplateEngine templateEngine;
    private final ObjectMapper objectMapper;
    private final DocumentTemplateFieldCatalog fieldCatalog;

    public record DraftCommand(String schemaVersion, String templateContent, String fieldManifest, String remark) {
    }

    public record TemplateSummary(Long id, String templateCode, String templateName, String businessType,
                                  Integer enabled, Long defaultVersionId, Integer defaultLockVersion,
                                  LocalDateTime updatedAt) {
    }

    public record DefaultBindingSummary(Long templateId, Long templateVersionId, Integer lockVersion) {
    }

    public record TemplateDetail(DocumentTemplate template, List<DocumentTemplateVersion> versions,
                                 DefaultBindingSummary defaultBinding) {
    }

    public record TemplateExport(String templateCode, String templateName, String businessType,
                                 String schemaVersion, String templateContent, String fieldManifest,
                                 String remark) {
    }

    public record TemplateValidationResult(String schemaVersion, int fieldCount, Set<String> referencedFields,
                                           Set<String> collectionPaths) {
    }

    public record TemplateVersionContext(DocumentTemplate template, DocumentTemplateVersion version) {
    }

    @Transactional(rollbackFor = Exception.class)
    public DocumentTemplateVersion create(String code, String name, String businessType, DraftCommand draft) {
        Long tenantId = requireTenant();
        String normalizedType = normalizeBusinessType(businessType);
        if (code == null || !code.matches("[A-Za-z][A-Za-z0-9_-]{2,79}")) {
            throw new BusinessException("DOCUMENT_TEMPLATE_CODE_INVALID", "模板编码格式非法");
        }
        if (name == null || name.isBlank() || name.length() > 200) {
            throw new BusinessException("DOCUMENT_TEMPLATE_NAME_INVALID", "模板名称不能为空且不得超过200字符");
        }
        validateDraft(normalizedType, draft);

        DocumentTemplate template = new DocumentTemplate();
        template.setTenantId(tenantId);
        template.setTemplateCode(code);
        template.setTemplateName(name.trim());
        template.setBusinessType(normalizedType);
        template.setEngineType("HTML_PDF");
        template.setEnabled(1);
        try {
            templateMapper.insert(template);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("DOCUMENT_TEMPLATE_CODE_DUPLICATE", "模板编码已存在");
        }
        return insertDraft(template.getId(), 1, draft);
    }

    @Transactional(rollbackFor = Exception.class)
    public DocumentTemplateVersion createNextDraft(Long templateId, DraftCommand draft) {
        DocumentTemplate template = requireTemplate(templateId);
        if (!Integer.valueOf(1).equals(template.getEnabled())) {
            throw new BusinessException("DOCUMENT_TEMPLATE_DISABLED", "停用模板不能创建新草稿");
        }
        validateDraft(template.getBusinessType(), draft);
        DocumentTemplateVersion latest = versionMapper.selectOne(new LambdaQueryWrapper<DocumentTemplateVersion>()
                .eq(DocumentTemplateVersion::getTenantId, requireTenant())
                .eq(DocumentTemplateVersion::getTemplateId, templateId)
                .orderByDesc(DocumentTemplateVersion::getVersionNo)
                .last("LIMIT 1"));
        int next = latest == null ? 1 : latest.getVersionNo() + 1;
        try {
            return insertDraft(templateId, next, draft);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("DOCUMENT_TEMPLATE_VERSION_CONFLICT", "模板版本号并发冲突，请重试");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateDraft(Long versionId, DraftCommand draft) {
        DocumentTemplateVersion version = requireVersion(versionId);
        DocumentTemplate template = requireTemplate(version.getTemplateId());
        validateDraft(template.getBusinessType(), draft);
        int changed = versionMapper.update(null, new LambdaUpdateWrapper<DocumentTemplateVersion>()
                .eq(DocumentTemplateVersion::getId, versionId)
                .eq(DocumentTemplateVersion::getTenantId, requireTenant())
                .eq(DocumentTemplateVersion::getStatus, "DRAFT")
                .set(DocumentTemplateVersion::getSchemaVersion, draft.schemaVersion().trim())
                .set(DocumentTemplateVersion::getTemplateContent, draft.templateContent())
                .set(DocumentTemplateVersion::getContentHash, sha256(draft.templateContent()))
                .set(DocumentTemplateVersion::getFieldManifest, normalizeManifest(draft.fieldManifest()))
                .set(DocumentTemplateVersion::getRemark, draft.remark()));
        if (changed != 1) {
            throw new BusinessException("DOCUMENT_TEMPLATE_VERSION_IMMUTABLE", "仅草稿版本允许修改");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public DocumentTemplateVersion publish(Long versionId) {
        DocumentTemplateVersion version = requireVersion(versionId);
        if (!"DRAFT".equals(version.getStatus())) {
            throw new BusinessException("DOCUMENT_TEMPLATE_VERSION_IMMUTABLE", "仅草稿版本允许发布");
        }
        DocumentTemplate template = requireTemplate(version.getTemplateId());
        validateDraft(template.getBusinessType(), new DraftCommand(version.getSchemaVersion(), version.getTemplateContent(),
                version.getFieldManifest(), version.getRemark()));
        LocalDateTime now = LocalDateTime.now();
        int changed = versionMapper.update(null, new LambdaUpdateWrapper<DocumentTemplateVersion>()
                .eq(DocumentTemplateVersion::getId, versionId)
                .eq(DocumentTemplateVersion::getTenantId, requireTenant())
                .eq(DocumentTemplateVersion::getStatus, "DRAFT")
                .set(DocumentTemplateVersion::getStatus, "PUBLISHED")
                .set(DocumentTemplateVersion::getPublishedBy, requireUser())
                .set(DocumentTemplateVersion::getPublishedAt, now));
        if (changed != 1) {
            throw new BusinessException("DOCUMENT_TEMPLATE_VERSION_CONFLICT", "模板版本已被其他用户处理");
        }
        version.setStatus("PUBLISHED");
        version.setPublishedBy(requireUser());
        version.setPublishedAt(now);
        return version;
    }

    @Transactional(rollbackFor = Exception.class)
    public void disablePublishedVersion(Long versionId) {
        int changed = versionMapper.update(null, new LambdaUpdateWrapper<DocumentTemplateVersion>()
                .eq(DocumentTemplateVersion::getId, versionId)
                .eq(DocumentTemplateVersion::getTenantId, requireTenant())
                .eq(DocumentTemplateVersion::getStatus, "PUBLISHED")
                .set(DocumentTemplateVersion::getStatus, "DISABLED"));
        if (changed != 1) {
            throw new BusinessException("DOCUMENT_TEMPLATE_VERSION_STATE_INVALID", "仅已发布版本允许停用");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void bindDefault(Long versionId, Integer expectedLockVersion) {
        DocumentTemplateVersion version = requireVersion(versionId);
        if (!"PUBLISHED".equals(version.getStatus())) {
            throw new BusinessException("DOCUMENT_TEMPLATE_NOT_PUBLISHED", "默认模板必须绑定已发布版本");
        }
        DocumentTemplate template = requireTemplate(version.getTemplateId());
        Long tenantId = requireTenant();
        DocumentDefaultBinding current = bindingMapper.selectOne(new LambdaQueryWrapper<DocumentDefaultBinding>()
                .eq(DocumentDefaultBinding::getTenantId, tenantId)
                .eq(DocumentDefaultBinding::getBusinessType, template.getBusinessType()));
        if (current == null) {
            if (expectedLockVersion != null && expectedLockVersion != 0) {
                throw new BusinessException("DOCUMENT_DEFAULT_BINDING_CONFLICT", "默认模板绑定已变化，请刷新后重试");
            }
            DocumentDefaultBinding binding = new DocumentDefaultBinding();
            binding.setTenantId(tenantId);
            binding.setBusinessType(template.getBusinessType());
            binding.setTemplateId(template.getId());
            binding.setTemplateVersionId(version.getId());
            binding.setLockVersion(0);
            binding.setCreatedBy(requireUser());
            binding.setCreatedAt(LocalDateTime.now());
            binding.setUpdatedBy(requireUser());
            binding.setUpdatedAt(LocalDateTime.now());
            try {
                bindingMapper.insert(binding);
                return;
            } catch (DuplicateKeyException exception) {
                throw new BusinessException("DOCUMENT_DEFAULT_BINDING_CONFLICT", "默认模板绑定已变化，请刷新后重试");
            }
        }
        if (expectedLockVersion == null || !expectedLockVersion.equals(current.getLockVersion())) {
            throw new BusinessException("DOCUMENT_DEFAULT_BINDING_CONFLICT", "默认模板绑定已变化，请刷新后重试");
        }
        int changed = bindingMapper.update(null, new LambdaUpdateWrapper<DocumentDefaultBinding>()
                .eq(DocumentDefaultBinding::getTenantId, tenantId)
                .eq(DocumentDefaultBinding::getBusinessType, template.getBusinessType())
                .eq(DocumentDefaultBinding::getLockVersion, expectedLockVersion)
                .set(DocumentDefaultBinding::getTemplateId, template.getId())
                .set(DocumentDefaultBinding::getTemplateVersionId, version.getId())
                .set(DocumentDefaultBinding::getLockVersion, expectedLockVersion + 1)
                .set(DocumentDefaultBinding::getUpdatedBy, requireUser())
                .set(DocumentDefaultBinding::getUpdatedAt, LocalDateTime.now()));
        if (changed != 1) {
            throw new BusinessException("DOCUMENT_DEFAULT_BINDING_CONFLICT", "默认模板绑定已变化，请刷新后重试");
        }
    }

    public DocumentTemplateVersion requireDefaultVersion(String businessType) {
        String normalized = normalizeBusinessType(businessType);
        DocumentDefaultBinding binding = bindingMapper.selectOne(new LambdaQueryWrapper<DocumentDefaultBinding>()
                .eq(DocumentDefaultBinding::getTenantId, requireTenant())
                .eq(DocumentDefaultBinding::getBusinessType, normalized));
        if (binding == null) throw new BusinessException("DOCUMENT_DEFAULT_TEMPLATE_MISSING", "未配置默认业务单据模板");
        DocumentTemplateVersion version = requireVersion(binding.getTemplateVersionId());
        if (!"PUBLISHED".equals(version.getStatus())) {
            throw new BusinessException("DOCUMENT_DEFAULT_TEMPLATE_DISABLED", "默认业务单据模板当前不可用");
        }
        return version;
    }

    public List<TemplateSummary> listTemplates(String businessType) {
        Long tenantId = requireTenant();
        String normalized = businessType == null || businessType.isBlank() ? null : normalizeBusinessType(businessType);
        List<DocumentTemplate> templates = templateMapper.selectList(new LambdaQueryWrapper<DocumentTemplate>()
                .eq(DocumentTemplate::getTenantId, tenantId)
                .eq(normalized != null, DocumentTemplate::getBusinessType, normalized)
                .orderByAsc(DocumentTemplate::getBusinessType)
                .orderByDesc(DocumentTemplate::getUpdatedAt));
        Map<String, DocumentDefaultBinding> bindings = new LinkedHashMap<>();
        bindingMapper.selectList(new LambdaQueryWrapper<DocumentDefaultBinding>()
                        .eq(DocumentDefaultBinding::getTenantId, tenantId))
                .forEach(binding -> bindings.put(binding.getBusinessType(), binding));
        return templates.stream().map(template -> {
            DocumentDefaultBinding binding = bindings.get(template.getBusinessType());
            return new TemplateSummary(template.getId(), template.getTemplateCode(), template.getTemplateName(),
                    template.getBusinessType(), template.getEnabled(),
                    binding == null || !Objects.equals(binding.getTemplateId(), template.getId())
                            ? null : binding.getTemplateVersionId(),
                    binding == null ? null : binding.getLockVersion(), template.getUpdatedAt());
        }).toList();
    }

    public TemplateDetail getTemplateDetail(Long templateId) {
        DocumentTemplate template = requireTemplate(templateId);
        List<DocumentTemplateVersion> versions = versionMapper.selectList(new LambdaQueryWrapper<DocumentTemplateVersion>()
                .eq(DocumentTemplateVersion::getTenantId, requireTenant())
                .eq(DocumentTemplateVersion::getTemplateId, template.getId())
                .orderByDesc(DocumentTemplateVersion::getVersionNo));
        DocumentDefaultBinding binding = bindingMapper.selectOne(new LambdaQueryWrapper<DocumentDefaultBinding>()
                .eq(DocumentDefaultBinding::getTenantId, requireTenant())
                .eq(DocumentDefaultBinding::getBusinessType, template.getBusinessType()));
        return new TemplateDetail(template, versions, binding == null ? null
                : new DefaultBindingSummary(binding.getTemplateId(), binding.getTemplateVersionId(), binding.getLockVersion()));
    }

    public DocumentTemplateFieldCatalog.Catalog getFieldCatalog(String businessType) {
        return fieldCatalog.require(normalizeBusinessType(businessType));
    }

    public TemplateValidationResult validate(String businessType, DraftCommand draft) {
        return validateDraft(normalizeBusinessType(businessType), draft);
    }

    @Transactional(rollbackFor = Exception.class)
    public DocumentTemplateVersion copyVersion(Long templateId, Long sourceVersionId) {
        DocumentTemplate template = requireTemplate(templateId);
        DocumentTemplateVersion source = requireVersion(sourceVersionId);
        if (!Objects.equals(template.getId(), source.getTemplateId())) {
            throw new BusinessException("DOCUMENT_TEMPLATE_VERSION_OWNER_INVALID", "仅允许复制同一模板的版本");
        }
        return createNextDraft(templateId, new DraftCommand(source.getSchemaVersion(), source.getTemplateContent(),
                source.getFieldManifest(), source.getRemark()));
    }

    public TemplateExport exportVersion(Long versionId) {
        DocumentTemplateVersion version = requireVersion(versionId);
        DocumentTemplate template = requireTemplate(version.getTemplateId());
        return new TemplateExport(template.getTemplateCode(), template.getTemplateName(), template.getBusinessType(),
                version.getSchemaVersion(), version.getTemplateContent(), version.getFieldManifest(), version.getRemark());
    }

    public TemplateVersionContext requirePreviewVersionContext(Long versionId) {
        DocumentTemplateVersion version = requireVersion(versionId);
        if (!Set.of("DRAFT", "PUBLISHED").contains(version.getStatus())) {
            throw new BusinessException("DOCUMENT_TEMPLATE_VERSION_STATE_INVALID", "仅草稿或已发布版本允许预览");
        }
        DocumentTemplate template = requireTemplate(version.getTemplateId());
        validateDraft(template.getBusinessType(), new DraftCommand(version.getSchemaVersion(), version.getTemplateContent(),
                version.getFieldManifest(), version.getRemark()));
        return new TemplateVersionContext(template, version);
    }

    private DocumentTemplateVersion insertDraft(Long templateId, int versionNo, DraftCommand draft) {
        DocumentTemplateVersion version = new DocumentTemplateVersion();
        version.setTenantId(requireTenant());
        version.setTemplateId(templateId);
        version.setVersionNo(versionNo);
        version.setStatus("DRAFT");
        version.setSchemaVersion(draft.schemaVersion().trim());
        version.setTemplateContent(draft.templateContent());
        version.setContentHash(sha256(draft.templateContent()));
        version.setFieldManifest(normalizeManifest(draft.fieldManifest()));
        version.setRemark(draft.remark());
        versionMapper.insert(version);
        return version;
    }

    private TemplateValidationResult validateDraft(String businessType, DraftCommand draft) {
        if (draft == null || draft.schemaVersion() == null || !draft.schemaVersion().matches("[A-Za-z0-9._-]{1,30}")) {
            throw new BusinessException("DOCUMENT_SCHEMA_VERSION_INVALID", "数据契约版本格式非法");
        }
        DocumentTemplateFieldCatalog.Catalog catalog = fieldCatalog.require(businessType);
        if (!catalog.schemaVersion().equals(draft.schemaVersion().trim())) {
            throw new BusinessException("DOCUMENT_SCHEMA_VERSION_MISMATCH", "模板与业务输出契约版本不一致");
        }
        templateEngine.validate(draft.templateContent());
        Set<String> manifest = parseManifest(normalizeManifest(draft.fieldManifest()));
        Set<String> unavailable = new LinkedHashSet<>(manifest);
        unavailable.removeAll(catalog.fieldPaths());
        if (!unavailable.isEmpty()) {
            throw new BusinessException("DOCUMENT_FIELD_UNAVAILABLE", "字段目录不存在字段: " + unavailable);
        }
        TemplateReferences references = referencedFields(draft.templateContent());
        Set<String> unknownCollections = new LinkedHashSet<>(references.collectionPaths());
        unknownCollections.removeAll(catalog.collectionPaths());
        if (!unknownCollections.isEmpty()) {
            throw new BusinessException("DOCUMENT_COLLECTION_UNAVAILABLE", "字段目录不存在循环集合: " + unknownCollections);
        }
        validateReferenceContext(catalog, references);
        Set<String> undeclared = new LinkedHashSet<>(references.fields());
        undeclared.removeAll(manifest);
        if (!undeclared.isEmpty()) {
            throw new BusinessException("DOCUMENT_FIELD_NOT_DECLARED", "模板使用了未声明字段: " + undeclared);
        }
        return new TemplateValidationResult(catalog.schemaVersion(), manifest.size(),
                Set.copyOf(references.fields()), Set.copyOf(references.collectionPaths()));
    }

    private TemplateReferences referencedFields(String template) {
        Set<String> fields = new LinkedHashSet<>();
        Set<String> scalarFields = new LinkedHashSet<>();
        Map<String, Set<String>> loopFields = new LinkedHashMap<>();
        Set<String> collectionPaths = new LinkedHashSet<>();
        Matcher loops = LOOP.matcher(template);
        StringBuffer withoutLoops = new StringBuffer(template.length());
        while (loops.find()) {
            String collectionPath = loops.group(1);
            if (loops.group(2).contains("{{#each") || loops.group(2).contains("{{/each}}")) {
                throw new BusinessException("DOCUMENT_TEMPLATE_LOOP_NESTED", "模板不允许嵌套循环");
            }
            collectionPaths.add(collectionPath);
            Set<String> values = loopFields.computeIfAbsent(collectionPath, ignored -> new LinkedHashSet<>());
            Matcher rowFields = PLACEHOLDER.matcher(loops.group(2));
            while (rowFields.find()) {
                String path = collectionPath + "." + rowFields.group(1);
                fields.add(path);
                values.add(path);
            }
            loops.appendReplacement(withoutLoops, "");
        }
        loops.appendTail(withoutLoops);
        Matcher scalars = PLACEHOLDER.matcher(withoutLoops);
        while (scalars.find()) {
            fields.add(scalars.group(1));
            scalarFields.add(scalars.group(1));
        }
        String supported = LOOP.matcher(template).replaceAll("");
        supported = PLACEHOLDER.matcher(supported).replaceAll("");
        if (supported.contains("{{") || supported.contains("}}")) {
            throw new BusinessException("DOCUMENT_TEMPLATE_SYNTAX_INVALID", "模板包含不受支持的表达式");
        }
        return new TemplateReferences(fields, scalarFields, loopFields, collectionPaths);
    }

    private void validateReferenceContext(DocumentTemplateFieldCatalog.Catalog catalog, TemplateReferences references) {
        Set<String> unavailable = new LinkedHashSet<>();
        Set<String> invalidContext = new LinkedHashSet<>();
        for (String path : references.scalarFields()) {
            DocumentTemplateFieldCatalog.Field field = catalog.field(path);
            if (field == null) unavailable.add(path);
            else if (field.collectionPath() != null) invalidContext.add(path);
        }
        references.loopFields().forEach((collectionPath, fields) -> fields.forEach(path -> {
            DocumentTemplateFieldCatalog.Field field = catalog.field(path);
            if (field == null) unavailable.add(path);
            else if (!Objects.equals(collectionPath, field.collectionPath())) invalidContext.add(path);
        }));
        if (!unavailable.isEmpty()) {
            throw new BusinessException("DOCUMENT_FIELD_UNAVAILABLE", "字段目录不存在字段: " + unavailable);
        }
        if (!invalidContext.isEmpty()) {
            throw new BusinessException("DOCUMENT_FIELD_CONTEXT_INVALID", "集合字段必须位于对应循环内: " + invalidContext);
        }
    }

    private record TemplateReferences(Set<String> fields, Set<String> scalarFields,
                                      Map<String, Set<String>> loopFields, Set<String> collectionPaths) {
    }

    private String normalizeManifest(String json) {
        Set<String> fields = parseManifest(json);
        try {
            return objectMapper.writeValueAsString(fields);
        } catch (Exception exception) {
            throw new BusinessException("DOCUMENT_FIELD_MANIFEST_INVALID", "字段清单无法序列化", exception);
        }
    }

    private Set<String> parseManifest(String json) {
        if (json == null || json.isBlank()) {
            throw new BusinessException("DOCUMENT_FIELD_MANIFEST_INVALID", "字段清单不能为空");
        }
        try {
            List<String> values = objectMapper.readValue(json, new TypeReference<>() {});
            Set<String> fields = new LinkedHashSet<>();
            for (String value : values) {
                if (value == null || !value.matches("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)*") || !fields.add(value)) {
                    throw new BusinessException("DOCUMENT_FIELD_MANIFEST_INVALID", "字段清单包含非法或重复字段");
                }
            }
            if (fields.isEmpty() || fields.size() > 500) {
                throw new BusinessException("DOCUMENT_FIELD_MANIFEST_INVALID", "字段清单数量必须为1到500");
            }
            return fields;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("DOCUMENT_FIELD_MANIFEST_INVALID", "字段清单必须是字符串数组");
        }
    }

    private DocumentTemplate requireTemplate(Long id) {
        DocumentTemplate template = templateMapper.selectOne(new LambdaQueryWrapper<DocumentTemplate>()
                .eq(DocumentTemplate::getId, id)
                .eq(DocumentTemplate::getTenantId, requireTenant()));
        if (template == null) throw new BusinessException("DOCUMENT_TEMPLATE_NOT_FOUND", "业务单据模板不存在");
        return template;
    }

    private DocumentTemplateVersion requireVersion(Long id) {
        DocumentTemplateVersion version = versionMapper.selectOne(new LambdaQueryWrapper<DocumentTemplateVersion>()
                .eq(DocumentTemplateVersion::getId, id)
                .eq(DocumentTemplateVersion::getTenantId, requireTenant()));
        if (version == null) throw new BusinessException("DOCUMENT_TEMPLATE_VERSION_NOT_FOUND", "业务单据模板版本不存在");
        return version;
    }

    private String normalizeBusinessType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if (!BUSINESS_TYPES.contains(normalized)) {
            throw new BusinessException("DOCUMENT_BUSINESS_TYPE_INVALID", "仅支持PAYMENT或SETTLEMENT业务单据");
        }
        return normalized;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private Long requireTenant() {
        Long tenantId = UserContext.getCurrentTenantId();
        if (tenantId == null) throw new BusinessException("AUTH_CONTEXT_MISSING", "缺少租户上下文");
        return tenantId;
    }

    private Long requireUser() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) throw new BusinessException("AUTH_CONTEXT_MISSING", "缺少用户上下文");
        return userId;
    }
}
