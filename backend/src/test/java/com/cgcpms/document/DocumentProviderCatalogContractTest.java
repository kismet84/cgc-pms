package com.cgcpms.document;

import com.cgcpms.closeout.service.ProjectCloseoutService;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.service.CostControlService;
import com.cgcpms.cost.service.CostSubjectV2Service;
import com.cgcpms.document.catalog.DocumentTemplateFieldCatalog;
import com.cgcpms.document.provider.*;
import com.cgcpms.project.service.ProjectCommencementService;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentProviderCatalogContractTest {
    private static final Set<String> FORBIDDEN = Set.of(
            "workflow", "approval", "approvalRecords", "signatures", "attachments",
            "tenantId", "deletedFlag", "approvalInstance", "idempotencyKey");

    @Test
    void newlyConnectedProviderCatalogsHaveSafeResolvableSamples() {
        CostSubjectV2Service costSubject = mock(CostSubjectV2Service.class);
        List<DocumentDataProvider> providers = List.of(
                new ProjectCommencementDocumentDataProvider(mock(ProjectCommencementService.class)),
                new CostCorrectiveActionDocumentDataProvider(mock(CostControlService.class)),
                new ProjectFinalAcceptanceDocumentDataProvider(mock(ProjectCloseoutService.class)),
                new CostSubjectMappingDocumentDataProvider(costSubject),
                new BidCostTargetTransferDocumentDataProvider(costSubject),
                new BidCostTargetTransferReversalDocumentDataProvider(costSubject),
                new FinanceCostAllocationDocumentDataProvider(costSubject),
                new FinanceCostAllocationReversalDocumentDataProvider(costSubject));

        providers.forEach(this::assertProvider);
    }

    @Test
    void providerKeepsDomainServiceAccessDenialAndFormalStateGate() {
        CostControlService service = mock(CostControlService.class);
        CostCorrectiveActionDocumentDataProvider provider = new CostCorrectiveActionDocumentDataProvider(service);
        when(service.correctiveActionDetail(1L))
                .thenThrow(new BusinessException("PROJECT_ACCESS_DENIED", "项目无权访问"));
        BusinessException denied = assertThrows(BusinessException.class, () -> provider.loadPreview(1L));
        assertEquals("PROJECT_ACCESS_DENIED", denied.getCode());

        when(service.correctiveActionDetail(2L)).thenReturn(Map.of("main", Map.of("status", "PENDING"), "items", List.of()));
        assertEquals("cost-corrective-action.v1", provider.loadPreview(2L).schemaVersion());
        BusinessException state = assertThrows(BusinessException.class, () -> provider.load(2L));
        assertEquals("DOCUMENT_COST_CORRECTIVE_STATE_INVALID", state.getCode());
    }

    private void assertProvider(DocumentDataProvider provider) {
        DocumentTemplateFieldCatalog.Catalog catalog = provider.fieldCatalog();
        DocumentDataSnapshot sample = provider.sampleData();
        assertEquals(provider.businessType(), catalog.businessType());
        assertEquals(provider.schemaVersion(), catalog.schemaVersion());
        assertEquals(provider.schemaVersion(), sample.schemaVersion());
        assertFalse(provider.queryAuthority().isBlank());
        assertFalse(catalog.fields().isEmpty());
        catalog.fields().forEach(field -> {
            for (String segment : field.path().split("\\.")) {
                assertFalse("id".equals(segment) || segment.endsWith("Id") || FORBIDDEN.contains(segment),
                        () -> "internal field: " + field.path());
            }
            assertNotNull(resolveSample(sample.values(), field), () -> "sample path missing: " + field.path());
        });
    }

    private Object resolveSample(Map<String, Object> root, DocumentTemplateFieldCatalog.Field field) {
        if (field.collectionPath() == null) return resolve(root, field.path());
        Object collection = resolve(root, field.collectionPath());
        assertInstanceOf(Collection.class, collection);
        Object first = ((Collection<?>) collection).stream().findFirst().orElse(null);
        assertInstanceOf(Map.class, first);
        return resolve((Map<?, ?>) first, field.path().substring(field.collectionPath().length() + 1));
    }

    private Object resolve(Map<?, ?> root, String path) {
        Object value = root;
        for (String segment : path.split("\\.")) {
            if (!(value instanceof Map<?, ?> map)) return null;
            value = map.get(segment);
        }
        return value;
    }
}
