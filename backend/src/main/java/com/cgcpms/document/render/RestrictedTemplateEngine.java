package com.cgcpms.document.render;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.document.config.DocumentGenerationProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RestrictedTemplateEngine {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)*)\\s*}}");
    private static final Pattern LOOP = Pattern.compile(
            "\\{\\{#each\\s+([A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)*)\\s*}}(.*?)\\{\\{/each}}",
            Pattern.DOTALL);
    private static final Pattern DATA_IMAGE = Pattern.compile("data:image/(?:png|jpeg|jpg|gif);base64,([A-Za-z0-9+/=\\r\\n]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FORBIDDEN = Pattern.compile(
            "<(?:script|iframe|object|embed|base|link)\\b|@import\\b|javascript\\s*:|file\\s*:|https?\\s*:|(?<!:)//|expression\\s*\\(|behavior\\s*:",
            Pattern.CASE_INSENSITIVE);

    private final DocumentGenerationProperties properties;

    public RestrictedTemplateEngine(DocumentGenerationProperties properties) {
        this.properties = properties;
    }

    public void validate(String template) {
        if (template == null || template.isBlank()) {
            throw new BusinessException("DOCUMENT_TEMPLATE_EMPTY", "模板正文不能为空");
        }
        if (template.getBytes(StandardCharsets.UTF_8).length > properties.getMaxTemplateBytes()) {
            throw new BusinessException("DOCUMENT_TEMPLATE_TOO_LARGE", "模板正文超过大小限制");
        }
        if (FORBIDDEN.matcher(template).find()) {
            throw new BusinessException("DOCUMENT_TEMPLATE_RESOURCE_FORBIDDEN", "模板包含脚本、本地文件或外部网络资源");
        }
        validateInlineImages(template);
    }

    public String render(String template, Map<String, ?> values) {
        validate(template);
        validateCollections(values);
        String expanded = renderCollections(template, values);
        String output = renderScalars(expanded, values, "");
        if (output.contains("{{") || output.contains("}}")) {
            throw new BusinessException("DOCUMENT_TEMPLATE_SYNTAX_INVALID", "模板包含不受支持的表达式");
        }
        return output;
    }

    private String renderCollections(String template, Map<String, ?> values) {
        Matcher matcher = LOOP.matcher(template);
        StringBuffer output = new StringBuffer(template.length());
        while (matcher.find()) {
            String path = matcher.group(1);
            Object collectionValue = resolve(values, path);
            if (!(collectionValue instanceof Collection<?> collection)) {
                throw new BusinessException("DOCUMENT_FIELD_TYPE_UNSUPPORTED", "循环字段必须为集合: " + path);
            }
            String rowTemplate = matcher.group(2);
            if (rowTemplate.contains("{{#each") || rowTemplate.contains("{{/each}}")) {
                throw new BusinessException("DOCUMENT_TEMPLATE_LOOP_NESTED", "模板不允许嵌套循环");
            }
            StringBuilder rows = new StringBuilder();
            for (Object item : collection) {
                if (!(item instanceof Map<?, ?> map)) {
                    throw new BusinessException("DOCUMENT_FIELD_TYPE_UNSUPPORTED", "循环明细必须为对象: " + path);
                }
                rows.append(renderScalars(rowTemplate, map, path + "."));
            }
            matcher.appendReplacement(output, Matcher.quoteReplacement(rows.toString()));
        }
        matcher.appendTail(output);
        String result = output.toString();
        if (result.contains("{{#each") || result.contains("{{/each}}")) {
            throw new BusinessException("DOCUMENT_TEMPLATE_SYNTAX_INVALID", "循环语法不完整");
        }
        return result;
    }

    private String renderScalars(String template, Map<?, ?> values, String pathPrefix) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuffer output = new StringBuffer(template.length());
        while (matcher.find()) {
            Object value = resolve(values, matcher.group(1));
            if (value == null) {
                throw new BusinessException("DOCUMENT_FIELD_MISSING", "模板字段缺失: " + pathPrefix + matcher.group(1));
            }
            if (value instanceof Map<?, ?> || value instanceof Collection<?>) {
                throw new BusinessException("DOCUMENT_FIELD_TYPE_UNSUPPORTED", "模板占位符仅允许标量字段: "
                        + pathPrefix + matcher.group(1));
            }
            matcher.appendReplacement(output, Matcher.quoteReplacement(escapeHtml(String.valueOf(value))));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private void validateInlineImages(String template) {
        int total = 0;
        Matcher matcher = DATA_IMAGE.matcher(template);
        while (matcher.find()) {
            byte[] bytes;
            try {
                bytes = Base64.getMimeDecoder().decode(matcher.group(1));
            } catch (IllegalArgumentException exception) {
                throw new BusinessException("DOCUMENT_IMAGE_INVALID", "内联图片Base64无效");
            }
            if (bytes.length > properties.getMaxImageBytes()) {
                throw new BusinessException("DOCUMENT_IMAGE_TOO_LARGE", "单张内联图片超过大小限制");
            }
            total += bytes.length;
            if (total > properties.getMaxTotalImageBytes()) {
                throw new BusinessException("DOCUMENT_IMAGES_TOO_LARGE", "内联图片总大小超过限制");
            }
        }
        String withoutImages = DATA_IMAGE.matcher(template).replaceAll("data:image/allowed;base64,removed");
        if (Pattern.compile("(?:src|url)\\s*[=(]\s*['\"]?data:", Pattern.CASE_INSENSITIVE).matcher(withoutImages).find()) {
            throw new BusinessException("DOCUMENT_IMAGE_TYPE_FORBIDDEN", "仅允许PNG/JPEG/GIF内联图片");
        }
    }

    private void validateCollections(Object value) {
        if (value instanceof Map<?, ?> map) {
            map.values().forEach(this::validateCollections);
        } else if (value instanceof Collection<?> collection) {
            if (collection.size() > properties.getMaxCollectionItems()) {
                throw new BusinessException("DOCUMENT_COLLECTION_TOO_LARGE", "业务明细数量超过限制");
            }
            collection.forEach(this::validateCollections);
        }
    }

    private Object resolve(Map<?, ?> root, String path) {
        Object current = root;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map) || !map.containsKey(segment)) return null;
            current = map.get(segment);
        }
        return current;
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
