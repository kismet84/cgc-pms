package com.cgcpms.document;

import com.cgcpms.document.catalog.DocumentTemplateFieldCatalog;
import com.cgcpms.document.provider.DocumentDataProvider;
import com.cgcpms.document.provider.DocumentDataSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentSampleDataTest {
    @Test
    void sampleDataFollowsScalarAndCollectionCatalogPaths() {
        DocumentDataProvider provider = new DocumentDataProvider() {
            @Override public String businessType() { return "TEST"; }
            @Override public DocumentTemplateFieldCatalog.Catalog fieldCatalog() {
                return new DocumentTemplateFieldCatalog.Catalog("TEST", "test.v1", List.of(
                        new DocumentTemplateFieldCatalog.Field("header.code", "编号", "TEXT", false,
                                null, false, "基本信息", 1),
                        new DocumentTemplateFieldCatalog.Field("items.amount", "金额", "MONEY", false,
                                "items", false, "明细", 2)));
            }
            @Override public DocumentDataSnapshot load(Long businessId) { return sampleData(); }
        };

        DocumentDataSnapshot sample = provider.sampleData();

        assertEquals("test.v1", sample.schemaVersion());
        assertEquals("编号", ((Map<?, ?>) sample.values().get("header")).get("code"));
        assertEquals("0.00", ((Map<?, ?>) ((List<?>) sample.values().get("items")).get(0)).get("amount"));
    }
}
