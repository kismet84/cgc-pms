package com.cgcpms.document.provider;

import com.cgcpms.document.catalog.DocumentTemplateFieldCatalog;

public interface DocumentDataProvider {
    String businessType();

    default String displayName() {
        return businessType();
    }

    default String schemaVersion() {
        return fieldCatalog().schemaVersion();
    }

    default String queryAuthority() {
        throw new IllegalStateException("Document provider query authority is not configured: " + businessType());
    }

    default String defaultTemplatePolicy() {
        return "NONE";
    }

    default DocumentTemplateFieldCatalog.Catalog fieldCatalog() {
        throw new IllegalStateException("Document provider catalog is not configured: " + businessType());
    }

    default DocumentDataSnapshot sampleData() {
        return DocumentSampleData.from(fieldCatalog());
    }

    DocumentDataSnapshot load(Long businessId);

    default DocumentDataSnapshot loadPreview(Long businessId) {
        return load(businessId);
    }
}
