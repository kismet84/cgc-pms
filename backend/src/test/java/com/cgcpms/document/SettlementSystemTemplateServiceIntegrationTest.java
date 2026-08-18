package com.cgcpms.document;

import com.cgcpms.common.TestUserContext;
import com.cgcpms.document.entity.DocumentTemplateVersion;
import com.cgcpms.document.render.DocumentRenderer;
import com.cgcpms.document.render.RenderedDocument;
import com.cgcpms.document.render.RestrictedTemplateEngine;
import com.cgcpms.document.service.DocumentTemplateService;
import com.cgcpms.document.service.SettlementSystemTemplateService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "minio.enabled=false")
@ActiveProfiles("local")
@Transactional
class SettlementSystemTemplateServiceIntegrationTest {
    @Autowired private SettlementSystemTemplateService systemTemplateService;
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
    void provisionsIdempotentPublishedDefaultAndRendersAllSettlementSections() throws Exception {
        DocumentTemplateVersion first = systemTemplateService.ensureCurrentTenantTemplate();
        DocumentTemplateVersion second = systemTemplateService.ensureCurrentTenantTemplate();

        assertEquals(first.getId(), second.getId());
        assertEquals(first.getId(), templateService.requireDefaultVersion("SETTLEMENT").getId());
        String html = templateEngine.render(first.getTemplateContent(), sample());
        assertTrue(html.contains("STL-2026-001"));
        assertTrue(html.contains("PAY-001"));
        assertTrue(html.contains("示范单位"));
        RenderedDocument pdf = renderer.render(html);
        assertTrue(pdf.content().length > 1000);
        try (var document = Loader.loadPDF(pdf.content())) {
            String renderedText = new PDFTextStripper().getText(document);
            assertTrue(renderedText.contains("第 1 页 / 共 " + pdf.pageCount() + " 页"), renderedText);
        }
    }

    @Test
    void upgradesOutdatedSystemTemplateIntoNewImmutableDefaultVersion() {
        DocumentTemplateVersion first = systemTemplateService.ensureCurrentTenantTemplate();
        String legacyContent = first.getTemplateContent().replace("flow-root", "legacy-flow-root");
        DocumentTemplateVersion legacyDraft = templateService.createNextDraft(first.getTemplateId(),
                new DocumentTemplateService.DraftCommand("settlement.v2", legacyContent,
                        first.getFieldManifest(), "legacy system template"));
        DocumentTemplateVersion legacyPublished = templateService.publish(legacyDraft.getId());
        templateService.bindDefault(legacyPublished.getId(), 0);

        DocumentTemplateVersion upgraded = systemTemplateService.ensureCurrentTenantTemplate();

        assertEquals(legacyPublished.getVersionNo() + 1, upgraded.getVersionNo());
        assertTrue(upgraded.getTemplateContent().contains("flow-root"));
        assertTrue(upgraded.getDesignSchema().contains("layoutVersion"), upgraded.getDesignSchema());
        assertEquals(upgraded.getId(), templateService.requireDefaultVersion("SETTLEMENT").getId());
    }

    @Test
    void rendersZeroAndLongSettlementDetailsUsingPublishedTemplate() throws Exception {
        DocumentTemplateVersion version = systemTemplateService.ensureCurrentTenantTemplate();
        Map<String, Object> zero = new LinkedHashMap<>(sample());
        zero.put("items", List.of());
        zero.put("variations", List.of());
        zero.put("payments", List.of());
        zero.put("costs", List.of());
        assertTrue(renderer.render(templateEngine.render(version.getTemplateContent(), zero)).content().length > 1000);

        Map<String, Object> longData = new LinkedHashMap<>(sample());
        longData.put("items", IntStream.range(0, 80).mapToObj(index -> Map.of(
                "name", "结算明细-" + index, "unit", "项", "quantity", "1.00", "unitPrice", "10.00",
                "amount", "10.00", "sourceType", "SUB_MEASURE", "remark", ""))
                .toList());
        longData.put("payments", IntStream.range(0, 25).mapToObj(index -> Map.of(
                "applyCode", "PAY-" + index, "payType", "PROGRESS", "applyAmount", "100.00",
                "approvedAmount", "100.00", "actualPayAmount", "100.00", "status", "PAID",
                "payDate", "2026-07-17", "voucherNo", "VCH-" + index)).toList());
        RenderedDocument longPdf = renderer.render(templateEngine.render(version.getTemplateContent(), longData));
        assertTrue(longPdf.pageCount() > 1);
        try (var pdf = Loader.loadPDF(longPdf.content())) {
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("结算明细-79"));
            assertTrue(text.contains("PAY-24"));
            assertTrue(text.contains("112000.00"));
        }
    }

    private Map<String, Object> sample() {
        Map<String, Object> settlement = new LinkedHashMap<>();
        settlement.put("code", "STL-2026-001");
        settlement.put("type", "FINAL");
        settlement.put("approvalStatus", "APPROVED");
        settlement.put("finalStatus", "FINALIZED");
        settlement.put("contractAmount", "100000.00");
        settlement.put("changeAmount", "5000.00");
        settlement.put("measuredAmount", "108000.00");
        settlement.put("deductionAmount", "1000.00");
        settlement.put("paidAmount", "20000.00");
        settlement.put("finalAmount", "112000.00");
        settlement.put("unpaidAmount", "86400.00");
        settlement.put("warrantyAmount", "5600.00");
        settlement.put("finalizedAt", "2026-07-17 12:00:00");
        settlement.put("status", "FINALIZED");
        settlement.put("createdAt", "2026-07-17 10:00:00");
        settlement.put("updatedAt", "2026-07-17 12:00:00");
        settlement.put("remark", "");
        return Map.ofEntries(
                Map.entry("settlement", settlement),
                Map.entry("project", Map.of("name", "示范项目")),
                Map.entry("contract", Map.of("name", "施工合同")),
                Map.entry("partner", Map.of("name", "示范单位")),
                Map.entry("items", List.of(Map.of("name", "主体工程", "unit", "项", "quantity", "1.00",
                        "unitPrice", "108000.00", "amount", "108000.00", "sourceType", "SUB_MEASURE",
                        "remark", "已审批计量"))),
                Map.entry("variations", List.of(Map.of("code", "VAR-001", "name", "现场签证", "type", "DESIGN",
                        "direction", "COST", "confirmedAmount", "5000.00", "status", "APPROVED"))),
                Map.entry("payments", List.of(Map.of("applyCode", "PAY-001", "payType", "PROGRESS",
                        "applyAmount", "20000.00", "approvedAmount", "20000.00", "actualPayAmount", "20000.00",
                        "status", "PAID", "payDate", "2026-07-16", "voucherNo", "VCH-001"))),
                Map.entry("costs", List.of(Map.of("subjectName", "分包成本", "type", "SUBCONTRACT",
                        "sourceType", "SETTLEMENT", "amount", "100000.00", "taxAmount", "9000.00",
                        "amountWithoutTax", "91000.00", "date", "2026-07-16", "status", "CONFIRMED"))));
    }
}
