package com.cgcpms.document.provider;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.document.catalog.DocumentTemplateFieldCatalog;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

final class DocumentProviderMappingSupport {
    private static final Set<String> PREVIEW_STATUSES = Set.of(
            "DRAFT", "REJECTED", "PENDING", "APPROVING", "APPROVED", "ACTIVE", "FINALIZED");

    private DocumentProviderMappingSupport() {
    }

    static DocumentTemplateFieldCatalog.Catalog catalog(
            String businessType, String schemaVersion, DocumentTemplateFieldCatalog.Field... fields) {
        return new DocumentTemplateFieldCatalog.Catalog(businessType, schemaVersion, List.of(fields));
    }

    static DocumentTemplateFieldCatalog.Field field(String path, String label, String type, boolean nullable) {
        return new DocumentTemplateFieldCatalog.Field(path, label, type, nullable, null, false);
    }

    static DocumentTemplateFieldCatalog.Field item(
            String path, String label, String type, String collectionPath) {
        return new DocumentTemplateFieldCatalog.Field(path, label, type, true, collectionPath, false);
    }

    static Map<String, Object> map(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            result.put((String) entries[index], entries[index + 1]);
        }
        return result;
    }

    static DocumentDataSnapshot snapshot(String schemaVersion, Object... entries) {
        return new DocumentDataSnapshot(schemaVersion, map(entries));
    }

    static Object value(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value == null) value = source.get(key.toUpperCase(Locale.ROOT));
            if (value != null) return value;
        }
        return null;
    }

    static String text(Object value) {
        if (value == null) return "";
        return value instanceof TemporalAccessor ? value.toString() : String.valueOf(value);
    }

    static String money(Object value) {
        if (value == null || value.toString().isBlank()) return "0.00";
        return new BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    static String number(Object value) {
        if (value == null || value.toString().isBlank()) return "0";
        return new BigDecimal(value.toString()).stripTrailingZeros().toPlainString();
    }

    static <T> List<Map<String, Object>> rows(List<T> source, Function<T, Map<String, Object>> mapper) {
        if (source == null) return List.of();
        List<Map<String, Object>> result = new ArrayList<>(source.size());
        source.forEach(value -> result.add(mapper.apply(value)));
        return result;
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> mapRows(Map<String, Object> source, String key) {
        Object rows = value(source, key);
        return rows instanceof List<?> list
                ? list.stream().filter(Map.class::isInstance).map(row -> (Map<String, Object>) row).toList()
                : List.of();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> mapValue(Map<String, Object> source, String key) {
        Object result = value(source, key);
        return result instanceof Map<?, ?> ? (Map<String, Object>) result : Map.of();
    }

    static void requireState(String status, boolean formal, Set<String> formalStates,
                             Set<String> previewStates, String code, String label) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        Set<String> allowed = formal ? formalStates : previewStates;
        if (!allowed.contains(normalized)) {
            throw new BusinessException(code, formal ? "正式" + label + "当前状态不允许生成" : label + "当前状态不允许预览");
        }
    }

    static void requireApproval(String status, boolean formal, String code, String label) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (formal && !Set.of("APPROVED", "ACTIVE", "FINALIZED").contains(normalized)) {
            throw new BusinessException(code, "正式" + label + "仅允许审批通过后生成");
        }
        if (!formal && !PREVIEW_STATUSES.contains(normalized)) {
            throw new BusinessException(code, label + "当前状态不允许预览");
        }
    }
}
