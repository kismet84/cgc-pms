package com.cgcpms.document.provider;

import com.cgcpms.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class DocumentDataProviderRegistry {
    private final Map<String, DocumentDataProvider> providers;

    public DocumentDataProviderRegistry(List<DocumentDataProvider> providerList) {
        Map<String, DocumentDataProvider> indexed = new HashMap<>();
        for (DocumentDataProvider provider : providerList) {
            String type = provider.businessType().toUpperCase(Locale.ROOT);
            if (indexed.putIfAbsent(type, provider) != null) {
                throw new IllegalStateException("Duplicate document data provider: " + type);
            }
        }
        this.providers = Map.copyOf(indexed);
    }

    public DocumentDataProvider require(String businessType) {
        String normalized = businessType == null ? "" : businessType.toUpperCase(Locale.ROOT);
        DocumentDataProvider provider = providers.get(normalized);
        if (provider == null) {
            throw new BusinessException("DOCUMENT_PROVIDER_UNAVAILABLE", "当前业务类型尚未启用文档生成");
        }
        return provider;
    }
}
