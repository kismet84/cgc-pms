package com.cgcpms.document.provider;

import com.cgcpms.document.catalog.DocumentTemplateFieldCatalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class DocumentSampleData {
    private DocumentSampleData() {
    }

    static DocumentDataSnapshot from(DocumentTemplateFieldCatalog.Catalog catalog) {
        Map<String, Object> root = new LinkedHashMap<>();
        for (DocumentTemplateFieldCatalog.Field field : catalog.fields()) {
            if (field.collectionPath() == null) {
                put(root, field.path(), sampleValue(field));
                continue;
            }
            Map<String, Object> row = collectionRow(root, field.collectionPath());
            String prefix = field.collectionPath() + ".";
            if (field.path().startsWith(prefix)) {
                put(row, field.path().substring(prefix.length()), sampleValue(field));
            }
        }
        return new DocumentDataSnapshot(catalog.schemaVersion(), root);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> collectionRow(Map<String, Object> root, String collectionPath) {
        String[] parts = collectionPath.split("\\.");
        Map<String, Object> parent = root;
        for (int index = 0; index < parts.length - 1; index++) {
            parent = (Map<String, Object>) parent.computeIfAbsent(parts[index], key -> new LinkedHashMap<>());
        }
        List<Map<String, Object>> rows = (List<Map<String, Object>>) parent.computeIfAbsent(
                parts[parts.length - 1], key -> new ArrayList<>());
        if (rows.isEmpty()) rows.add(new LinkedHashMap<>());
        return rows.get(0);
    }

    @SuppressWarnings("unchecked")
    private static void put(Map<String, Object> root, String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> parent = root;
        for (int index = 0; index < parts.length - 1; index++) {
            parent = (Map<String, Object>) parent.computeIfAbsent(parts[index], key -> new LinkedHashMap<>());
        }
        parent.put(parts[parts.length - 1], value);
    }

    private static Object sampleValue(DocumentTemplateFieldCatalog.Field field) {
        if (field.masked()) return "****";
        return switch (field.valueType()) {
            case "MONEY" -> "0.00";
            case "NUMBER" -> "0";
            case "DATE" -> "2026-01-01";
            case "DATETIME" -> "2026-01-01 00:00:00";
            case "BOOLEAN" -> false;
            default -> field.label();
        };
    }
}
