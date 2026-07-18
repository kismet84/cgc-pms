package com.cgcpms.document;

import com.cgcpms.common.TestUserContext;
import com.cgcpms.document.entity.DocumentTemplateVersion;
import com.cgcpms.document.render.DocumentRenderer;
import com.cgcpms.document.render.RenderedDocument;
import com.cgcpms.document.render.RestrictedTemplateEngine;
import com.cgcpms.document.service.DocumentTemplateService;
import com.cgcpms.document.service.PaymentSystemTemplateService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "minio.enabled=false")
@ActiveProfiles("local")
@Transactional
class PaymentSystemTemplateServiceIntegrationTest {
    @Autowired private PaymentSystemTemplateService systemTemplateService;
    @Autowired private DocumentTemplateService templateService;
    @Autowired private RestrictedTemplateEngine templateEngine;
    @Autowired private DocumentRenderer renderer;

    @BeforeEach
    void setUp() {
        TestUserContext.setAdmin(TestUserContext.TENANT_0, TestUserContext.USER_ADMIN);
    }

    @AfterEach
    void tearDown() {
        TestUserContext.clear();
    }

    @Test
    void provisionsIdempotentPublishedDefaultAndRendersAllPaymentSections() throws Exception {
        DocumentTemplateVersion first = systemTemplateService.ensureCurrentTenantTemplate();
        DocumentTemplateVersion second = systemTemplateService.ensureCurrentTenantTemplate();

        assertEquals(first.getId(), second.getId());
        assertEquals(first.getId(), templateService.requireDefaultVersion("PAYMENT").getId());
        String html = templateEngine.render(first.getTemplateContent(), sample());
        assertTrue(html.contains("PAY-2026-001"));
        assertTrue(html.contains("INV-001"));
        assertTrue(html.contains("审核通过"));
        RenderedDocument pdf = renderer.render(html);
        assertTrue(pdf.content().length > 1000);
        assertTrue(pdf.pageCount() >= 1 && pdf.pageCount() <= 3);
        try (var document = Loader.loadPDF(pdf.content())) {
            String renderedText = new PDFTextStripper().getText(document);
            assertTrue(renderedText.contains("第 1 页 / 共 " + pdf.pageCount() + " 页"), renderedText);
        }
    }

    @Test
    void upgradesAnOutdatedSystemTemplateIntoANewImmutableDefaultVersion() {
        DocumentTemplateVersion first = systemTemplateService.ensureCurrentTenantTemplate();
        String legacyContent = first.getTemplateContent()
                .replace(" font-family: 'CGC PMS Document Font', sans-serif;", "");
        DocumentTemplateVersion legacyDraft = templateService.createNextDraft(first.getTemplateId(),
                new DocumentTemplateService.DraftCommand("payment.v1", legacyContent,
                        first.getFieldManifest(), "legacy system template"));
        DocumentTemplateVersion legacyPublished = templateService.publish(legacyDraft.getId());
        templateService.bindDefault(legacyPublished.getId(), 0);

        DocumentTemplateVersion upgraded = systemTemplateService.ensureCurrentTenantTemplate();

        assertEquals(legacyPublished.getVersionNo() + 1, upgraded.getVersionNo());
        assertTrue(upgraded.getTemplateContent().contains("@bottom-center"));
        assertTrue(upgraded.getTemplateContent().contains("CGC PMS Document Font"));
        assertEquals(upgraded.getId(), templateService.requireDefaultVersion("PAYMENT").getId());
    }

    @Test
    void rendersZeroDetailAndLongDetailUsingThePublishedPaymentTemplate() throws Exception {
        DocumentTemplateVersion version = systemTemplateService.ensureCurrentTenantTemplate();
        Map<String, Object> zeroDetail = new LinkedHashMap<>(sample());
        zeroDetail.put("sources", List.of());
        zeroDetail.put("basis", List.of());
        zeroDetail.put("invoices", List.of());
        zeroDetail.put("attachments", List.of());
        zeroDetail.put("approvalRecords", List.of());
        String zeroHtml = templateEngine.render(version.getTemplateContent(), zeroDetail);
        assertTrue(!zeroHtml.contains("{{#each") && !zeroHtml.contains("{{/each}}"));
        assertTrue(renderer.render(zeroHtml).content().length > 1000);

        Map<String, Object> longDetail = new LinkedHashMap<>(sample());
        longDetail.put("sources", IntStream.range(0, 80).mapToObj(index -> Map.of(
                "type", "EXPENSE-" + index,
                "referenceId", "SRC-" + index,
                "amount", String.format("%d.00", index + 1),
                "paidAmount", "0.00")).toList());
        longDetail.put("invoices", IntStream.range(0, 25).mapToObj(index -> Map.of(
                "number", "INV-LONG-" + index,
                "type", "VAT_SPECIAL",
                "date", "2026-07-17",
                "amount", String.format("%d.00", index + 1),
                "verifyStatus", "VERIFIED")).toList());
        RenderedDocument longPdf = renderer.render(templateEngine.render(version.getTemplateContent(), longDetail));
        assertTrue(longPdf.pageCount() > 1);
        try (var pdf = Loader.loadPDF(longPdf.content())) {
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("SRC-79"));
            assertTrue(text.contains("INV-LONG-24"));
            assertTrue(text.contains("123456.78"));
        }
    }

    private Map<String, Object> sample() {
        return Map.ofEntries(
                Map.entry("payment", Map.of("applyCode", "PAY-2026-001", "approvalStatus", "APPROVED",
                        "applyAmount", "123456.78", "approvedAmount", "120000.00", "payType", "PROGRESS",
                        "createdAt", "2026-07-17T12:00:00", "applyReason", "工程进度款")),
                Map.entry("project", Map.of("name", "示范项目", "code", "PRJ-001")),
                Map.entry("contract", Map.of("name", "施工合同", "code", "CT-001")),
                Map.entry("payee", Map.of("name", "示范供应商", "bankName", "示范银行",
                        "bankAccount", "****1234", "contactPhone", "138****5678")),
                Map.entry("sources", List.of(Map.of("type", "SETTLEMENT", "referenceId", "11",
                        "amount", "120000.00", "paidAmount", "0.00"))),
                Map.entry("basis", List.of(Map.of("type", "SUB_MEASURE", "referenceId", "21",
                        "amount", "120000.00"))),
                Map.entry("invoices", List.of(Map.of("number", "INV-001", "type", "VAT_SPECIAL",
                        "date", "2026-07-16", "amount", "120000.00", "verifyStatus", "VERIFIED"))),
                Map.entry("attachments", List.of(Map.of("name", "付款依据.pdf", "type", "PAYMENT_PROOF",
                        "size", "1024"))),
                Map.entry("approvalRecords", List.of(Map.of("node", "财务审核", "action", "审核通过",
                        "operator", "测试管理员", "time", "2026-07-17T13:00:00", "comment", "同意")))
        );
    }
}
