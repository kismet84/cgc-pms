package com.cgcpms.document;

import com.cgcpms.common.TestUserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.document.entity.DocumentGeneration;
import com.cgcpms.document.entity.DocumentTemplateVersion;
import com.cgcpms.document.render.RenderedDocument;
import com.cgcpms.document.provider.DocumentDataProvider;
import com.cgcpms.document.provider.DocumentDataSnapshot;
import com.cgcpms.document.service.DocumentGenerationService;
import com.cgcpms.document.service.DocumentGenerationReconciliationService;
import com.cgcpms.document.service.DocumentTemplateService;
import com.cgcpms.file.auth.BusinessObjectAuthorizer;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:document_e2e;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;LOCK_TIMEOUT=300000",
        "minio.enabled=true",
        "minio.endpoint=http://localhost:9000",
        "minio.access-key=test",
        "minio.secret-key=test",
        "minio.bucket=document-e2e",
        "document.generation.enabled=true",
        "document.generation.payment-enabled=true",
        "jwt.secret=document-generation-e2e-secret-at-least-32-bytes"
})
@ActiveProfiles({"local", "document-test-provider"})
@Import(DocumentGenerationEndToEndTest.TestProviderConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DocumentGenerationEndToEndTest {
    private static final AtomicReference<String> TEST_APPLY_AMOUNT = new AtomicReference<>("123456.78");

    @Autowired private DocumentTemplateService templateService;
    @Autowired private DocumentGenerationService generationService;
    @Autowired private DocumentGenerationReconciliationService reconciliationService;

    @MockitoBean private MinioClient minioClient;
    @MockitoBean private BusinessObjectAuthorizer authorizer;

    @BeforeEach
    void setUp() {
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);
        TEST_APPLY_AMOUNT.set("123456.78");
    }

    @AfterEach
    void tearDown() {
        TestUserContext.clear();
    }

    @Test
    void publishedSystemTemplateGeneratesArchivesDownloadsAndRemainsTenantScoped() throws Exception {
        DocumentTemplateService.DraftCommand command = new DocumentTemplateService.DraftCommand(
                "payment.v1",
                "<html><head><style>@page { size: A4; margin: 12mm; }</style></head>"
                        + "<body><h1>System payment</h1><p>{{payment.applyCode}}</p><p>{{payment.applyAmount}}</p></body></html>",
                "[\"payment.applyCode\",\"payment.applyAmount\"]",
                "M1 test-only provider and system template");
        DocumentTemplateVersion draft = templateService.create(
                "M1_E2E_SYSTEM_PAYMENT", "M1系统付款模板", "PAYMENT", command);

        BusinessException draftBlocked = assertThrows(BusinessException.class,
                () -> generationService.generate("PAYMENT", 88L, "m1:e2e:draft:88", null));
        assertEquals("DOCUMENT_DEFAULT_TEMPLATE_MISSING", draftBlocked.getCode());

        DocumentTemplateVersion published = templateService.publish(draft.getId());
        templateService.bindDefault(published.getId(), 0);
        RenderedDocument preview = generationService.preview("PAYMENT", 88L);
        assertTrue(new String(preview.content(), 0, 5, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"));
        try (var previewPdf = Loader.loadPDF(preview.content())) {
            assertTrue(new PDFTextStripper().getText(previewPdf).contains("预览件 非正式文件"));
        }
        DocumentGeneration first = generationService.generate("PAYMENT", 88L, "m1:e2e:published:88", null);
        DocumentGeneration duplicate = generationService.generate("PAYMENT", 88L, "m1:e2e:published:88", null);

        assertEquals("SUCCEEDED", first.getStatus());
        assertNotNull(first.getFileId());
        assertEquals(64, first.getSourceDigest().length());
        assertEquals(64, first.getOutputSha256().length());
        assertEquals(first.getId(), duplicate.getId());
        verify(minioClient, times(1)).putObject(any(PutObjectArgs.class));

        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://localhost:9000/document-e2e/file.pdf?X-Amz-Signature=test&X-Amz-Expires=300");
        assertTrue(generationService.downloadUrl(first.getId()).contains("X-Amz-Signature=test"));
        assertEquals(1, generationService.history(1, 20, "PAYMENT", 88L).getTotal());
        DocumentGenerationReconciliationService.ReconciliationResult reconciliation =
                reconciliationService.reconcileCurrentTenant(30);
        assertTrue(reconciliation.staleFailedIds().isEmpty());
        assertTrue(reconciliation.brokenSuccessfulIds().isEmpty());
        assertTrue(reconciliation.orphanGeneratedFileIds().isEmpty());

        templateService.disablePublishedVersion(published.getId());
        BusinessException disabled = assertThrows(BusinessException.class,
                () -> generationService.generate("PAYMENT", 88L, "m1:e2e:disabled:88", null));
        assertEquals("DOCUMENT_DEFAULT_TEMPLATE_DISABLED", disabled.getCode());
        assertTrue(generationService.downloadUrl(first.getId()).contains("X-Amz-Signature=test"));

        doThrow(new BusinessException("BUSINESS_OBJECT_FORBIDDEN", "archived"))
                .when(authorizer).checkGeneratedDocumentAccess("PAYMENT", 88L);
        BusinessException ordinaryBlocked = assertThrows(BusinessException.class,
                () -> generationService.downloadUrl(first.getId()));
        assertEquals("BUSINESS_OBJECT_FORBIDDEN", ordinaryBlocked.getCode());
        assertTrue(generationService.auditDownloadUrl(first.getId(), "归档付款审计复核")
                .contains("X-Amz-Signature=test"));

        TestUserContext.setAdmin(999L, TestUserContext.USER_ADMIN);
        BusinessException crossTenant = assertThrows(BusinessException.class,
                () -> generationService.requireGeneration(first.getId()));
        assertEquals("DOCUMENT_GENERATION_NOT_FOUND", crossTenant.getCode());
    }

    @Test
    void authoritativeSourceChangeProducesNewSourceDigest() {
        DocumentTemplateVersion draft = templateService.create(
                "M1_E2E_SOURCE_DIGEST", "M1源数据摘要模板", "PAYMENT",
                new DocumentTemplateService.DraftCommand("payment.v1",
                        "<html><body><p>{{payment.applyAmount}}</p></body></html>",
                        "[\"payment.applyAmount\"]", "source digest regression"));
        DocumentTemplateVersion published = templateService.publish(draft.getId());
        templateService.bindDefault(published.getId(), 0);

        DocumentGeneration before = generationService.generate("PAYMENT", 89L,
                "m1:e2e:source-digest:89:before", null);
        TEST_APPLY_AMOUNT.set("123456.79");
        DocumentGeneration after = generationService.generate("PAYMENT", 89L,
                "m1:e2e:source-digest:89:after", null);

        assertEquals("SUCCEEDED", before.getStatus());
        assertEquals("SUCCEEDED", after.getStatus());
        assertNotEquals(before.getSourceDigest(), after.getSourceDigest());
    }

    @TestConfiguration
    static class TestProviderConfig {
        @Bean
        DocumentDataProvider testPaymentDocumentDataProvider() {
            return new DocumentDataProvider() {
                @Override
                public String businessType() { return "PAYMENT"; }

                @Override
                public DocumentDataSnapshot load(Long businessId) {
                        return new DocumentDataSnapshot("payment.v1", Map.of("payment", Map.of(
                            "applyCode", "PAY-TEST-" + businessId,
                            "applyAmount", TEST_APPLY_AMOUNT.get())));
                }
            };
        }
    }
}
