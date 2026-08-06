package com.cgcpms.document.provider;

import com.cgcpms.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Component
public class DocumentDataProviderRegistry {
    private final Map<String, DocumentDataProvider> providers;

    public DocumentDataProviderRegistry(List<DocumentDataProvider> providerList) {
        Map<String, DocumentDataProvider> indexed = new HashMap<>();
        for (DocumentDataProvider provider : providerList) {
            String type = normalize(provider.businessType());
            if (type.isBlank()) {
                throw new IllegalStateException("Document data provider business type is blank");
            }
            if (indexed.putIfAbsent(type, provider) != null) {
                throw new IllegalStateException("Duplicate document data provider: " + type);
            }
        }
        this.providers = Collections.unmodifiableMap(new TreeMap<>(indexed));
    }

    public DocumentDataProvider require(String businessType) {
        String normalized = normalize(businessType);
        DocumentDataProvider provider = providers.get(normalized);
        if (provider == null) {
            throw new BusinessException("DOCUMENT_PROVIDER_UNAVAILABLE", "当前业务类型尚未启用文档生成");
        }
        return provider;
    }

    public boolean has(String businessType) {
        return providers.containsKey(normalize(businessType));
    }

    public Set<String> businessTypes() {
        return providers.keySet();
    }

    public List<DocumentDataProvider> providers() {
        return List.copyOf(providers.values());
    }

    private static String normalize(String businessType) {
        return businessType == null ? "" : businessType.trim().toUpperCase(Locale.ROOT);
    }
}
