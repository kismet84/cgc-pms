package com.cgcpms.document.service;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.document.canvas.DocumentCanvasCompiler;
import com.cgcpms.document.provider.DocumentDataProvider;
import com.cgcpms.document.provider.DocumentDataProviderRegistry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Collections;

@Component
public class SystemDocumentTemplateCatalog {
    public static final String EXCLUDED_BUSINESS_TYPE = "COST_SUBJECT_MAPPING";
    private static final String RESOURCE = "document/system-document-templates.json";

    private final ObjectMapper objectMapper;
    private final DocumentCanvasCompiler canvasCompiler;
    private final DocumentDataProviderRegistry providerRegistry;
    private final Map<String, Definition> definitions;

    public SystemDocumentTemplateCatalog(ObjectMapper objectMapper, DocumentCanvasCompiler canvasCompiler,
                                         DocumentDataProviderRegistry providerRegistry) {
        this.objectMapper = objectMapper;
        this.canvasCompiler = canvasCompiler;
        this.providerRegistry = providerRegistry;
        this.definitions = load();
    }

    public List<Definition> definitions() {
        return List.copyOf(definitions.values());
    }

    public Definition require(String businessType) {
        String normalized = normalize(businessType);
        Definition definition = definitions.get(normalized);
        if (definition == null) {
            throw new BusinessException("SYSTEM_DOCUMENT_TEMPLATE_UNAVAILABLE", "当前业务类型没有系统单据模板");
        }
        return definition;
    }

    public ValidatedDefinition validate(Definition definition) {
        DocumentDataProvider provider = providerRegistry.require(definition.businessType());
        if (!"SYSTEM".equals(provider.defaultTemplatePolicy())) {
            throw new BusinessException("SYSTEM_DOCUMENT_TEMPLATE_POLICY_INVALID",
                    "业务类型未启用系统模板策略: " + definition.businessType());
        }
        if (!provider.schemaVersion().equals(definition.schemaVersion())) {
            throw new BusinessException("SYSTEM_DOCUMENT_TEMPLATE_SCHEMA_MISMATCH",
                    "系统模板与Provider契约版本不一致: " + definition.businessType());
        }
        String designSchema;
        try {
            designSchema = objectMapper.writeValueAsString(definition.designSchema());
        } catch (Exception exception) {
            throw new BusinessException("SYSTEM_DOCUMENT_TEMPLATE_INVALID", "系统模板定义无法序列化", exception);
        }
        DocumentCanvasCompiler.Compilation compilation = canvasCompiler.compile(designSchema, provider.fieldCatalog());
        Set<String> missing = new LinkedHashSet<>(provider.fieldCatalog().fieldPaths());
        missing.removeAll(compilation.fieldManifest());
        Set<String> unexpected = new LinkedHashSet<>(compilation.fieldManifest());
        unexpected.removeAll(provider.fieldCatalog().fieldPaths());
        if (!missing.isEmpty() || !unexpected.isEmpty()) {
            throw new BusinessException("SYSTEM_DOCUMENT_TEMPLATE_FIELD_COVERAGE_INVALID",
                    "系统模板字段覆盖不完整: missing=" + missing + ", unexpected=" + unexpected);
        }
        return new ValidatedDefinition(definition, designSchema, compilation.html());
    }

    public List<ValidatedDefinition> validateAll() {
        Set<String> expected = new LinkedHashSet<>(providerRegistry.businessTypes());
        expected.remove(EXCLUDED_BUSINESS_TYPE);
        if (!expected.equals(definitions.keySet())) {
            Set<String> missing = new LinkedHashSet<>(expected);
            missing.removeAll(definitions.keySet());
            Set<String> extra = new LinkedHashSet<>(definitions.keySet());
            extra.removeAll(expected);
            throw new BusinessException("SYSTEM_DOCUMENT_TEMPLATE_COVERAGE_INVALID",
                    "系统模板目录必须与启用Provider一一对应: missing=" + missing + ", extra=" + extra);
        }
        return definitions.values().stream().map(this::validate).toList();
    }

    private Map<String, Definition> load() {
        try (InputStream input = new ClassPathResource(RESOURCE).getInputStream()) {
            List<Definition> loaded = objectMapper.readValue(input, new TypeReference<>() {});
            Map<String, Definition> indexed = new LinkedHashMap<>();
            for (Definition value : loaded) {
                String type = normalize(value.businessType());
                if (!type.matches("[A-Z][A-Z0-9_]{1,79}") || value.templateCode() == null
                        || !value.templateCode().matches("[A-Za-z][A-Za-z0-9_-]{2,79}")
                        || value.templateName() == null || value.templateName().isBlank()
                        || value.schemaVersion() == null || value.designSchema() == null
                        || indexed.putIfAbsent(type, value) != null) {
                    throw new IllegalStateException("Invalid or duplicate system document template: " + type);
                }
            }
            return Collections.unmodifiableMap(indexed);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot load " + RESOURCE, exception);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    public record Definition(String templateCode, String templateName, String businessType, String schemaVersion,
                             String orientation, JsonNode designSchema) {
    }

    public record ValidatedDefinition(Definition definition, String designSchema, String templateContent) {
    }
}
