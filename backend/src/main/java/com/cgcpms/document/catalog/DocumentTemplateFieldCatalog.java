package com.cgcpms.document.catalog;

import com.cgcpms.document.provider.DocumentDataProviderRegistry;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Provider registry facade. Business field facts live in their provider only. */
@Component
public class DocumentTemplateFieldCatalog {
    private final DocumentDataProviderRegistry providerRegistry;

    public DocumentTemplateFieldCatalog(DocumentDataProviderRegistry providerRegistry) {
        this.providerRegistry = providerRegistry;
    }

    public Catalog require(String businessType) {
        return providerRegistry.require(businessType).fieldCatalog();
    }

    public record Catalog(String businessType, String schemaVersion, List<Field> fields) {
        public Catalog {
            if (businessType == null || !businessType.matches("[A-Z][A-Z0-9_]{1,79}")) {
                throw new IllegalArgumentException("Invalid document catalog business type: " + businessType);
            }
            if (schemaVersion == null || !schemaVersion.matches("[A-Za-z0-9._-]{1,30}")) {
                throw new IllegalArgumentException("Invalid document catalog schema version: " + schemaVersion);
            }
            List<Field> source = List.copyOf(fields);
            if (source.isEmpty() || source.size() > 500) {
                throw new IllegalArgumentException("Document catalog fields must contain 1 to 500 entries");
            }
            Set<String> paths = new LinkedHashSet<>();
            for (Field field : source) {
                if (field.path() == null
                        || !field.path().matches("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)*")
                        || !paths.add(field.path())) {
                    throw new IllegalArgumentException("Invalid or duplicate document field path: " + field.path());
                }
                if (field.collectionPath() != null
                        && (!field.collectionPath().matches("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)*")
                        || !field.path().startsWith(field.collectionPath() + "."))) {
                    throw new IllegalArgumentException("Invalid document field collection context: " + field.path());
                }
            }
            fields = java.util.stream.IntStream.range(0, source.size())
                    .mapToObj(index -> source.get(index).withDefaults(index))
                    .toList();
        }

        public Set<String> fieldPaths() {
            Set<String> paths = new LinkedHashSet<>();
            fields.forEach(field -> paths.add(field.path()));
            return paths;
        }

        public Set<String> collectionPaths() {
            Set<String> paths = new LinkedHashSet<>();
            fields.stream().map(Field::collectionPath).filter(value -> value != null && !value.isBlank())
                    .forEach(paths::add);
            return paths;
        }

        public Field field(String path) {
            return fields.stream().filter(field -> field.path().equals(path)).findFirst().orElse(null);
        }
    }

    public record Field(String path, String label, String valueType, boolean nullable,
                        String collectionPath, boolean masked, String group, int sortOrder) {
        public Field(String path, String label, String valueType, boolean nullable,
                     String collectionPath, boolean masked) {
            this(path, label, valueType, nullable, collectionPath, masked, null, -1);
        }

        private Field withDefaults(int index) {
            String resolvedGroup = group == null || group.isBlank()
                    ? path.substring(0, path.indexOf('.') < 0 ? path.length() : path.indexOf('.'))
                    : group;
            return new Field(path, label, valueType, nullable, collectionPath, masked,
                    resolvedGroup, sortOrder < 0 ? index : sortOrder);
        }
    }
}
