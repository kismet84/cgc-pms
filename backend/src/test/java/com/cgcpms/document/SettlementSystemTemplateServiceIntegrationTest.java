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
        assertTrue(html.contains("商务审核"));
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
        String legacyContent = first.getTemplateContent()
                .replace(" font-family: 'CGC PMS Document Font', sans-serif;", "");
        DocumentTemplateVersion legacyDraft = templateService.createNextDraft(first.getTemplateId(),
                new DocumentTemplateService.DraftCommand("settlement.v1", legacyContent,
                        first.getFieldManifest(), "legacy system template"));
        DocumentTemplateVersion legacyPublished = templateService.publish(legacyDraft.getId());
        templateService.bindDefault(legacyPublished.getId(), 0);

        DocumentTemplateVersion upgraded = systemTemplateService.ensureCurrentTenantTemplate();

        assertEquals(legacyPublished.getVersionNo() + 1, upgraded.getVersionNo());
        assertTrue(upgraded.getTemplateContent().contains("CGC PMS Document Font"));
        assertEquals(upgraded.getId(), templateService.requireDefaultVersion("SETTLEMENT").getId());
    }

    @Test
    @SuppressWarnings("unchecked")
    void rendersZeroAndLongSettlementDetailsUsingPublishedTemplate() throws Exception {
        DocumentTemplateVersion version = systemTemplateService.ensureCurrentTenantTemplate();
        Map<String, Object> zero = new LinkedHashMap<>(sample());
        Map<String, Object> zeroSettlement = new LinkedHashMap<>((Map<String, Object>) zero.get("settlement"));
        zeroSettlement.put("items", List.of());
        zeroSettlement.put("variations", List.of());
        zeroSettlement.put("payments", List.of());
        zeroSettlement.put("costs", List.of());
        zeroSettlement.put("attachments", List.of());
        zeroSettlement.put("approvalRecords", List.of());
        zero.put("settlement", zeroSettlement);
        assertTrue(renderer.render(templateEngine.render(version.getTemplateContent(), zero)).content().length > 1000);

        Map<String, Object> longData = new LinkedHashMap<>(sample());
        Map<String, Object> longSettlement = new LinkedHashMap<>((Map<String, Object>) longData.get("settlement"));
        longSettlement.put("items", IntStream.range(0, 80).mapToObj(index -> Map.of(
                "name", "结算明细-" + index, "unit", "项", "quantity", "1.00", "unitPrice", "10.00",
                "amount", "10.00", "sourceType", "SUB_MEASURE", "sourceId", String.valueOf(index), "remark", ""))
                .toList());
        longSettlement.put("payments", IntStream.range(0, 25).mapToObj(index -> Map.of(
                "applicationCode", "PAY-" + index, "type", "PROGRESS", "applyAmount", "100.00",
                "approvedAmount", "100.00", "actualPayAmount", "100.00", "status", "PAID",
                "payDate", "2026-07-17", "voucherNo", "VCH-" + index)).toList());
        longData.put("settlement", longSettlement);
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
        settlement.put("amountFormulaVersion", "settlement.v1");
        settlement.put("amount", Map.of("contract", "100000.00", "change", "5000.00", "measured", "108000.00",
                "deduction", "1000.00", "paid", "20000.00", "final", "112000.00", "unpaid", "86400.00",
                "warranty", "5600.00"));
        settlement.put("items", List.of(Map.of("name", "主体工程", "unit", "项", "quantity", "1.00",
                "unitPrice", "108000.00", "amount", "108000.00", "sourceType", "SUB_MEASURE", "sourceId", "21",
                "remark", "已审批计量")));
        settlement.put("variations", List.of(Map.of("code", "VAR-001", "name", "现场签证", "type", "DESIGN",
                "direction", "COST", "confirmedAmount", "5000.00", "status", "APPROVED")));
        settlement.put("payments", List.of(Map.of("applicationCode", "PAY-001", "type", "PROGRESS",
                "applyAmount", "20000.00", "approvedAmount", "20000.00", "actualPayAmount", "20000.00",
                "status", "PAID", "payDate", "2026-07-16", "voucherNo", "VCH-001")));
        settlement.put("costs", List.of(Map.of("subjectName", "分包成本", "type", "SUBCONTRACT", "sourceType", "SETTLEMENT",
                "sourceId", "21", "amount", "100000.00", "taxAmount", "9000.00", "amountWithoutTax", "91000.00",
                "date", "2026-07-16", "status", "CONFIRMED")));
        settlement.put("attachments", List.of(Map.of("name", "结算依据.pdf", "type", "application/pdf", "size", "1024",
                "uploadedBy", "测试管理员", "uploadedAt", "2026-07-17 10:00:00")));
        settlement.put("approvalRecords", List.of(Map.of("node", "商务审核", "action", "同意", "operator", "测试管理员",
                "time", "2026-07-17 11:00:00", "comment", "金额确认")));
        return Map.ofEntries(
                Map.entry("settlement", settlement),
                Map.entry("project", Map.of("id", "PRJ-001", "name", "示范项目")),
                Map.entry("contract", Map.of("id", "CT-001", "name", "施工合同")),
                Map.entry("partner", Map.of("id", "PT-001", "name", "示范单位")),
                Map.entry("audit", Map.of("finalizedAt", "2026-07-17 12:00:00", "createdBy", "测试管理员",
                        "createdAt", "2026-07-16 09:00:00", "updatedAt", "2026-07-17 12:00:00")));
    }
}
