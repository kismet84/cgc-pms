package com.cgcpms.document.provider;

public interface DocumentDataProvider {
    String businessType();
    DocumentDataSnapshot load(Long businessId);

    default DocumentDataSnapshot loadPreview(Long businessId) {
        return load(businessId);
    }
}
