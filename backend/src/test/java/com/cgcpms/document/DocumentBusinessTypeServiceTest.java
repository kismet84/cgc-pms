package com.cgcpms.document;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.document.catalog.DocumentTemplateFieldCatalog;
import com.cgcpms.document.provider.DocumentBusinessTypeService;
import com.cgcpms.document.provider.DocumentDataProvider;
import com.cgcpms.document.provider.DocumentDataProviderRegistry;
import com.cgcpms.document.provider.DocumentDataSnapshot;
import com.cgcpms.workflow.entity.WfTemplate;
import com.cgcpms.workflow.mapper.WfTemplateMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentBusinessTypeServiceTest {
    @AfterEach
    void clearContext() {
        TestUserContext.clear();
    }

    @Test
    void listsEnabledTypesOnceAndMarksMissingProviderFailClosed() {
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);
        WfTemplateMapper mapper = mock(WfTemplateMapper.class);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                template("SUB_MEASURE", "分包计量审批流程"),
                template("SUB_MEASURE", "分包计量大额流程"),
                template("TECHNICAL_SCHEME", "技术方案审批")));
        DocumentDataProvider ready = provider("SUB_MEASURE", "分包计量", "sub-measure.v1");

        List<DocumentBusinessTypeService.BusinessTypeDefinition> rows = new DocumentBusinessTypeService(
                mapper, new DocumentDataProviderRegistry(List.of(ready))).listEnabled();

        assertEquals(2, rows.size());
        assertEquals("分包计量", rows.get(0).displayName());
        assertTrue(rows.get(0).providerReady());
        assertEquals(1, rows.get(0).fieldCount());
        assertEquals("TECHNICAL_SCHEME", rows.get(1).businessType());
        assertFalse(rows.get(1).providerReady());
        assertEquals(0, rows.get(1).fieldCount());
    }

    @Test
    void requiresTenantContext() {
        WfTemplateMapper mapper = mock(WfTemplateMapper.class);
        DocumentBusinessTypeService service = new DocumentBusinessTypeService(
                mapper, new DocumentDataProviderRegistry(List.of()));

        BusinessException error = assertThrows(BusinessException.class, service::listEnabled);

        assertEquals("AUTH_CONTEXT_MISSING", error.getCode());
    }

    @Test
    void registryRejectsNormalizedDuplicateTypes() {
        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                new DocumentDataProviderRegistry(List.of(
                        provider("SUB_MEASURE", "A", "a.v1"),
                        provider(" sub_measure ", "B", "b.v1"))));

        assertTrue(error.getMessage().contains("Duplicate"));
    }

    private static WfTemplate template(String type, String name) {
        WfTemplate template = new WfTemplate();
        template.setBusinessType(type);
        template.setTemplateName(name);
        return template;
    }

    private static DocumentDataProvider provider(String type, String name, String schema) {
        return new DocumentDataProvider() {
            @Override public String businessType() { return type; }
            @Override public String displayName() { return name; }
            @Override public DocumentTemplateFieldCatalog.Catalog fieldCatalog() {
                return new DocumentTemplateFieldCatalog.Catalog(type.trim(), schema, List.of(
                        new DocumentTemplateFieldCatalog.Field("document.code", "编号", "TEXT", false,
                                null, false, "基本信息", 0)));
            }
            @Override public DocumentDataSnapshot load(Long businessId) {
                return new DocumentDataSnapshot(schema, Map.of("document", Map.of("code", "DOC-001")));
            }
        };
    }
}
